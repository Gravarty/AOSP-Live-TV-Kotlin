package com.android.tv.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvInputManager.TvInputCallback
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.UiThread
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.compat.TvInputInfoCompat
import com.android.tv.common.util.CommonUtils
import com.android.tv.util.images.ImageCache
import com.android.tv.util.images.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port von com.android.tv.util.TvInputManagerHelper.
 * Entfernt (nur für System-Apps): Kindersicherung (ParentalControlSettings, ContentRatingsManager),
 * CEC/MHL-Erkennung, Lesen von ro.tv_allow_third_party_inputs.
 */
@UiThread
@Singleton
class TvInputManagerHelper internal constructor(
    context: Context,
    private val tvInputManager: TvInputManagerInterface?,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, createTvInputManagerWrapper(context))

    interface TvInputManagerInterface {
        fun getTvInputInfo(inputId: String): TvInputInfo?
        fun getInputState(inputId: String): Int
        fun registerCallback(callback: TvInputCallback, handler: Handler)
        fun unregisterCallback(callback: TvInputCallback)
        fun getTvInputList(): List<TvInputInfo>
    }

    private class TvInputManagerImpl(private val delegate: TvInputManager) : TvInputManagerInterface {
        override fun getTvInputInfo(inputId: String) = delegate.getTvInputInfo(inputId)
        override fun getInputState(inputId: String) = delegate.getInputState(inputId)
        override fun registerCallback(callback: TvInputCallback, handler: Handler) =
            delegate.registerCallback(callback, handler)
        override fun unregisterCallback(callback: TvInputCallback) = delegate.unregisterCallback(callback)
        override fun getTvInputList(): List<TvInputInfo> = delegate.tvInputList
    }

    private val context: Context = context.applicationContext
    private val packageManager: PackageManager = context.packageManager
    private val handler = Handler(Looper.getMainLooper())

    private val inputStateMap = HashMap<String, Int>()
    private val inputMap = HashMap<String, TvInputInfoCompat>()
    private val tvInputLabels = HashMap<String, String?>()
    private val tvInputCustomLabels = HashMap<String, String>()
    private val inputIdToPartnerInputMap = HashMap<String, Boolean>()
    private val tvInputApplicationLabels = HashMap<CharSequence, CharSequence>()
    private val tvInputApplicationIcons = HashMap<String, Drawable>()
    private val tvInputApplicationBanners = HashMap<String, Drawable>()
    private val callbacks = HashSet<TvInputCallback>()

    private var started = false
    private var allow3rdPartyInputs = false

    val defaultTvInputInfoComparator: Comparator<TvInputInfo> = InputComparatorInternal(this)

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri?.lastPathSegment != TV_INPUT_ALLOW_3RD_PARTY_INPUTS) return
            val previous = allow3rdPartyInputs
            updateAllow3rdPartyInputs()
            if (previous != allow3rdPartyInputs) initInputMaps()
        }
    }

    private val internalCallback = object : TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            val info = inputMap[inputId]?.tvInputInfo
            if (info == null || isInputBlocked(info)) return
            inputStateMap[inputId] = state
            callbacks.forEach { it.onInputStateChanged(inputId, state) }
        }

        override fun onInputAdded(inputId: String) {
            val info = tvInputManager!!.getTvInputInfo(inputId)
            if (info == null || isInputBlocked(info)) return
            inputMap[inputId] = TvInputInfoCompat(this@TvInputManagerHelper.context, info)
            // In Tests kann das Label fehlen – dann die Input-ID nehmen
            tvInputLabels[inputId] = info.loadLabel(this@TvInputManagerHelper.context)?.toString() ?: inputId
            info.loadCustomLabel(this@TvInputManagerHelper.context)?.let { tvInputCustomLabels[inputId] = it.toString() }
            inputStateMap[inputId] = tvInputManager.getInputState(inputId)
            inputIdToPartnerInputMap[inputId] = isPartnerInput(info)
            callbacks.forEach { it.onInputAdded(inputId) }
        }

        override fun onInputRemoved(inputId: String) {
            inputMap.remove(inputId)
            tvInputLabels.remove(inputId)
            tvInputCustomLabels.remove(inputId)
            tvInputApplicationLabels.remove(inputId)
            tvInputApplicationIcons.remove(inputId)
            tvInputApplicationBanners.remove(inputId)
            inputStateMap.remove(inputId)
            inputIdToPartnerInputMap.remove(inputId)
            callbacks.forEach { it.onInputRemoved(inputId) }
            ImageCache.getInstance().remove(ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(inputId))
        }

        override fun onInputUpdated(inputId: String) {
            val info = tvInputManager!!.getTvInputInfo(inputId)
            if (info == null || isInputBlocked(info)) return
            inputMap[inputId] = TvInputInfoCompat(this@TvInputManagerHelper.context, info)
            tvInputLabels[inputId] = info.loadLabel(this@TvInputManagerHelper.context).toString()
            info.loadCustomLabel(this@TvInputManagerHelper.context)?.let { tvInputCustomLabels[inputId] = it.toString() }
            tvInputApplicationLabels.remove(inputId)
            tvInputApplicationIcons.remove(inputId)
            tvInputApplicationBanners.remove(inputId)
            callbacks.forEach { it.onInputUpdated(inputId) }
            ImageCache.getInstance().remove(ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(inputId))
        }

        override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) {
            if (isInputBlocked(inputInfo)) return
            val id = inputInfo.id
            inputMap[id] = TvInputInfoCompat(this@TvInputManagerHelper.context, inputInfo)
            tvInputLabels[id] = inputInfo.loadLabel(this@TvInputManagerHelper.context).toString()
            inputInfo.loadCustomLabel(this@TvInputManagerHelper.context)?.let { tvInputCustomLabels[id] = it.toString() }
            callbacks.forEach { it.onTvInputInfoUpdated(inputInfo) }
            ImageCache.getInstance().remove(ImageLoader.LoadTvInputLogoTask.getTvInputLogoKey(id))
        }
    }

    fun start() {
        if (!hasTvInputManager()) return // kein TV-Gerät
        if (started) return
        started = true
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(TV_INPUT_ALLOW_3RD_PARTY_INPUTS), true, contentObserver)
        updateAllow3rdPartyInputs()
        tvInputManager!!.registerCallback(internalCallback, handler)
        initInputMaps()
    }

    fun stop() {
        if (!started) return
        tvInputManager!!.unregisterCallback(internalCallback)
        context.contentResolver.unregisterContentObserver(contentObserver)
        started = false
        inputStateMap.clear()
        inputMap.clear()
        tvInputLabels.clear()
        tvInputCustomLabels.clear()
        tvInputApplicationLabels.clear()
        tvInputApplicationIcons.clear()
        tvInputApplicationBanners.clear()
        inputIdToPartnerInputMap.clear()
    }

    fun clearTvInputLabels() {
        tvInputLabels.clear()
        tvInputCustomLabels.clear()
        tvInputApplicationLabels.clear()
    }

    fun getTvInputInfos(availableOnly: Boolean, tunerOnly: Boolean): List<TvInputInfo> {
        val list = ArrayList<TvInputInfo>()
        for ((inputId, state) in inputStateMap) {
            if (availableOnly && state == TvInputManager.INPUT_STATE_DISCONNECTED) continue
            val input = getTvInputInfo(inputId)
            if (input == null || isInputBlocked(input)) continue
            if (tunerOnly && input.type != TvInputInfo.TYPE_TUNER) continue
            list.add(input)
        }
        list.sortWith(defaultTvInputInfoComparator)
        return list
    }

    fun isPartnerInput(inputInfo: TvInputInfo?): Boolean =
        isSystemInput(inputInfo) && !isBundledInput(inputInfo)

    fun isSystemInput(inputInfo: TvInputInfo?): Boolean =
        inputInfo != null && inputInfo.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    fun isBundledInput(inputInfo: TvInputInfo?): Boolean =
        inputInfo != null && CommonUtils.isInBundledPackageSet(inputInfo.serviceInfo.applicationInfo.packageName)

    fun isPartnerInput(inputId: String): Boolean = inputIdToPartnerInputMap[inputId] ?: false

    fun hasTvInputManager(): Boolean = tvInputManager != null

    /** Ersatz für ParentalControlSettings.isParentalControlsEnabled() (öffentliche API). */
    fun isParentalControlsEnabled(): Boolean =
        context.getSystemService(TvInputManager::class.java)?.isParentalControlsEnabled == true

    fun loadLabel(info: TvInputInfo): String? {
        return tvInputLabels[info.id]
            ?: info.loadLabel(context)?.toString().also { tvInputLabels[info.id] = it }
    }

    fun loadCustomLabel(info: TvInputInfo): String? {
        tvInputCustomLabels[info.id]?.let { return it }
        return info.loadCustomLabel(context)?.toString()?.also { tvInputCustomLabels[info.id] = it }
    }

    fun getTvInputApplicationLabel(inputId: CharSequence): CharSequence? = tvInputApplicationLabels[inputId]
    fun setTvInputApplicationLabel(inputId: String, label: CharSequence) { tvInputApplicationLabels[inputId] = label }
    fun getTvInputApplicationIcon(inputId: String): Drawable? = tvInputApplicationIcons[inputId]
    fun setTvInputApplicationIcon(inputId: String, icon: Drawable) { tvInputApplicationIcons[inputId] = icon }
    fun getTvInputApplicationBanner(inputId: String): Drawable? = tvInputApplicationBanners[inputId]
    fun setTvInputApplicationBanner(inputId: String, banner: Drawable) { tvInputApplicationBanners[inputId] = banner }

    fun hasTvInputInfo(inputId: String?): Boolean {
        SoftPreconditions.checkState(started, TAG, "hasTvInputInfo() called before TvInputManagerHelper was started.")
        return started && !inputId.isNullOrEmpty() && inputMap[inputId] != null
    }

    fun getTvInputInfo(inputId: String?): TvInputInfo? = getTvInputInfoCompat(inputId)?.tvInputInfo

    fun getTvInputInfoCompat(inputId: String?): TvInputInfoCompat? {
        SoftPreconditions.checkState(started, TAG, "getTvInputInfo() called before TvInputManagerHelper was started.")
        if (!started || inputId == null) return null
        return inputMap[inputId]
    }

    fun getTvInputAppInfo(inputId: String?): ApplicationInfo? =
        getTvInputInfo(inputId)?.serviceInfo?.applicationInfo

    fun getTunerTvInputSize(): Int = inputMap.values.count { it.type == TvInputInfo.TYPE_TUNER }

    fun getInputState(inputInfo: TvInputInfo?): Int =
        if (inputInfo == null) TvInputManager.INPUT_STATE_DISCONNECTED else getInputState(inputInfo.id)

    fun getInputState(inputId: String): Int {
        SoftPreconditions.checkState(started, TAG, "AvailabilityManager not started")
        if (!started) return TvInputManager.INPUT_STATE_DISCONNECTED
        return inputStateMap[inputId] ?: run {
            Log.w(TAG, "getInputState: no such input (id=$inputId)")
            TvInputManager.INPUT_STATE_DISCONNECTED
        }
    }

    fun addCallback(callback: TvInputCallback) { callbacks.add(callback) }
    fun removeCallback(callback: TvInputCallback) { callbacks.remove(callback) }

    private fun getInputSortKey(input: TvInputInfo): Int =
        input.serviceInfo.metaData?.getInt(META_LABEL_SORT_KEY, Int.MAX_VALUE) ?: Int.MAX_VALUE

    private fun isInputPhysicalTuner(input: TvInputInfo): Boolean {
        val packageName = input.serviceInfo.packageName
        if (packageName in PHYSICAL_TUNER_BLOCK_LIST) return false
        if (input.createSetupIntent() == null) return false
        val mayBeTunerInput = packageManager.checkPermission(PERMISSION_ACCESS_ALL_EPG_DATA, packageName) ==
            PackageManager.PERMISSION_GRANTED
        if (!mayBeTunerInput) {
            try {
                val ai = packageManager.getApplicationInfo(packageName, 0)
                if (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                    return false
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return false
            }
        }
        return true
    }

    private fun initInputMaps() {
        inputMap.clear()
        tvInputLabels.clear()
        tvInputCustomLabels.clear()
        tvInputApplicationLabels.clear()
        tvInputApplicationIcons.clear()
        tvInputApplicationBanners.clear()
        inputStateMap.clear()
        inputIdToPartnerInputMap.clear()
        for (input in tvInputManager!!.getTvInputList()) {
            if (DEBUG) Log.d(TAG, "Input detected $input")
            if (isInputBlocked(input)) continue
            val inputId = input.id
            inputMap[inputId] = TvInputInfoCompat(context, input)
            inputStateMap[inputId] = tvInputManager.getInputState(inputId)
            inputIdToPartnerInputMap[inputId] = isPartnerInput(input)
        }
        SoftPreconditions.checkState(
            inputStateMap.size == inputMap.size, TAG, "mInputStateMap not the same size as mInputMap")
    }

    private fun updateAllow3rdPartyInputs() {
        allow3rdPartyInputs = try {
            Settings.Global.getInt(context.contentResolver, TV_INPUT_ALLOW_3RD_PARTY_INPUTS) == 1
        } catch (e: Settings.SettingNotFoundException) {
            // Original: SystemProperty ro.tv_allow_third_party_inputs (für Apps nicht lesbar), Standard true
            true
        }
    }

    private fun isInputBlocked(info: TvInputInfo): Boolean {
        if (!allow3rdPartyInputs) {
            if (!isSystemInput(info)) return true
            if (SYSTEM_INPUT_ID_BLOCKLIST.any { info.id.startsWith(it) }) return true
        }
        // Original isBlocked(): Partner-Blockliste ist in AOSP leer, Test-Inputs nur in Tests.
        return false
    }

    internal class InputComparatorInternal(private val inputManager: TvInputManagerHelper) :
        Comparator<TvInputInfo> {
        override fun compare(lhs: TvInputInfo, rhs: TvInputInfo): Int {
            val lp = inputManager.isPartnerInput(lhs)
            if (lp != inputManager.isPartnerInput(rhs)) return if (lp) -1 else 1
            return nullsFirst<String>().compare(inputManager.loadLabel(lhs), inputManager.loadLabel(rhs))
        }
    }

    /** Sortierung der Hardware-Eingänge. CEC/MHL-Erkennung entfällt (System-API). */
    class HardwareInputComparator(context: Context, private val helper: TvInputManagerHelper) :
        Comparator<TvInputInfo?> {
        private val typePriorities: MutableMap<Int, Int> = Partner.getInstance(context).getInputsOrderMap().also {
            // Fehlende Prioritäten aus der Standardliste ergänzen
            var priority = it.size
            for (type in DEFAULT_TV_INPUT_PRIORITY) if (!it.containsKey(type)) it[type] = priority++
        }

        override fun compare(lhs: TvInputInfo?, rhs: TvInputInfo?): Int {
            if (lhs == null) return if (rhs == null) 0 else 1
            if (rhs == null) return -1

            val enabledL = helper.getInputState(lhs) != TvInputManager.INPUT_STATE_DISCONNECTED
            val enabledR = helper.getInputState(rhs) != TvInputManager.INPUT_STATE_DISCONNECTED
            if (enabledL != enabledR) return if (enabledL) -1 else 1

            val priorityL = typePriorities[lhs.type] ?: Int.MAX_VALUE
            val priorityR = typePriorities[rhs.type] ?: Int.MAX_VALUE
            if (priorityL != priorityR) return priorityL - priorityR

            if (lhs.type == TvInputInfo.TYPE_TUNER && rhs.type == TvInputInfo.TYPE_TUNER) {
                val physL = helper.isInputPhysicalTuner(lhs)
                val physR = helper.isInputPhysicalTuner(rhs)
                if (physL != physR) return if (physL) -1 else 1
            }

            val sortKeyL = helper.getInputSortKey(lhs)
            val sortKeyR = helper.getInputSortKey(rhs)
            if (sortKeyL != sortKeyR) return sortKeyR - sortKeyL

            val parentL = getLabel(helper.getTvInputInfo(lhs.parentId ?: lhs.id))
            val parentR = getLabel(helper.getTvInputInfo(rhs.parentId ?: rhs.id))
            if (parentL != parentR) return parentL.compareTo(parentR, ignoreCase = true)
            return getLabel(lhs).compareTo(getLabel(rhs), ignoreCase = true)
        }

        private fun getLabel(input: TvInputInfo?): String {
            if (input == null) return ""
            val custom = helper.loadCustomLabel(input)
            return if (!custom.isNullOrEmpty()) custom else helper.loadLabel(input) ?: ""
        }
    }

    companion object {
        private const val TAG = "TvInputManagerHelper"
        private const val DEBUG = false

        const val TYPE_CEC_DEVICE = -2
        const val TYPE_BUNDLED_TUNER = -3
        const val TYPE_CEC_DEVICE_RECORDER = -4
        const val TYPE_CEC_DEVICE_PLAYBACK = -5
        const val TYPE_MHL_MOBILE = -6

        private const val PERMISSION_ACCESS_ALL_EPG_DATA = "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA"
        private val PHYSICAL_TUNER_BLOCK_LIST = setOf("com.google.android.videos") // Play Movies
        private const val META_LABEL_SORT_KEY = "input_sort_key"
        private const val TV_INPUT_ALLOW_3RD_PARTY_INPUTS = "tv_input_allow_3rd_party_inputs"
        private val SYSTEM_INPUT_ID_BLOCKLIST = arrayOf("com.google.android.videos/") // Play Movies

        private val DEFAULT_TV_INPUT_PRIORITY = listOf(
            TYPE_BUNDLED_TUNER, TvInputInfo.TYPE_TUNER, TYPE_CEC_DEVICE, TYPE_CEC_DEVICE_RECORDER,
            TYPE_CEC_DEVICE_PLAYBACK, TYPE_MHL_MOBILE, TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_DVI,
            TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_SVIDEO, TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_DISPLAY_PORT, TvInputInfo.TYPE_VGA, TvInputInfo.TYPE_SCART,
            TvInputInfo.TYPE_OTHER,
        )

        private fun createTvInputManagerWrapper(context: Context): TvInputManagerInterface? =
            (context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager?)?.let(::TvInputManagerImpl)
    }
}
