package com.android.tv.dvr.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.tv.TvContentRating
import android.media.tv.TvContract.Programs.Genres
import android.media.tv.TvContract.RecordedPrograms
import android.net.Uri
import android.util.Log
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import com.android.tv.R
import com.android.tv.common.TvContentRatingCache
import com.android.tv.common.data.RecordedProgramState
import com.android.tv.data.BaseProgramImpl
import com.android.tv.data.GenreItems
import com.android.tv.data.api.BaseProgram
import com.android.tv.util.TvProviderUtils
import java.util.concurrent.TimeUnit

/**
 * Eine Aufnahme aus TvContract.RecordedPrograms (ersetzt die AutoValue-Klasse).
 * Entfällt: interne Provider-Daten des eingebauten Tuners (InternalDataUtils).
 */
data class RecordedProgram(
    override val id: Long,
    val packageName: String,
    val inputId: String?,
    override val channelId: Long,
    override val title: String,
    override val seriesId: String?,
    override val seasonNumber: String,
    val seasonTitle: String,
    override val episodeNumber: String,
    override val episodeTitle: String,
    override val startTimeUtcMillis: Long,
    override val endTimeUtcMillis: Long,
    val state: RecordedProgramState,
    val broadcastGenres: List<String>,
    val canonicalGenres: List<String>,
    override val description: String,
    override val longDescription: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val audioLanguage: String,
    override val contentRatings: List<TvContentRating>,
    override val posterArtUri: String,
    override val thumbnailUri: String,
    val isSearchable: Boolean,
    val dataUri: Uri?,
    val dataBytes: Long,
    override val durationMillis: Long,
    val expireTimeUtcMillis: Long,
    val versionNumber: Int,
) : BaseProgramImpl() {

    class Builder internal constructor() {
        private var id = ID_NOT_SET.toLong()
        internal var packageName = ""
        private var inputId: String? = null
        private var channelId = ID_NOT_SET.toLong()
        internal var title = ""
        internal var seriesId: String? = ""
        private var seasonNumber = ""
        private var seasonTitle = ""
        internal var episodeNumber: String? = ""
        private var episodeTitle = ""
        private var startTimeUtcMillis = 0L
        private var endTimeUtcMillis = 0L
        private var state = RecordedProgramState.NOT_SET
        private var broadcastGenres: List<String> = emptyList()
        private var canonicalGenres: List<String> = emptyList()
        private var description = ""
        private var longDescription = ""
        private var videoWidth = 0
        private var videoHeight = 0
        private var audioLanguage = ""
        private var contentRatings: List<TvContentRating> = emptyList()
        private var posterArtUri = ""
        private var thumbnailUri = ""
        private var searchable = false
        private var dataUri: Uri? = Uri.EMPTY
        private var dataBytes = 0L
        private var durationMillis = 0L
        private var expireTimeUtcMillis = 0L
        private var versionNumber = 0

        fun setId(v: Long) = apply { id = v }
        fun setPackageName(v: String?) = apply { packageName = v.orEmpty() }
        fun setInputId(v: String?) = apply { inputId = v }
        fun setChannelId(v: Long) = apply { channelId = v }
        fun setTitle(v: String?) = apply { title = v.orEmpty() }
        fun setSeriesId(v: String?) = apply { seriesId = v }
        fun setSeasonNumber(v: String?) = apply { seasonNumber = v.orEmpty() }
        fun setSeasonTitle(v: String?) = apply { seasonTitle = v.orEmpty() }
        fun setEpisodeNumber(v: String?) = apply { episodeNumber = v }
        fun setEpisodeTitle(v: String?) = apply { episodeTitle = v.orEmpty() }
        fun setStartTimeUtcMillis(v: Long) = apply { startTimeUtcMillis = v }
        fun setEndTimeUtcMillis(v: Long) = apply { endTimeUtcMillis = v }
        fun setState(v: RecordedProgramState) = apply { state = v }

        /** Unbekannte Werte → NOT_SET. */
        fun setState(v: String?) = apply {
            state = if (v.isNullOrEmpty()) RecordedProgramState.NOT_SET else try {
                RecordedProgramState.valueOf(v)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown recording state $v", e)
                RecordedProgramState.NOT_SET
            }
        }

        fun setBroadcastGenres(v: String?) = apply { broadcastGenres = if (v.isNullOrEmpty()) emptyList() else Genres.decode(v).toList() }
        fun setBroadcastGenres(v: List<String>) = apply { broadcastGenres = v }
        fun setCanonicalGenres(v: String?) = apply { canonicalGenres = if (v.isNullOrEmpty()) emptyList() else Genres.decode(v).toList() }
        fun setCanonicalGenres(v: List<String>) = apply { canonicalGenres = v }
        fun setDescription(v: String?) = apply { description = v.orEmpty() }
        fun setLongDescription(v: String?) = apply { longDescription = v.orEmpty() }
        fun setVideoWidth(v: Int) = apply { videoWidth = v }
        fun setVideoHeight(v: Int) = apply { videoHeight = v }
        fun setAudioLanguage(v: String?) = apply { audioLanguage = v.orEmpty() }
        fun setContentRatings(v: List<TvContentRating>?) = apply { contentRatings = v ?: emptyList() }
        fun setPosterArtUri(v: String?) = apply { posterArtUri = v.orEmpty() }
        fun setThumbnailUri(v: String?) = apply { thumbnailUri = v.orEmpty() }
        fun setSearchable(v: Boolean) = apply { searchable = v }
        fun setDataUri(v: String?) = apply {
            dataUri = try {
                v?.let { Uri.parse(it) }
            } catch (e: Exception) {
                Uri.EMPTY
            }
        }
        fun setDataUri(v: Uri?) = apply { dataUri = v }
        fun setDataBytes(v: Long) = apply { dataBytes = v }
        fun setDurationMillis(v: Long) = apply { durationMillis = v }
        fun setExpireTimeUtcMillis(v: Long) = apply { expireTimeUtcMillis = v }
        fun setVersionNumber(v: Int) = apply { versionNumber = v }

        /** Ohne Titel keine Serie; mit Folgennummer ohne Serien-ID wird eine erzeugt. */
        fun build(): RecordedProgram {
            if (title.isEmpty()) {
                seriesId = null
            } else if (seriesId.isNullOrEmpty() && !episodeNumber.isNullOrEmpty()) {
                seriesId = BaseProgram.generateSeriesId(packageName, title)
            }
            return RecordedProgram(id, packageName, inputId, channelId, title, seriesId, seasonNumber, seasonTitle,
                episodeNumber.orEmpty(), episodeTitle, startTimeUtcMillis, endTimeUtcMillis, state, broadcastGenres,
                canonicalGenres, description, longDescription, videoWidth, videoHeight, audioLanguage, contentRatings,
                posterArtUri, thumbnailUri, searchable, dataUri, dataBytes, durationMillis, expireTimeUtcMillis, versionNumber)
        }

        internal fun from(p: RecordedProgram) = apply {
            id = p.id; packageName = p.packageName; inputId = p.inputId; channelId = p.channelId; title = p.title
            seriesId = p.seriesId; seasonNumber = p.seasonNumber; seasonTitle = p.seasonTitle; episodeNumber = p.episodeNumber
            episodeTitle = p.episodeTitle; startTimeUtcMillis = p.startTimeUtcMillis; endTimeUtcMillis = p.endTimeUtcMillis
            state = p.state; broadcastGenres = p.broadcastGenres; canonicalGenres = p.canonicalGenres; description = p.description
            longDescription = p.longDescription; videoWidth = p.videoWidth; videoHeight = p.videoHeight
            audioLanguage = p.audioLanguage; contentRatings = p.contentRatings; posterArtUri = p.posterArtUri
            thumbnailUri = p.thumbnailUri; searchable = p.isSearchable; dataUri = p.dataUri; dataBytes = p.dataBytes
            durationMillis = p.durationMillis; expireTimeUtcMillis = p.expireTimeUtcMillis; versionNumber = p.versionNumber
        }
    }

    override val canonicalGenreIds: IntArray get() = IntArray(canonicalGenres.size) { GenreItems.getId(canonicalGenres[it]) }
    override val isValid: Boolean get() = true

    /** "S1 E2" bzw. ohne Staffel bei Staffel "0". */
    fun getEpisodeDisplayNumber(context: Context): String? {
        if (episodeNumber.isEmpty()) return null
        return if (seasonNumber == "0") context.resources.getString(R.string.display_episode_number_format_no_season_number, episodeNumber)
        else context.resources.getString(R.string.display_episode_number_format, seasonNumber, episodeNumber)
    }

    /** Sichtbar, wenn kein Zustand gesetzt oder abgeschlossen. */
    val isVisible: Boolean get() = state == RecordedProgramState.NOT_SET || state == RecordedProgramState.FINISHED
    val isPartial: Boolean get() = state == RecordedProgramState.PARTIAL
    val uri: Uri get() = ContentUris.withAppendedId(RecordedPrograms.CONTENT_URI, id)

    /** Mehr als 5 min kürzer als geplant. */
    val isClipped: Boolean get() = endTimeUtcMillis - startTimeUtcMillis - durationMillis > CLIPPED_THRESHOLD_MS

    fun toBuilder(): Builder = Builder().from(this)

    @CheckResult
    fun withId(id: Long): RecordedProgram = toBuilder().setId(id).build()

    companion object {
        const val ID_NOT_SET = -1
        private const val TAG = "RecordedProgram"
        private val CLIPPED_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)

        @JvmField
        val PROJECTION = arrayOf(
            RecordedPrograms._ID, RecordedPrograms.COLUMN_PACKAGE_NAME, RecordedPrograms.COLUMN_INPUT_ID,
            RecordedPrograms.COLUMN_CHANNEL_ID, RecordedPrograms.COLUMN_TITLE, RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            RecordedPrograms.COLUMN_SEASON_TITLE, RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            RecordedPrograms.COLUMN_EPISODE_TITLE, RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
            RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, RecordedPrograms.COLUMN_BROADCAST_GENRE,
            RecordedPrograms.COLUMN_CANONICAL_GENRE, RecordedPrograms.COLUMN_SHORT_DESCRIPTION,
            RecordedPrograms.COLUMN_LONG_DESCRIPTION, RecordedPrograms.COLUMN_VIDEO_WIDTH, RecordedPrograms.COLUMN_VIDEO_HEIGHT,
            RecordedPrograms.COLUMN_AUDIO_LANGUAGE, RecordedPrograms.COLUMN_CONTENT_RATING, RecordedPrograms.COLUMN_POSTER_ART_URI,
            RecordedPrograms.COLUMN_THUMBNAIL_URI, RecordedPrograms.COLUMN_SEARCHABLE, RecordedPrograms.COLUMN_RECORDING_DATA_URI,
            RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
            RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS, RecordedPrograms.COLUMN_VERSION_NUMBER,
            RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
        )

        @JvmField
        val START_TIME_THEN_ID_COMPARATOR: Comparator<RecordedProgram> =
            compareBy<RecordedProgram> { it.startTimeUtcMillis }.thenBy { it.id }

        @JvmStatic
        fun builder(): Builder = Builder()

        /** Liest PROJECTION (+ optional series_id/state, falls vom Provider unterstützt). */
        @JvmStatic
        fun fromCursor(cursor: Cursor): RecordedProgram {
            var i = 0
            val b = Builder()
                .setId(cursor.getLong(i++)).setPackageName(cursor.getString(i++)).setInputId(cursor.getString(i++))
                .setChannelId(cursor.getLong(i++)).setTitle(cursor.getString(i++)).setSeasonNumber(cursor.getString(i++))
                .setSeasonTitle(cursor.getString(i++)).setEpisodeNumber(cursor.getString(i++).orEmpty())
                .setEpisodeTitle(cursor.getString(i++)).setStartTimeUtcMillis(cursor.getLong(i++))
                .setEndTimeUtcMillis(cursor.getLong(i++)).setBroadcastGenres(cursor.getString(i++))
                .setCanonicalGenres(cursor.getString(i++)).setDescription(cursor.getString(i++))
                .setLongDescription(cursor.getString(i++)).setVideoWidth(cursor.getInt(i++)).setVideoHeight(cursor.getInt(i++))
                .setAudioLanguage(cursor.getString(i++))
                .setContentRatings(TvContentRatingCache.getInstance().getRatings(cursor.getString(i++)))
                .setPosterArtUri(cursor.getString(i++)).setThumbnailUri(cursor.getString(i++))
                .setSearchable(cursor.getInt(i++) == 1).setDataUri(cursor.getString(i++).orEmpty())
                .setDataBytes(cursor.getLong(i++)).setDurationMillis(cursor.getLong(i++))
                .setExpireTimeUtcMillis(cursor.getLong(i++)).setVersionNumber(cursor.getInt(i++))
            i++ // interne Provider-Daten (nur eingebauter Tuner)
            if (TvProviderUtils.getRecordedProgramHasSeriesIdColumn()) b.setSeriesId(cursor.getString(i++).orEmpty())
            if (TvProviderUtils.getRecordedProgramHasStateColumn()) b.setState(cursor.getString(i++))
            return b.build()
        }

        @JvmStatic
        @WorkerThread
        fun toValues(context: Context, p: RecordedProgram): ContentValues = ContentValues().apply {
            if (p.id != ID_NOT_SET.toLong()) put(RecordedPrograms._ID, p.id)
            put(RecordedPrograms.COLUMN_INPUT_ID, p.inputId)
            put(RecordedPrograms.COLUMN_CHANNEL_ID, p.channelId)
            put(RecordedPrograms.COLUMN_TITLE, p.title)
            put(RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER, p.seasonNumber)
            put(RecordedPrograms.COLUMN_SEASON_TITLE, p.seasonTitle)
            put(RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, p.episodeNumber)
            put(RecordedPrograms.COLUMN_EPISODE_TITLE, p.episodeTitle)
            put(RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS, p.startTimeUtcMillis)
            put(RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, p.endTimeUtcMillis)
            put(RecordedPrograms.COLUMN_BROADCAST_GENRE, Genres.encode(*p.broadcastGenres.toTypedArray()))
            put(RecordedPrograms.COLUMN_CANONICAL_GENRE, Genres.encode(*p.canonicalGenres.toTypedArray()))
            put(RecordedPrograms.COLUMN_SHORT_DESCRIPTION, p.description)
            put(RecordedPrograms.COLUMN_LONG_DESCRIPTION, p.longDescription)
            if (p.videoWidth == 0) putNull(RecordedPrograms.COLUMN_VIDEO_WIDTH) else put(RecordedPrograms.COLUMN_VIDEO_WIDTH, p.videoWidth)
            if (p.videoHeight == 0) putNull(RecordedPrograms.COLUMN_VIDEO_HEIGHT) else put(RecordedPrograms.COLUMN_VIDEO_HEIGHT, p.videoHeight)
            put(RecordedPrograms.COLUMN_AUDIO_LANGUAGE, p.audioLanguage)
            put(RecordedPrograms.COLUMN_CONTENT_RATING, TvContentRatingCache.contentRatingsToString(p.contentRatings))
            put(RecordedPrograms.COLUMN_POSTER_ART_URI, p.posterArtUri)
            put(RecordedPrograms.COLUMN_THUMBNAIL_URI, p.thumbnailUri)
            put(RecordedPrograms.COLUMN_SEARCHABLE, if (p.isSearchable) 1 else 0)
            put(RecordedPrograms.COLUMN_RECORDING_DATA_URI, p.dataUri?.toString())
            put(RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, p.dataBytes)
            put(RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS, p.durationMillis)
            put(RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS, p.expireTimeUtcMillis)
            put(RecordedPrograms.COLUMN_VERSION_NUMBER, p.versionNumber)
            if (TvProviderUtils.checkSeriesIdColumn(context, RecordedPrograms.CONTENT_URI)) put(BaseProgram.COLUMN_SERIES_ID, p.seriesId)
            if (TvProviderUtils.checkStateColumn(context, RecordedPrograms.CONTENT_URI)) put(BaseProgram.COLUMN_STATE, p.state.toString())
        }

        @JvmStatic
        fun toArray(recordedPrograms: Collection<RecordedProgram>): Array<RecordedProgram> = recordedPrograms.toTypedArray()
    }
}
