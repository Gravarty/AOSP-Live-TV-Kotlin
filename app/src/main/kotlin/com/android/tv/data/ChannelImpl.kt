package com.android.tv.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import com.android.tv.common.CommonConstants
import com.android.tv.common.util.CommonUtils
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Channel.Companion.APP_LINK_TYPE_APP
import com.android.tv.data.api.Channel.Companion.APP_LINK_TYPE_CHANNEL
import com.android.tv.data.api.Channel.Companion.APP_LINK_TYPE_NONE
import com.android.tv.data.api.Channel.Companion.CHANNEL_NUMBER_DELIMITER
import com.android.tv.data.api.Channel.Companion.INVALID_ID
import com.android.tv.data.api.Channel.Companion.LOAD_IMAGE_TYPE_APP_LINK_ICON
import com.android.tv.data.api.Channel.Companion.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART
import com.android.tv.data.api.Channel.Companion.LOAD_IMAGE_TYPE_CHANNEL_LOGO
import com.android.tv.util.TvInputManagerHelper
import com.android.tv.util.Utils
import com.android.tv.util.images.ImageLoader
import java.net.URISyntaxException

/** 1:1-Port von com.android.tv.data.ChannelImpl. */
class ChannelImpl private constructor() : Channel {

    override var id: Long = 0; private set
    override var packageName: String = ""; private set
    override var inputId: String = ""; private set
    override var type: String? = null; private set
    override var displayNumber: String? = null; private set
    override var displayName: String? = null; private set
    override var description: String? = null; private set
    override var videoFormat: String? = null; private set
    override var isBrowsable = false
    override var isSearchable = false; private set
    override var isLocked = false
    override var isPassthrough = false; private set
    override var appLinkText: String? = null; private set
    override var appLinkColor = 0; private set
    override var appLinkIconUri: String? = null; private set
    override var appLinkPosterArtUri: String? = null; private set
    override var appLinkIntentUri: String? = null; private set
    override var networkAffiliation: String? = null
    override var logoUri: String? = null
    override var isRecordingProhibited = false; private set
    private var appLinkIntent: Intent? = null
    private var appLinkType = APP_LINK_TYPE_NOT_SET
    private var channelLogoExist = false

    override val uri: Uri
        get() = if (isPassthrough) TvContract.buildChannelUriForPassthroughInput(inputId)
        else TvContract.buildChannelUri(id)

    override val displayText: String
        // Abweichung: Original gibt bei fehlender Nummer null zurück, hier leerer Text (nicht-null-Typ).
        get() = if (displayName.isNullOrEmpty()) displayNumber.orEmpty() else "$displayNumber $displayName"

    override val isPhysicalTunerChannel: Boolean
        get() = !type.isNullOrEmpty() && TvContract.Channels.TYPE_OTHER != type

    override fun equals(other: Any?): Boolean =
        // Alle Passthrough-Kanäle haben INVALID_ID als id.
        other is ChannelImpl && id == other.id && inputId == other.inputId &&
            isPassthrough == other.isPassthrough

    override fun hashCode(): Int = java.util.Objects.hash(id, inputId, isPassthrough)

    override fun hasSameReadOnlyInfo(other: Channel?): Boolean =
        other != null &&
            id == other.id &&
            packageName == other.packageName &&
            inputId == other.inputId &&
            type == other.type &&
            displayNumber == other.displayNumber &&
            displayName == other.displayName &&
            description == other.description &&
            videoFormat == other.videoFormat &&
            isPassthrough == other.isPassthrough &&
            appLinkText == other.appLinkText &&
            appLinkColor == other.appLinkColor &&
            appLinkIconUri == other.appLinkIconUri &&
            appLinkPosterArtUri == other.appLinkPosterArtUri &&
            appLinkIntentUri == other.appLinkIntentUri &&
            isRecordingProhibited == other.isRecordingProhibited

    override fun toString(): String =
        "Channel{id=$id, packageName=$packageName, inputId=$inputId, type=$type, " +
            "displayNumber=$displayNumber, displayName=$displayName, description=$description, " +
            "videoFormat=$videoFormat, isPassthrough=$isPassthrough, browsable=$isBrowsable, " +
            "searchable=$isSearchable, locked=$isLocked, appLinkText=$appLinkText, " +
            "recordingProhibited=$isRecordingProhibited}"

