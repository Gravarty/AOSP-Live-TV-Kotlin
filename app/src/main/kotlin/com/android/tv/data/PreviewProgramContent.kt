package com.android.tv.data

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.RecordedProgram

/** Inhalt eines Vorschau-Programms im Launcher (Kanal-Sendung oder Aufnahme). */
data class PreviewProgramContent(
    val id: Long,
    val previewChannelId: Long,
    val type: Int,
    val live: Boolean,
    val title: String?,
    val description: String?,
    val posterArtUri: Uri?,
    val intentUri: Uri?,
    val previewVideoUri: Uri?,
) {
    companion object {
        internal const val PARAM_INPUT = "input"

        @JvmStatic
        fun createFromProgram(context: Context, previewChannelId: Long, program: Program): PreviewProgramContent? {
            val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(program.channelId)
            return channel?.let { createFromProgram(previewChannelId, program, it) }
        }

        @JvmStatic
        fun createFromRecordedProgram(context: Context, previewChannelId: Long, recordedProgram: RecordedProgram): PreviewProgramContent {
            val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(recordedProgram.channelId)
            return createFromRecordedProgram(previewChannelId, recordedProgram, channel)
        }

        internal fun createFromProgram(previewChannelId: Long, program: Program, channel: Channel): PreviewProgramContent {
            val channelDisplayName = channel.displayName
            return PreviewProgramContent(
                id = program.id,
                previewChannelId = previewChannelId,
                type = TvContractCompat.PreviewPrograms.TYPE_CHANNEL,
                live = true,
                title = program.title,
                description = if (!channelDisplayName.isNullOrEmpty()) channelDisplayName else channel.displayNumber,
                posterArtUri = Uri.parse(program.posterArtUri),
                intentUri = channel.uri,
                previewVideoUri = PreviewDataManager.PreviewDataUtils.addQueryParamToUri(channel.uri, PARAM_INPUT to channel.inputId),
            )
        }

        internal fun createFromRecordedProgram(previewChannelId: Long, recordedProgram: RecordedProgram, channel: Channel?): PreviewProgramContent {
            val recordedProgramUri = TvContractCompat.buildRecordedProgramUri(recordedProgram.id)
            return PreviewProgramContent(
                id = recordedProgram.id,
                previewChannelId = previewChannelId,
                type = TvContractCompat.PreviewPrograms.TYPE_CLIP,
                live = false,
                title = recordedProgram.title,
                description = channel?.displayName ?: "",
                posterArtUri = Uri.parse(recordedProgram.posterArtUri),
                intentUri = recordedProgramUri,
                previewVideoUri = PreviewDataManager.PreviewDataUtils.addQueryParamToUri(
                    recordedProgramUri, PARAM_INPUT to recordedProgram.inputId),
            )
        }
    }
}
