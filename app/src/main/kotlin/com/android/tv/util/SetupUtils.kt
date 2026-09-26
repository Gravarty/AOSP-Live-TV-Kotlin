package com.android.tv.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.CommonUtils
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.api.Channel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port von SetupUtils: merkt sich bekannte/eingerichtete/erkannte Inputs und den ersten Tune.
 * Ohne eingebauten Tuner entfällt dessen Sonderbehandlung in onInputListUpdated().
 */
@Singleton
class SetupUtils @Inject constructor(@ApplicationContext private val context: Context) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val setUpInputs: MutableSet<String> =
        HashSet(sharedPreferences.getStringSet(PREF_KEY_SET_UP_INPUTS, emptySet()) ?: emptySet())
    private val knownInputs: MutableSet<String> =
        HashSet(sharedPreferences.getStringSet(PREF_KEY_KNOWN_INPUTS, emptySet()) ?: emptySet())
    // Bekannte Inputs gelten als erkannt, wenn noch nichts gespeichert ist
    private val recognizedInputs: MutableSet<String> =
        HashSet(sharedPreferences.getStringSet(PREF_KEY_RECOGNIZED_INPUTS, knownInputs) ?: knownInputs)
    var isFirstTune = sharedPreferences.getBoolean(PREF_KEY_IS_FIRST_TUNE, true)
        private set

    /** Nach der Einrichtung: Input merken, Kanäle neu laden, ersten Kanal als zuletzt gesehen setzen. */
    fun onTvInputSetupFinished(inputId: String, postRunnable: Runnable?) {
        onSetupDone(inputId)
        val manager = TvSingletons.getSingletons(context).getChannelDataManager()
        if (!manager.isDbLoadFinished) {
            manager.addListener(object : ChannelDataManager.Listener {
                override fun onLoadFinished() {
                    manager.removeListener(this)
                    updateChannelsAfterSetup(context, inputId, postRunnable)
                }
                override fun onChannelListUpdated() {}
                override fun onChannelBrowsableChanged() {}
            })
        } else {
            updateChannelsAfterSetup(context, inputId, postRunnable)
        }
    }

    /** Im Original deaktiviert (MARK_NEW_CHANNELS_BROWSABLE = false). */
    fun markNewChannelsBrowsableIfEnabled() {
        if (!MARK_NEW_CHANNELS_BROWSABLE) return
        val singletons = TvSingletons.getSingletons(context)
        val inputHelper = singletons.getTvInputManagerHelper()
        val channelDataManager = singletons.getChannelDataManager()
        SoftPreconditions.checkState(channelDataManager.isDbLoadFinished, TAG, "channels not loaded")
        val newInputsWithChannels = HashSet<String>()
        for (input in inputHelper.getTvInputInfos(true, true)) {
            val inputId = input.id
            if (!isSetupDone(inputId) && channelDataManager.getChannelCountForInput(inputId) > 0) {
                onSetupDone(inputId)
                newInputsWithChannels.add(inputId)
            }
        }
        if (newInputsWithChannels.isNotEmpty()) {
            channelDataManager.getChannelList().filter { it.inputId in newInputsWithChannels }
                .forEach { channelDataManager.updateBrowsable(it.id, true) }
            channelDataManager.applyUpdatedValuesToDb()
        }
    }

    fun isNewInput(inputId: String): Boolean = inputId !in knownInputs

    fun markAsKnownInput(inputId: String) {
        knownInputs.add(inputId)
        recognizedInputs.add(inputId)
        sharedPreferences.edit()
            .putStringSet(PREF_KEY_KNOWN_INPUTS, knownInputs)
            .putStringSet(PREF_KEY_RECOGNIZED_INPUTS, recognizedInputs)
            .apply()
    }

    fun isSetupDone(inputId: String): Boolean = inputId in setUpInputs

    fun hasNewInput(inputManager: TvInputManagerHelper): Boolean =
        inputManager.getTvInputInfos(true, true).any { isNewInput(it.id) }

    private fun isRecognizedInput(inputId: String) = inputId in recognizedInputs

    fun markAllInputsRecognized(inputManager: TvInputManagerHelper) {
        inputManager.getTvInputInfos(true, true).forEach { recognizedInputs.add(it.id) }
        sharedPreferences.edit().putStringSet(PREF_KEY_RECOGNIZED_INPUTS, recognizedInputs).apply()
    }

    /** Neue Quelle, zu der noch kein "Neue Quellen"-Hinweis kam. */
    fun hasUnrecognizedInput(inputManager: TvInputManagerHelper): Boolean =
        inputManager.getTvInputInfos(true, true).any { !isRecognizedInput(it.id) }

    fun onTuned() {
        if (!isFirstTune) return
        isFirstTune = false
        sharedPreferences.edit().putBoolean(PREF_KEY_IS_FIRST_TUNE, false).apply()
    }

    /** Entfernt Einträge von Inputs, deren App deinstalliert wurde. */
    fun onInputListUpdated(manager: TvInputManager) {
        val removed = HashSet(recognizedInputs)
        manager.tvInputList.forEach { removed.remove(it.id) }
        if (removed.isEmpty()) return
        var inputPackageDeleted = false
        for (input in removed) {
            try {
                // App noch vorhanden: Input nur vorübergehend weg
                // Bugfix: fehlerhafte Input-ID → als gelöscht behandeln statt NPE
                val packageName = ComponentName.unflattenFromString(input)?.packageName
                    ?: throw PackageManager.NameNotFoundException(input)
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                Log.i(TAG, "TV input ($input) is removed but package is not deleted")
            } catch (e: PackageManager.NameNotFoundException) {
                Log.i(TAG, "TV input ($input) and its package are removed")
                recognizedInputs.remove(input)
                setUpInputs.remove(input)
                knownInputs.remove(input)
                inputPackageDeleted = true
            }
        }
        if (inputPackageDeleted) {
            sharedPreferences.edit()
                .putStringSet(PREF_KEY_SET_UP_INPUTS, setUpInputs)
                .putStringSet(PREF_KEY_KNOWN_INPUTS, knownInputs)
                .putStringSet(PREF_KEY_RECOGNIZED_INPUTS, recognizedInputs)
                .apply()
        }
    }

    /** Setup-Intent des Inputs; Overlay-Einträge aus R.array.setup_ComponentNames haben Vorrang. */
    fun createSetupIntent(context: Context, input: TvInputInfo): Intent? {
        for (component in context.resources.getStringArray(R.array.setup_ComponentNames)) {
            val split = component.split("#")
            if (split.size != 2) {
                Log.w(TAG, "Invalid component item: $split")
                continue
            }
            val inputId = split[0].trim()
            if (inputId != input.id) continue
            val flattened = split[1].trim()
            val componentName = ComponentName.unflattenFromString(flattened)
            if (componentName == null) {
                Log.w(TAG, "Failed to unflatten component: $flattened")
                continue
            }
            val overlaySetupIntent = Intent(Intent.ACTION_MAIN).apply {
                setComponent(componentName)
                putExtra(TvInputInfo.EXTRA_INPUT_ID, inputId)
            }
            if (overlaySetupIntent.resolveActivityInfo(context.packageManager, 0) == null) {
                Log.w(TAG, "unable to find component$flattened")
                continue
            }
            Log.i(TAG, "overlay input id: $inputId to setup activity: $flattened")
            return CommonUtils.createSetupIntent(overlaySetupIntent, inputId)
        }
        return CommonUtils.createSetupIntent(input)
    }

    private fun onSetupDone(inputId: String) {
        if (recognizedInputs.add(inputId)) {
            Log.i(TAG, "An unrecognized input's setup has been done. inputId=$inputId")
            sharedPreferences.edit().putStringSet(PREF_KEY_RECOGNIZED_INPUTS, recognizedInputs).apply()
        }
        if (knownInputs.add(inputId)) {
            Log.i(TAG, "An unknown input's setup has been done. inputId=$inputId")
            sharedPreferences.edit().putStringSet(PREF_KEY_KNOWN_INPUTS, knownInputs).apply()
        }
        if (setUpInputs.add(inputId)) {
            sharedPreferences.edit().putStringSet(PREF_KEY_SET_UP_INPUTS, setUpInputs).apply()
        }
    }

    companion object {
        private const val TAG = "SetupUtils"
        private const val PREF_KEY_KNOWN_INPUTS = "known_inputs"
        private const val PREF_KEY_SET_UP_INPUTS = "set_up_inputs"
        private const val PREF_KEY_RECOGNIZED_INPUTS = "recognized_inputs"
        private const val PREF_KEY_IS_FIRST_TUNE = "is_first_tune"
        // Original: false – neue Kanäle werden nicht automatisch eingeblendet
        private const val MARK_NEW_CHANNELS_BROWSABLE = false

        private fun updateChannelsAfterSetup(context: Context, inputId: String, postRunnable: Runnable?) {
            val manager = TvSingletons.getSingletons(context).getChannelDataManager()
            manager.updateChannels {
                var firstChannelForInput: Channel? = null
                var browsableChanged = false
                for (channel in manager.getChannelList()) {
                    if (channel.inputId != inputId) continue
                    if (!channel.isBrowsable && MARK_NEW_CHANNELS_BROWSABLE) {
                        manager.updateBrowsable(channel.id, true, true)
                        browsableChanged = true
                    }
                    if (firstChannelForInput == null && channel.isBrowsable) firstChannelForInput = channel
                }
                firstChannelForInput?.let { Utils.setLastWatchedChannel(context, it) }
                if (browsableChanged) {
                    manager.notifyChannelBrowsableChanged()
                    manager.applyUpdatedValuesToDb()
                }
                postRunnable?.run()
            }
        }

        /** Gibt eingerichteten Inputs Schreibrecht auf Kanäle/Programme (braucht Systemrechte, sonst Log). */
        @JvmStatic
        fun grantEpgPermissionToSetUpPackages(context: Context) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            (sp.getStringSet(PREF_KEY_SET_UP_INPUTS, emptySet()) ?: emptySet())
                .filter { it.isNotEmpty() }
                .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
                .toSet()
                .forEach { grantEpgPermission(context, it) }
        }

        @JvmStatic
        fun grantEpgPermission(context: Context, packageName: String) {
            try {
                val modeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                context.grantUriPermission(packageName, TvContract.Channels.CONTENT_URI, modeFlags)
                context.grantUriPermission(packageName, TvContract.Programs.CONTENT_URI, modeFlags)
            } catch (e: SecurityException) {
                Log.e(TAG, "Either TvProvider does not allow granting of Uri permissions or the app does not have permission.", e)
            }
        }
    }
}