    override fun copyFrom(channel: Channel) {
        if (channel is ChannelImpl) {
            copyFromImpl(channel)
            return
        }
        // Übernehmen, was geht
        id = channel.id
        packageName = channel.packageName
        inputId = channel.inputId
        type = channel.type
        displayNumber = channel.displayNumber
        displayName = channel.displayName
        description = channel.description
        videoFormat = channel.videoFormat
        isPassthrough = channel.isPassthrough
        isBrowsable = channel.isBrowsable
        isSearchable = channel.isSearchable
        isLocked = channel.isLocked
        appLinkText = channel.appLinkText
        appLinkColor = channel.appLinkColor
        appLinkIconUri = channel.appLinkIconUri
        appLinkPosterArtUri = channel.appLinkPosterArtUri
        appLinkIntentUri = channel.appLinkIntentUri
        networkAffiliation = channel.networkAffiliation
        isRecordingProhibited = channel.isRecordingProhibited
        channelLogoExist = channel.channelLogoExists()
    }

    private fun copyFromImpl(other: ChannelImpl) {
        if (this === other) return
        id = other.id
        packageName = other.packageName
        inputId = other.inputId
        type = other.type
        displayNumber = other.displayNumber
        displayName = other.displayName
        description = other.description
        videoFormat = other.videoFormat
        isPassthrough = other.isPassthrough
        isBrowsable = other.isBrowsable
        isSearchable = other.isSearchable
        isLocked = other.isLocked
        appLinkText = other.appLinkText
        appLinkColor = other.appLinkColor
        appLinkIconUri = other.appLinkIconUri
        appLinkPosterArtUri = other.appLinkPosterArtUri
        appLinkIntentUri = other.appLinkIntentUri
        networkAffiliation = other.networkAffiliation
        appLinkIntent = other.appLinkIntent
        appLinkType = other.appLinkType
        isRecordingProhibited = other.isRecordingProhibited
        channelLogoExist = other.channelLogoExist
    }

    override fun prefetchImage(context: Context, loadImageType: Int, maxWidth: Int, maxHeight: Int) {
        val uriString = getImageUriString(loadImageType)
        if (!uriString.isNullOrEmpty()) {
            ImageLoader.prefetchBitmap(context, uriString, maxWidth, maxHeight)
        }
    }

    override fun loadBitmap(
        context: Context,
        loadImageType: Int,
        maxWidth: Int,
        maxHeight: Int,
        callback: ImageLoader.ImageLoaderCallback<*>,
    ) {
        ImageLoader.loadBitmap(context, getImageUriString(loadImageType), maxWidth, maxHeight, callback)
    }

    override fun setChannelLogoExist(result: Boolean) { channelLogoExist = result }
    override fun channelLogoExists(): Boolean = channelLogoExist

    override fun getAppLinkType(context: Context): Int {
        if (appLinkType == APP_LINK_TYPE_NOT_SET) initAppLinkTypeAndIntent(context)
        return appLinkType
    }

    override fun getAppLinkIntent(context: Context): Intent? {
        if (appLinkType == APP_LINK_TYPE_NOT_SET) initAppLinkTypeAndIntent(context)
        return appLinkIntent
    }

    private fun initAppLinkTypeAndIntent(context: Context) {
        appLinkType = APP_LINK_TYPE_NONE
        appLinkIntent = null
        val pm = context.packageManager
        if (!appLinkText.isNullOrEmpty() && !appLinkIntentUri.isNullOrEmpty()) {
            try {
                val intent = Intent.parseUri(appLinkIntentUri, Intent.URI_INTENT_SCHEME)
                val activityInfo = intent.resolveActivityInfo(pm, 0)
                if (activityInfo != null) {
                    val pkg = activityInfo.packageName
                    // Keine App-Links auf private Activities dieses Pakets
                    val isProtectedActivity = pkg != null &&
                        (pkg == CommonConstants.BASE_PACKAGE ||
                            pkg.startsWith(CommonConstants.BASE_PACKAGE + "."))
                    // Keine App-Links auf berechtigungsgeschützte Activities
                    val isPermissionProtected = activityInfo.exported && activityInfo.permission != null
                    if (isProtectedActivity) {
                        Log.w(TAG, "Attempt to add app link to protected activity: $appLinkIntentUri")
                        return
                    }
                    if (isPermissionProtected) {
                        Log.w(TAG, "Attempt to add app link to permission protected activity: $appLinkIntentUri")
                        return
                    }
                    appLinkIntent = intent.apply {
                        putExtra(CommonConstants.EXTRA_APP_LINK_CHANNEL_URI, uri.toString())
                    }
                    appLinkType = APP_LINK_TYPE_CHANNEL
                    return
                } else {
                    Log.w(TAG, "No activity exists to handle : $appLinkIntentUri")
                }
            } catch (e: URISyntaxException) {
                Log.w(TAG, "Unable to set app link for $appLinkIntentUri", e)
            }
        }
        if (packageName == context.applicationContext.packageName) return
        appLinkIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
        appLinkIntent?.let {
            it.putExtra(CommonConstants.EXTRA_APP_LINK_CHANNEL_URI, uri.toString())
            appLinkType = APP_LINK_TYPE_APP
        }
    }

