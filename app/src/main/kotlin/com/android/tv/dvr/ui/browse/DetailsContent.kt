package com.android.tv.dvr.ui.browse

import android.content.Context
import android.media.tv.TvContract
import android.text.TextUtils
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.DvrUiHelper

/** Inhalt der Detailansicht (Titel, Zeiten, Beschreibung, Bild-URIs). */
class DetailsContent private constructor() {
    /** Titel. */
    var title: CharSequence? = null
        private set
    /** Startzeit. */
    var startTimeUtcMillis: Long = 0
        private set
    /** Endzeit. */
    var endTimeUtcMillis: Long = 0
        private set
    /** Beschreibung. */
    var description: String? = null
        private set
    /** Logo-Bild-URI. */
    var logoImageUri: String? = null
        private set
    /** Hintergrundbild-URI. */
    var backgroundImageUri: String? = null
        private set
    /** Ob die Bild-URIs vom Kanallogo stammen. */
    var isUsingChannelLogo: Boolean = false
        private set
    private var showErrorMessage: Boolean = false

    /** Ob die Fehlermeldung angezeigt werden soll. */
    fun shouldShowErrorMessage(): Boolean = showErrorMessage

    /** Übernimmt die Werte eines anderen Inhalts. */
    fun copyFrom(other: DetailsContent) {
        if (this === other) return
        title = other.title
        startTimeUtcMillis = other.startTimeUtcMillis
        endTimeUtcMillis = other.endTimeUtcMillis
        description = other.description
        logoImageUri = other.logoImageUri
        backgroundImageUri = other.backgroundImageUri
        isUsingChannelLogo = other.isUsingChannelLogo
        showErrorMessage = other.showErrorMessage
    }

    /** Baut einen [DetailsContent]. */
    class Builder {
        private val detailsContent = DetailsContent().apply {
            startTimeUtcMillis = INVALID_TIME
            endTimeUtcMillis = INVALID_TIME
        }
        private var channelId = 0L
        private var programTitle: String? = null
        private var seasonNumber: String? = null
        private var episodeNumber: String? = null
        private var posterArtUri: String? = null
        private var thumbnailUri: String? = null

        fun setTitle(title: CharSequence?) = apply { detailsContent.title = title }
        fun setStartTimeUtcMillis(startTimeUtcMillis: Long) = apply { detailsContent.startTimeUtcMillis = startTimeUtcMillis }
        fun setEndTimeUtcMillis(endTimeUtcMillis: Long) = apply { detailsContent.endTimeUtcMillis = endTimeUtcMillis }
        fun setDescription(description: String?) = apply { detailsContent.description = description }
        fun setLogoImageUri(logoImageUri: String?) = apply { detailsContent.logoImageUri = logoImageUri }
        fun setBackgroundImageUri(backgroundImageUri: String?) = apply { detailsContent.backgroundImageUri = backgroundImageUri }

        internal fun setProgramTitle(programTitle: String?) = apply { this.programTitle = programTitle }
        internal fun setSeasonNumber(seasonNumber: String?) = apply { this.seasonNumber = seasonNumber }
        internal fun setEpisodeNumber(episodeNumber: String?) = apply { this.episodeNumber = episodeNumber }
        internal fun setChannelId(channelId: Long) = apply { this.channelId = channelId }
        internal fun setPosterArtUri(posterArtUri: String?) = apply { this.posterArtUri = posterArtUri }
        internal fun setThumbnailUri(thumbnailUri: String?) = apply { this.thumbnailUri = thumbnailUri }
        internal fun setShowErrorMessage(showErrorMessage: Boolean) = apply { detailsContent.showErrorMessage = showErrorMessage }

        private fun createStyledTitle(context: Context, channel: Channel?) {
            val title = DvrUiHelper.getStyledTitleWithEpisodeNumber(
                context, programTitle, seasonNumber, episodeNumber,
                R.style.text_appearance_card_view_episode_number)
            detailsContent.title = if (TextUtils.isEmpty(title)) {
                if (channel != null) channel.displayName
                else context.resources.getString(R.string.no_program_information)
            } else {
                title
            }
        }

        private fun createImageUris(channel: Channel?) {
            detailsContent.logoImageUri = null
            detailsContent.backgroundImageUri = null
            detailsContent.isUsingChannelLogo = false
            if (!TextUtils.isEmpty(posterArtUri) && !TextUtils.isEmpty(thumbnailUri)) {
                detailsContent.logoImageUri = posterArtUri
                detailsContent.backgroundImageUri = thumbnailUri
            } else if (!TextUtils.isEmpty(posterArtUri)) {
                // thumbnailUri ist leer
                detailsContent.logoImageUri = posterArtUri
                detailsContent.backgroundImageUri = posterArtUri
            } else if (!TextUtils.isEmpty(thumbnailUri)) {
                // posterArtUri ist leer
                detailsContent.logoImageUri = thumbnailUri
                detailsContent.backgroundImageUri = thumbnailUri
            }
            if (TextUtils.isEmpty(detailsContent.logoImageUri) && channel != null) {
                val channelLogoUri = TvContract.buildChannelLogoUri(channel.id).toString()
                detailsContent.logoImageUri = channelLogoUri
                detailsContent.backgroundImageUri = channelLogoUri
                detailsContent.isUsingChannelLogo = true
            }
        }

