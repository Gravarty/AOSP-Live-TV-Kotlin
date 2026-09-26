package com.android.tv.dvr.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Parcel
import android.os.Parcelable
import android.util.Range
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.CommonUtils
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.provider.DvrContract.Schedules
import java.util.Objects

/** Eine geplante bzw. laufende/abgeschlossene Aufnahme (Sendung oder Zeitraum). Unveränderlich außer der ID. */
class ScheduledRecording private constructor(
    var id: Long,
    val priority: Long,
    val inputId: String?,
    val channelId: Long,
    val programId: Long,
    val programTitle: String?,
    val type: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val seasonNumber: String?,
    val episodeNumber: String?,
    val episodeTitle: String?,
    val programDescription: String?,
    val programLongDescription: String?,
    val programPosterArtUri: String?,
    val programThumbnailUri: String?,
    val state: Int,
    val seriesRecordingId: Long,
    val recordedProgramId: Long?,
    val failedReason: Int?,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
) : Parcelable {

    class Builder internal constructor() {
        private var id = ID_NOT_SET
        private var priority = DvrScheduleManager.DEFAULT_PRIORITY
        private var inputId: String? = null
        private var channelId = 0L
        private var programId = ID_NOT_SET
        private var programTitle: String? = null
        private var type = 0
        private var startTimeMs = 0L
        private var endTimeMs = 0L
        private var seasonNumber: String? = null
        private var episodeNumber: String? = null
        private var episodeTitle: String? = null
        private var programDescription: String? = null
        private var programLongDescription: String? = null
        private var programPosterArtUri: String? = null
        private var programThumbnailUri: String? = null
        private var state = 0
        private var seriesRecordingId = ID_NOT_SET
        private var recordedProgramId: Long? = null
        private var failedReason: Int? = null
        private var startOffsetMs = DEFAULT_TIME_OFFSET
        private var endOffsetMs = DEFAULT_TIME_OFFSET

        fun setId(v: Long) = apply { id = v }
        fun setPriority(v: Long) = apply { priority = v }
        fun setInputId(v: String?) = apply { inputId = v }
        fun setChannelId(v: Long) = apply { channelId = v }
        fun setProgramId(v: Long) = apply { programId = v }
        fun setProgramTitle(v: String?) = apply { programTitle = v }
        internal fun setType(v: Int) = apply { type = v }
        fun setStartTimeMs(v: Long) = apply { startTimeMs = v }
        fun setEndTimeMs(v: Long) = apply { endTimeMs = v }
        fun setSeasonNumber(v: String?) = apply { seasonNumber = v }
        fun setEpisodeNumber(v: String?) = apply { episodeNumber = v }
        fun setEpisodeTitle(v: String?) = apply { episodeTitle = v }
        fun setProgramDescription(v: String?) = apply { programDescription = v }
        fun setProgramLongDescription(v: String?) = apply { programLongDescription = v }
        fun setProgramPosterArtUri(v: String?) = apply { programPosterArtUri = v }
        fun setProgramThumbnailUri(v: String?) = apply { programThumbnailUri = v }
        fun setState(v: Int) = apply { state = v }
        fun setSeriesRecordingId(v: Long) = apply { seriesRecordingId = v }
        fun setRecordedProgramId(v: Long?) = apply { recordedProgramId = v }
        fun setFailedReason(v: Int?) = apply { failedReason = v }
        fun setStartOffsetMs(v: Long) = apply { startOffsetMs = maxOf(0, v) }
        fun setEndOffsetMs(v: Long) = apply { endOffsetMs = maxOf(0, v) }

        fun build() = ScheduledRecording(id, priority, inputId, channelId, programId, programTitle, type, startTimeMs,
            endTimeMs, seasonNumber, episodeNumber, episodeTitle, programDescription, programLongDescription,
            programPosterArtUri, programThumbnailUri, state, seriesRecordingId, recordedProgramId, failedReason,
            startOffsetMs, endOffsetMs)
    }

    val duration: Long get() = endTimeMs - startTimeMs

    /** "S1 E2 Titel" bzw. ohne Staffel bei Staffel "0". */
    fun getEpisodeDisplayTitle(context: Context): String? {
        if (!episodeNumber.isNullOrEmpty()) {
            val title = episodeTitle ?: ""
            return if (seasonNumber == "0") {
                context.resources.getString(R.string.display_episode_title_format_no_season_number).format(episodeNumber, title)
            } else {
                context.resources.getString(R.string.display_episode_title_format).format(seasonNumber, episodeNumber, title)
            }
        }
        return episodeTitle
    }

    /** Sendungstitel, sonst Kanalname. */
    fun getProgramDisplayTitle(context: Context): String? {
        if (!programTitle.isNullOrEmpty()) return programTitle
        val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(channelId)
        return channel?.displayName ?: context.getString(R.string.no_program_information)
    }

    fun isOverLapping(period: Range<Long>) = startTimeMs < period.upper && endTimeMs > period.lower
    fun isOverLapping(schedule: ScheduledRecording) = startTimeMs < schedule.endTimeMs && endTimeMs > schedule.startTimeMs

    val isNotStarted: Boolean get() = state == STATE_RECORDING_NOT_STARTED
    val isInProgress: Boolean get() = state == STATE_RECORDING_IN_PROGRESS
    val isFinished: Boolean get() = state == STATE_RECORDING_FINISHED
    val isFailed: Boolean get() = state == STATE_RECORDING_FAILED

    override fun toString() = "ScheduledRecording[id=$id,inputId=$inputId,channelId=$channelId,programId=$programId," +
        "programTitle=$programTitle,type=$type,startTime=${CommonUtils.toIsoDateTimeString(startTimeMs)}($startTimeMs)," +
        "endTime=${CommonUtils.toIsoDateTimeString(endTimeMs)}($endTimeMs),seasonNumber=$seasonNumber," +
        "episodeNumber=$episodeNumber,episodeTitle=$episodeTitle,state=$state,failedReason=$failedReason," +
        "priority=$priority,seriesRecordingId=$seriesRecordingId,startOffsetMs=$startOffsetMs,endOffsetMs=$endOffsetMs]"

    override fun describeContents() = 0

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeLong(id)
        out.writeLong(priority)
        out.writeString(inputId)
        out.writeLong(channelId)
        out.writeLong(programId)
        out.writeString(programTitle)
        out.writeInt(type)
        out.writeLong(startTimeMs)
        out.writeLong(endTimeMs)
        out.writeString(seasonNumber)
        out.writeString(episodeNumber)
        out.writeString(episodeTitle)
        out.writeString(programDescription)
        out.writeString(programLongDescription)
        out.writeString(programPosterArtUri)
        out.writeString(programThumbnailUri)
        out.writeInt(state)
        out.writeString(recordingFailedReason(failedReason))
        out.writeLong(seriesRecordingId)
        out.writeLong(startOffsetMs)
        out.writeLong(endOffsetMs)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ScheduledRecording) return false
        return id == other.id && priority == other.priority && channelId == other.channelId && programId == other.programId &&
            programTitle == other.programTitle && type == other.type && startTimeMs == other.startTimeMs &&
            endTimeMs == other.endTimeMs && seasonNumber == other.seasonNumber && episodeNumber == other.episodeNumber &&
            episodeTitle == other.episodeTitle && programDescription == other.programDescription &&
            programLongDescription == other.programLongDescription && programPosterArtUri == other.programPosterArtUri &&
            programThumbnailUri == other.programThumbnailUri && state == other.state && failedReason == other.failedReason &&
            seriesRecordingId == other.seriesRecordingId && startOffsetMs == other.startOffsetMs && endOffsetMs == other.endOffsetMs
    }

    override fun hashCode() = Objects.hash(id, priority, channelId, programId, programTitle, type, startTimeMs, endTimeMs,
        seasonNumber, episodeNumber, episodeTitle, programDescription, programLongDescription, programPosterArtUri,
        programThumbnailUri, state, failedReason, seriesRecordingId, startOffsetMs, endOffsetMs)

    companion object {
        private const val TAG = "ScheduledRecording"
        const val ID_NOT_SET = 0L
        const val DEFAULT_PRIORITY = Long.MAX_VALUE shr 1
        const val DEFAULT_TIME_OFFSET = 0L

        const val STATE_RECORDING_NOT_STARTED = 0
        const val STATE_RECORDING_IN_PROGRESS = 1
        const val STATE_RECORDING_FINISHED = 2
        const val STATE_RECORDING_FAILED = 3
        const val STATE_RECORDING_CLIPPED = 4
        const val STATE_RECORDING_DELETED = 5
        const val STATE_RECORDING_CANCELED = 6

        const val FAILED_REASON_OTHER = 0
        const val FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED = 1
        const val FAILED_REASON_NOT_FINISHED = 2
        const val FAILED_REASON_SCHEDULER_STOPPED = 3
        const val FAILED_REASON_INVALID_CHANNEL = 4
        const val FAILED_REASON_MESSAGE_NOT_SENT = 5
        const val FAILED_REASON_CONNECTION_FAILED = 6
        const val FAILED_REASON_RESOURCE_BUSY = 7
        const val FAILED_REASON_INPUT_UNAVAILABLE = 8
        const val FAILED_REASON_INPUT_DVR_UNSUPPORTED = 9
        const val FAILED_REASON_INSUFFICIENT_SPACE = 10

        const val TYPE_TIMED = 1
        const val TYPE_PROGRAM = 2

        @JvmField val START_TIME_COMPARATOR: Comparator<ScheduledRecording> = compareBy { it.startTimeMs }
        @JvmField val END_TIME_COMPARATOR: Comparator<ScheduledRecording> = compareBy { it.endTimeMs }
        @JvmField val ID_COMPARATOR: Comparator<ScheduledRecording> = compareBy { it.id }
        @JvmField val PRIORITY_COMPARATOR: Comparator<ScheduledRecording> = compareBy { it.priority }
        /** Startzeit, dann höhere Priorität, dann höhere ID zuerst. */
        @JvmField val START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR: Comparator<ScheduledRecording> =
            START_TIME_COMPARATOR.then(PRIORITY_COMPARATOR.reversed()).then(ID_COMPARATOR.reversed())

        @JvmStatic
        fun builder(inputId: String?, p: Program): Builder = Builder()
            .setInputId(inputId).setChannelId(p.channelId).setStartTimeMs(p.startTimeUtcMillis).setEndTimeMs(p.endTimeUtcMillis)
            .setProgramId(p.id).setProgramTitle(p.title).setSeasonNumber(p.seasonNumber).setEpisodeNumber(p.episodeNumber)
            .setEpisodeTitle(p.episodeTitle).setProgramDescription(p.description).setProgramLongDescription(p.longDescription)
            .setProgramPosterArtUri(p.posterArtUri).setProgramThumbnailUri(p.thumbnailUri).setType(TYPE_PROGRAM)

        @JvmStatic
        fun builder(inputId: String?, channelId: Long, startTime: Long, endTime: Long): Builder = Builder()
            .setInputId(inputId).setChannelId(channelId).setStartTimeMs(startTime).setEndTimeMs(endTime).setType(TYPE_TIMED)

        @JvmStatic
        fun builder(p: RecordedProgram): Builder = Builder()
            .setInputId(p.inputId).setChannelId(p.channelId)
            .setType(if (!p.title.isNullOrEmpty()) TYPE_PROGRAM else TYPE_TIMED)
            .setStartTimeMs(p.startTimeUtcMillis).setEndTimeMs(p.endTimeUtcMillis).setProgramTitle(p.title)
            .setSeasonNumber(p.seasonNumber).setEpisodeNumber(p.episodeNumber).setEpisodeTitle(p.episodeTitle)
            .setProgramDescription(p.description).setProgramLongDescription(p.longDescription)
            .setProgramPosterArtUri(p.posterArtUri).setProgramThumbnailUri(p.thumbnailUri)
            .setState(STATE_RECORDING_FINISHED).setRecordedProgramId(p.id)

        /** Kopie zum Ändern (recordedProgramId wird wie im Original nicht übernommen). */
        @JvmStatic
        fun buildFrom(orig: ScheduledRecording): Builder = Builder()
            .setId(orig.id).setInputId(orig.inputId).setChannelId(orig.channelId).setEndTimeMs(orig.endTimeMs)
            .setSeriesRecordingId(orig.seriesRecordingId).setPriority(orig.priority).setProgramId(orig.programId)
            .setProgramTitle(orig.programTitle).setStartTimeMs(orig.startTimeMs).setSeasonNumber(orig.seasonNumber)
            .setEpisodeNumber(orig.episodeNumber).setEpisodeTitle(orig.episodeTitle)
            .setProgramDescription(orig.programDescription).setProgramLongDescription(orig.programLongDescription)
            .setProgramPosterArtUri(orig.programPosterArtUri).setProgramThumbnailUri(orig.programThumbnailUri)
            .setState(orig.state).setFailedReason(orig.failedReason).setType(orig.type)
            .setStartOffsetMs(orig.startOffsetMs).setEndOffsetMs(orig.endOffsetMs)

        @JvmField
        val PROJECTION = arrayOf(
            Schedules._ID, Schedules.COLUMN_PRIORITY, Schedules.COLUMN_TYPE, Schedules.COLUMN_INPUT_ID,
            Schedules.COLUMN_CHANNEL_ID, Schedules.COLUMN_PROGRAM_ID, Schedules.COLUMN_PROGRAM_TITLE,
            Schedules.COLUMN_START_TIME_UTC_MILLIS, Schedules.COLUMN_END_TIME_UTC_MILLIS, Schedules.COLUMN_SEASON_NUMBER,
            Schedules.COLUMN_EPISODE_NUMBER, Schedules.COLUMN_EPISODE_TITLE, Schedules.COLUMN_PROGRAM_DESCRIPTION,
            Schedules.COLUMN_PROGRAM_LONG_DESCRIPTION, Schedules.COLUMN_PROGRAM_POST_ART_URI,
            Schedules.COLUMN_PROGRAM_THUMBNAIL_URI, Schedules.COLUMN_STATE, Schedules.COLUMN_FAILED_REASON,
            Schedules.COLUMN_SERIES_RECORDING_ID,
        )

        @JvmField
        val PROJECTION_WITH_TIME_OFFSET = PROJECTION + arrayOf(Schedules.COLUMN_START_OFFSET_MILLIS, Schedules.COLUMN_END_OFFSET_MILLIS)

        private fun readCommon(c: Cursor): Builder {
            var i = -1
            return Builder()
                .setId(c.getLong(++i)).setPriority(c.getLong(++i)).setType(recordingType(c.getString(++i)))
                .setInputId(c.getString(++i)).setChannelId(c.getLong(++i)).setProgramId(c.getLong(++i))
                .setProgramTitle(c.getString(++i)).setStartTimeMs(c.getLong(++i)).setEndTimeMs(c.getLong(++i))
                .setSeasonNumber(c.getString(++i)).setEpisodeNumber(c.getString(++i)).setEpisodeTitle(c.getString(++i))
                .setProgramDescription(c.getString(++i)).setProgramLongDescription(c.getString(++i))
                .setProgramPosterArtUri(c.getString(++i)).setProgramThumbnailUri(c.getString(++i))
                .setState(recordingState(c.getString(++i))).setFailedReason(recordingFailedReason(c.getString(++i)))
                .setSeriesRecordingId(c.getLong(++i))
        }

        @JvmStatic
        fun fromCursor(c: Cursor): ScheduledRecording = readCommon(c).build()

        @JvmStatic
        fun fromCursorWithTimeOffset(c: Cursor): ScheduledRecording =
            readCommon(c).setStartOffsetMs(c.getLong(PROJECTION.size)).setEndOffsetMs(c.getLong(PROJECTION.size + 1)).build()

        @JvmStatic
        fun toContentValues(r: ScheduledRecording): ContentValues = ContentValues().apply {
            if (r.id != ID_NOT_SET) put(Schedules._ID, r.id)
            put(Schedules.COLUMN_INPUT_ID, r.inputId)
            put(Schedules.COLUMN_CHANNEL_ID, r.channelId)
            put(Schedules.COLUMN_PROGRAM_ID, r.programId)
            put(Schedules.COLUMN_PROGRAM_TITLE, r.programTitle)
            put(Schedules.COLUMN_PRIORITY, r.priority)
            put(Schedules.COLUMN_START_TIME_UTC_MILLIS, r.startTimeMs)
            put(Schedules.COLUMN_END_TIME_UTC_MILLIS, r.endTimeMs)
            put(Schedules.COLUMN_SEASON_NUMBER, r.seasonNumber)
            put(Schedules.COLUMN_EPISODE_NUMBER, r.episodeNumber)
            put(Schedules.COLUMN_EPISODE_TITLE, r.episodeTitle)
            put(Schedules.COLUMN_PROGRAM_DESCRIPTION, r.programDescription)
            put(Schedules.COLUMN_PROGRAM_LONG_DESCRIPTION, r.programLongDescription)
            put(Schedules.COLUMN_PROGRAM_POST_ART_URI, r.programPosterArtUri)
            put(Schedules.COLUMN_PROGRAM_THUMBNAIL_URI, r.programThumbnailUri)
            put(Schedules.COLUMN_STATE, recordingState(r.state))
            put(Schedules.COLUMN_FAILED_REASON, recordingFailedReason(r.failedReason))
            put(Schedules.COLUMN_TYPE, recordingType(r.type))
            if (r.seriesRecordingId != ID_NOT_SET) put(Schedules.COLUMN_SERIES_RECORDING_ID, r.seriesRecordingId)
            else putNull(Schedules.COLUMN_SERIES_RECORDING_ID)
        }

        @JvmStatic
        fun toContentValuesWithTimeOffset(r: ScheduledRecording): ContentValues = toContentValues(r).apply {
            put(Schedules.COLUMN_START_OFFSET_MILLIS, r.startOffsetMs)
            put(Schedules.COLUMN_END_OFFSET_MILLIS, r.endOffsetMs)
        }

        @JvmStatic
        fun fromParcel(p: Parcel): ScheduledRecording = Builder()
            .setId(p.readLong()).setPriority(p.readLong()).setInputId(p.readString()).setChannelId(p.readLong())
            .setProgramId(p.readLong()).setProgramTitle(p.readString()).setType(p.readInt()).setStartTimeMs(p.readLong())
            .setEndTimeMs(p.readLong()).setSeasonNumber(p.readString()).setEpisodeNumber(p.readString())
            .setEpisodeTitle(p.readString()).setProgramDescription(p.readString()).setProgramLongDescription(p.readString())
            .setProgramPosterArtUri(p.readString()).setProgramThumbnailUri(p.readString()).setState(p.readInt())
            .setFailedReason(recordingFailedReason(p.readString())).setSeriesRecordingId(p.readLong())
            .setStartOffsetMs(p.readLong()).setEndOffsetMs(p.readLong()).build()

        @JvmField
        val CREATOR = object : Parcelable.Creator<ScheduledRecording> {
            override fun createFromParcel(p: Parcel) = fromParcel(p)
            override fun newArray(size: Int) = arrayOfNulls<ScheduledRecording>(size)
        }

        @JvmStatic
        fun toArray(schedules: Collection<ScheduledRecording>): Array<ScheduledRecording> = schedules.toTypedArray()

        private fun recordingType(type: String?): Int = when (type) {
            Schedules.TYPE_TIMED -> TYPE_TIMED
            Schedules.TYPE_PROGRAM -> TYPE_PROGRAM
            else -> {
                SoftPreconditions.checkArgument(false, TAG, "Unknown recording type %s", type)
                TYPE_TIMED
            }
        }

        private fun recordingType(type: Int): String = when (type) {
            TYPE_TIMED -> Schedules.TYPE_TIMED
            TYPE_PROGRAM -> Schedules.TYPE_PROGRAM
            else -> {
                SoftPreconditions.checkArgument(false, TAG, "Unknown recording type %s", type)
                Schedules.TYPE_TIMED
            }
        }

        private val STATE_MAP = mapOf(
            STATE_RECORDING_NOT_STARTED to Schedules.STATE_RECORDING_NOT_STARTED,
            STATE_RECORDING_IN_PROGRESS to Schedules.STATE_RECORDING_IN_PROGRESS,
            STATE_RECORDING_FINISHED to Schedules.STATE_RECORDING_FINISHED,
            STATE_RECORDING_FAILED to Schedules.STATE_RECORDING_FAILED,
            STATE_RECORDING_CLIPPED to Schedules.STATE_RECORDING_CLIPPED,
            STATE_RECORDING_DELETED to Schedules.STATE_RECORDING_DELETED,
            STATE_RECORDING_CANCELED to Schedules.STATE_RECORDING_CANCELED,
        )

        private fun recordingState(state: String?): Int =
            STATE_MAP.entries.firstOrNull { it.value == state }?.key ?: run {
                SoftPreconditions.checkArgument(false, TAG, "Unknown recording state %s", state)
                STATE_RECORDING_NOT_STARTED
            }

        private fun recordingState(state: Int): String = STATE_MAP[state] ?: run {
            SoftPreconditions.checkArgument(false, TAG, "Unknown recording state %s", state)
            Schedules.STATE_RECORDING_NOT_STARTED
        }

        private val FAILED_MAP = mapOf(
            FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED to Schedules.FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED,
            FAILED_REASON_NOT_FINISHED to Schedules.FAILED_REASON_NOT_FINISHED,
            FAILED_REASON_SCHEDULER_STOPPED to Schedules.FAILED_REASON_SCHEDULER_STOPPED,
            FAILED_REASON_INVALID_CHANNEL to Schedules.FAILED_REASON_INVALID_CHANNEL,
            FAILED_REASON_MESSAGE_NOT_SENT to Schedules.FAILED_REASON_MESSAGE_NOT_SENT,
            FAILED_REASON_CONNECTION_FAILED to Schedules.FAILED_REASON_CONNECTION_FAILED,
            FAILED_REASON_RESOURCE_BUSY to Schedules.FAILED_REASON_RESOURCE_BUSY,
            FAILED_REASON_INPUT_UNAVAILABLE to Schedules.FAILED_REASON_INPUT_UNAVAILABLE,
            FAILED_REASON_INPUT_DVR_UNSUPPORTED to Schedules.FAILED_REASON_INPUT_DVR_UNSUPPORTED,
            FAILED_REASON_INSUFFICIENT_SPACE to Schedules.FAILED_REASON_INSUFFICIENT_SPACE,
            FAILED_REASON_OTHER to Schedules.FAILED_REASON_OTHER,
        )

        private fun recordingFailedReason(reason: String?): Int? {
            if (reason.isNullOrEmpty()) return null
            return FAILED_MAP.entries.firstOrNull { it.value == reason }?.key ?: FAILED_REASON_OTHER
        }

        private fun recordingFailedReason(reason: Int?): String? =
            if (reason == null) null else FAILED_MAP[reason] ?: Schedules.FAILED_REASON_OTHER
    }
}