    private fun getImageUriString(type: Int): String? = when (type) {
        LOAD_IMAGE_TYPE_CHANNEL_LOGO -> TvContract.buildChannelLogoUri(id).toString()
        LOAD_IMAGE_TYPE_APP_LINK_ICON -> appLinkIconUri
        LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART -> appLinkPosterArtUri
        else -> null
    }

    class Builder() {
        private val channel = ChannelImpl().apply {
            id = INVALID_ID
            packageName = INVALID_PACKAGE_NAME
            inputId = "inputId"
            type = "type"
            displayNumber = "0"
            displayName = "name"
            description = "description"
            isBrowsable = true
            isSearchable = true
        }

        constructor(other: Channel) : this() { channel.copyFrom(other) }

        fun setId(v: Long) = apply { channel.id = v }
        fun setPackageName(v: String) = apply { channel.packageName = v }
        fun setInputId(v: String) = apply { channel.inputId = v }
        fun setType(v: String?) = apply { channel.type = v }
        fun setDisplayNumber(v: String?) = apply { channel.displayNumber = normalizeDisplayNumber(v) }
        fun setDisplayName(v: String?) = apply { channel.displayName = v }
        fun setDescription(v: String?) = apply { channel.description = v }
        fun setVideoFormat(v: String?) = apply { channel.videoFormat = v }
        fun setBrowsable(v: Boolean) = apply { channel.isBrowsable = v }
        fun setSearchable(v: Boolean) = apply { channel.isSearchable = v }
        fun setLocked(v: Boolean) = apply { channel.isLocked = v }
        fun setPassthrough(v: Boolean) = apply { channel.isPassthrough = v }
        fun setAppLinkText(v: String?) = apply { channel.appLinkText = v }
        fun setNetworkAffiliation(v: String?) = apply { channel.networkAffiliation = v }
        fun setAppLinkColor(v: Int) = apply { channel.appLinkColor = v }
        fun setAppLinkIconUri(v: String?) = apply { channel.appLinkIconUri = v }
        fun setAppLinkPosterArtUri(v: String?) = apply { channel.appLinkPosterArtUri = v }
        fun setAppLinkIntentUri(v: String?) = apply { channel.appLinkIntentUri = v }
        fun setRecordingProhibited(v: Boolean) = apply { channel.isRecordingProhibited = v }

        fun build(): ChannelImpl = ChannelImpl().also { it.copyFrom(channel) }
    }

    /** Sortierung: Partner-Inputs zuerst, dann Input-Label, Input-ID, Kanalnummer. */
    class DefaultComparator(
        private val context: Context,
        private val inputManager: TvInputManagerHelper,
    ) : Comparator<Channel> {
        private val inputIdToLabelMap = HashMap<String, String>()
        var detectDuplicatesEnabled = false

        override fun compare(lhs: Channel, rhs: Channel): Int {
            if (lhs === rhs) return 0
            val lhsIsPartner = inputManager.isPartnerInput(lhs.inputId)
            val rhsIsPartner = inputManager.isPartnerInput(rhs.inputId)
            if (lhsIsPartner != rhsIsPartner) return if (lhsIsPartner) -1 else 1

            val lhsLabel = getInputLabelForChannel(lhs)
            val rhsLabel = getInputLabelForChannel(rhs)
            var result = when {
                lhsLabel == null -> if (rhsLabel == null) 0 else 1
                rhsLabel == null -> -1
                else -> lhsLabel.compareTo(rhsLabel)
            }
            if (result != 0) return result

            result = lhs.inputId.compareTo(rhs.inputId)
            if (result != 0) return result

            result = ChannelNumber.compare(lhs.displayNumber, rhs.displayNumber)
            if (detectDuplicatesEnabled && result == 0) {
                Log.w(TAG, "Duplicate channels detected! - \"${lhs.displayText}\" and \"${rhs.displayText}\"")
            }
            return result
        }

        fun getInputLabelForChannel(channel: Channel): String? {
            inputIdToLabelMap[channel.inputId]?.let { return it }
            val info = inputManager.getTvInputInfo(channel.inputId) ?: return null
            return Utils.loadLabel(context, info)?.also { inputIdToLabelMap[channel.inputId] = it }
        }
    }