        /** Baut den Inhalt (Titel und Bilder ggf. aus dem Kanal). */
        fun build(context: Context): DetailsContent {
            val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(channelId)
            if (detailsContent.title == null) createStyledTitle(context, channel)
            if (detailsContent.backgroundImageUri == null && detailsContent.logoImageUri == null) {
                createImageUris(channel)
            }
            return DetailsContent().also { it.copyFrom(detailsContent) }
        }
    }

    companion object {
        /** Ungültige Zeit. */
        const val INVALID_TIME = -1L

        @JvmStatic
        internal fun createFromRecordedProgram(context: Context, recordedProgram: RecordedProgram): DetailsContent =
            Builder()
                .setChannelId(recordedProgram.channelId)
                .setProgramTitle(recordedProgram.title)
                .setSeasonNumber(recordedProgram.seasonNumber)
                .setEpisodeNumber(recordedProgram.episodeNumber)
                .setStartTimeUtcMillis(recordedProgram.startTimeUtcMillis)
                .setEndTimeUtcMillis(recordedProgram.endTimeUtcMillis)
                .setDescription(
                    if (TextUtils.isEmpty(recordedProgram.longDescription)) recordedProgram.description
                    else recordedProgram.longDescription)
                .setPosterArtUri(recordedProgram.posterArtUri)
                .setThumbnailUri(recordedProgram.thumbnailUri)
                .build(context)

        @JvmStatic
        fun createFromProgram(context: Context, program: Program): DetailsContent =
            Builder()
                .setChannelId(program.channelId)
                .setProgramTitle(program.title)
                .setSeasonNumber(program.seasonNumber)
                .setEpisodeNumber(program.episodeNumber)
                .setStartTimeUtcMillis(program.startTimeUtcMillis)
                .setEndTimeUtcMillis(program.endTimeUtcMillis)
                .setDescription(
                    if (TextUtils.isEmpty(program.longDescription)) program.description
                    else program.longDescription)
                .setPosterArtUri(program.posterArtUri)
                .setThumbnailUri(program.thumbnailUri)
                .build(context)

        @JvmStatic
        internal fun createFromSeriesRecording(context: Context, seriesRecording: SeriesRecording): DetailsContent =
            Builder()
                .setChannelId(seriesRecording.channelId)
                .setTitle(seriesRecording.title)
                .setDescription(
                    if (TextUtils.isEmpty(seriesRecording.longDescription)) seriesRecording.description
                    else seriesRecording.longDescription)
                .setPosterArtUri(seriesRecording.posterUri)
                .setThumbnailUri(seriesRecording.photoUri)
                .build(context)

        @JvmStatic
        internal fun createFromScheduledRecording(context: Context, scheduledRecording: ScheduledRecording): DetailsContent {
            val channel = TvSingletons.getSingletons(context).getChannelDataManager()
                .getChannel(scheduledRecording.channelId)
            var description: String? =
                if (scheduledRecording.state == ScheduledRecording.STATE_RECORDING_FAILED) {
                    getErrorMessage(context, scheduledRecording)
                } else if (!TextUtils.isEmpty(scheduledRecording.programDescription)) {
                    scheduledRecording.programDescription
                } else {
                    scheduledRecording.programLongDescription
                }
            if (TextUtils.isEmpty(description)) description = channel?.description
            return Builder()
                .setChannelId(scheduledRecording.channelId)
                .setProgramTitle(scheduledRecording.programTitle)
                .setSeasonNumber(scheduledRecording.seasonNumber)
                .setEpisodeNumber(scheduledRecording.episodeNumber)
                .setStartTimeUtcMillis(scheduledRecording.startTimeMs)
                .setEndTimeUtcMillis(scheduledRecording.endTimeMs)
                .setDescription(description)
                .setPosterArtUri(scheduledRecording.programPosterArtUri)
                .setThumbnailUri(scheduledRecording.programThumbnailUri)
                .setShowErrorMessage(scheduledRecording.state == ScheduledRecording.STATE_RECORDING_FAILED)
                .build(context)
        }

        private fun getErrorMessage(context: Context, recording: ScheduledRecording): String {
            val reason = recording.failedReason ?: ScheduledRecording.FAILED_REASON_OTHER
            return when (reason) {
                ScheduledRecording.FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED ->
                    context.getString(R.string.dvr_recording_failed_not_started)
                ScheduledRecording.FAILED_REASON_RESOURCE_BUSY ->
                    context.getString(R.string.dvr_recording_failed_resource_busy)
                ScheduledRecording.FAILED_REASON_INPUT_UNAVAILABLE ->
                    context.getString(R.string.dvr_recording_failed_input_unavailable, recording.inputId)
                ScheduledRecording.FAILED_REASON_INPUT_DVR_UNSUPPORTED ->
                    context.getString(R.string.dvr_recording_failed_input_dvr_unsupported)
                ScheduledRecording.FAILED_REASON_INSUFFICIENT_SPACE ->
                    context.getString(R.string.dvr_recording_failed_insufficient_space)
                // OTHER, NOT_FINISHED, SCHEDULER_STOPPED, INVALID_CHANNEL, MESSAGE_NOT_SENT, CONNECTION_FAILED
                else -> context.getString(R.string.dvr_recording_failed_system_failure, reason)
            }
        }
    }
}
