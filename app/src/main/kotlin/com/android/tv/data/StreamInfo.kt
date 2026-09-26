package com.android.tv.data

import android.media.tv.TvContentRating
import com.android.tv.data.api.Channel

interface StreamInfo {
    val currentChannel: Channel?
    val blockedContentRating: TvContentRating?
    val videoWidth: Int
    val videoHeight: Int
    val videoFrameRate: Float
    val videoDisplayAspectRatio: Float
    val videoDefinitionLevel: Int
    val audioChannelCount: Int
    val streamVolume: Float
    fun hasClosedCaption(): Boolean
    val isVideoAvailable: Boolean
    val isVideoOrAudioAvailable: Boolean
    val videoUnavailableReason: Int

    companion object {
        const val VIDEO_DEFINITION_LEVEL_UNKNOWN = 0
        const val VIDEO_DEFINITION_LEVEL_SD = 1
        const val VIDEO_DEFINITION_LEVEL_HD = 2
        const val VIDEO_DEFINITION_LEVEL_FULL_HD = 3
        const val VIDEO_DEFINITION_LEVEL_ULTRA_HD = 4
        const val AUDIO_CHANNEL_COUNT_UNKNOWN = 0
    }
}
