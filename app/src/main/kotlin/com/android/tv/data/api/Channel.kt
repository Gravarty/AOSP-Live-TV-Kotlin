package com.android.tv.data.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.tv.util.images.ImageLoader.ImageLoaderCallback

/** 1:1-Port von com.android.tv.data.api.Channel. */
interface Channel {
    val id: Long
    val uri: Uri
    val packageName: String
    val inputId: String
    val type: String?
    val displayNumber: String?
    val displayName: String?
    val description: String?
    val videoFormat: String?
    val isPassthrough: Boolean
    val displayText: String
    val appLinkText: String?
    val appLinkColor: Int
    val appLinkIconUri: String?
    val appLinkPosterArtUri: String?
    val appLinkIntentUri: String?
    var networkAffiliation: String?
    var logoUri: String?
    val isRecordingProhibited: Boolean
    val isPhysicalTunerChannel: Boolean
    var isBrowsable: Boolean
    val isSearchable: Boolean
    var isLocked: Boolean

    fun hasSameReadOnlyInfo(other: Channel?): Boolean
    fun setChannelLogoExist(result: Boolean)
    fun channelLogoExists(): Boolean
    fun copyFrom(channel: Channel)

    fun loadBitmap(
        context: Context,
        loadImageType: Int,
        maxWidth: Int,
        maxHeight: Int,
        callback: ImageLoaderCallback<*>,
    )

    fun getAppLinkType(context: Context): Int
    fun getAppLinkIntent(context: Context): Intent?
    fun prefetchImage(context: Context, loadImageType: Int, maxWidth: Int, maxHeight: Int)

    companion object {
        const val INVALID_ID = -1L
        const val LOAD_IMAGE_TYPE_CHANNEL_LOGO = 1
        const val LOAD_IMAGE_TYPE_APP_LINK_ICON = 2
        const val LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART = 3
        const val APP_LINK_TYPE_NONE = -1
        const val APP_LINK_TYPE_CHANNEL = 1
        const val APP_LINK_TYPE_APP = 2
        const val CHANNEL_NUMBER_DELIMITER = '-'
    }
}