    companion object {
        private const val TAG = "ChannelImpl"
        private const val APP_LINK_TYPE_NOT_SET = 0
        private const val INVALID_PACKAGE_NAME = "packageName"

        @JvmField
        val CHANNEL_NUMBER_COMPARATOR = Comparator<Channel> { l, r ->
            ChannelNumber.compare(l.displayNumber, r.displayNumber)
        }

        // Reihenfolge muss zu fromCursor() passen
        @JvmField
        val PROJECTION = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_TYPE,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_DESCRIPTION,
            TvContract.Channels.COLUMN_VIDEO_FORMAT,
            TvContract.Channels.COLUMN_BROWSABLE,
            TvContract.Channels.COLUMN_SEARCHABLE,
            TvContract.Channels.COLUMN_LOCKED,
            TvContract.Channels.COLUMN_APP_LINK_TEXT,
            TvContract.Channels.COLUMN_APP_LINK_COLOR,
            TvContract.Channels.COLUMN_APP_LINK_ICON_URI,
            TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI,
            TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
            TvContract.Channels.COLUMN_NETWORK_AFFILIATION,
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG2, // nur für gebündelte Inputs
        )

        @JvmStatic
        fun fromCursor(cursor: Cursor): ChannelImpl = ChannelImpl().apply {
            var i = 0
            id = cursor.getLong(i++)
            packageName = cursor.getString(i++).orEmpty().intern()
            inputId = cursor.getString(i++).orEmpty().intern()
            type = cursor.getString(i++)?.intern()
            displayNumber = normalizeDisplayNumber(cursor.getString(i++))
            displayName = cursor.getString(i++)
            description = cursor.getString(i++)
            videoFormat = cursor.getString(i++)?.intern()
            isBrowsable = cursor.getInt(i++) == 1
            isSearchable = cursor.getInt(i++) == 1
            isLocked = cursor.getInt(i++) == 1
            appLinkText = cursor.getString(i++)
            appLinkColor = cursor.getInt(i++)
            appLinkIconUri = cursor.getString(i++)
            appLinkPosterArtUri = cursor.getString(i++)
            appLinkIntentUri = cursor.getString(i++)
            networkAffiliation = cursor.getString(i++)
            if (CommonUtils.isBundledInput(inputId)) {
                isRecordingProhibited = cursor.getInt(i) != 0
            }
        }

        @JvmStatic
        fun normalizeDisplayNumber(string: String?): String? {
            if (string.isNullOrEmpty()) return string
            for (i in string.indices) {
                val c = string[i]
                if (c == '.' || Character.isWhitespace(c) ||
                    Character.getType(c) == Character.DASH_PUNCTUATION.toInt()
                ) {
                    return StringBuilder(string).apply { setCharAt(i, CHANNEL_NUMBER_DELIMITER) }.toString()
                }
            }
            return string
        }

        @JvmStatic
        fun createPassthroughChannel(uri: Uri): ChannelImpl {
            require(TvContract.isChannelUriForPassthroughInput(uri)) {
                "URI is not a passthrough channel URI"
            }
            return createPassthroughChannel(uri.pathSegments[1])
        }

        @JvmStatic
        fun createPassthroughChannel(inputId: String): ChannelImpl =
            Builder().setInputId(inputId).setPassthrough(true).build()

        @JvmStatic
        fun isValid(channel: Channel?): Boolean =
            channel != null && (channel.id != INVALID_ID || channel.isPassthrough)
    }
}
