package com.android.tv.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.tv.TvContentRating
import android.media.tv.TvContract
import android.media.tv.TvContract.Programs
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.WorkerThread
import com.android.tv.common.TvContentRatingCache
import com.android.tv.data.api.BaseProgram
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.data.api.Program.CriticScore
import com.android.tv.util.TvProviderUtils
import com.android.tv.util.Utils
import com.android.tv.util.images.ImageLoader

/**
 * Port von com.android.tv.data.ProgramImpl.
 * Entfällt: Pre-N-Spalten (minSdk 30) und InternalProviderData des eingebauten Tuners.
 */
class ProgramImpl private constructor() : BaseProgramImpl(), Parcelable, Program {

    override var id = 0L; private set
    override var packageName: String? = null; private set
    override var channelId = 0L; private set
    override var title: String? = null; private set
    override var seriesId: String? = null; private set
    override var episodeTitle: String? = null; private set
    override var seasonNumber: String? = null; private set
    override var seasonTitle: String? = null; private set
    override var episodeNumber: String? = null; private set
    override var startTimeUtcMillis = 0L; private set
    override var endTimeUtcMillis = 0L; private set
    private var durationString: String? = null
    override var description: String? = null; private set
    override var longDescription: String? = null; private set
    override var videoWidth = 0; private set
    override var videoHeight = 0; private set
    override var criticScores: MutableList<CriticScore>? = null; private set
    override var posterArtUri: String? = null; private set
    override var thumbnailUri: String? = null; private set
    override var canonicalGenreIds: IntArray? = null; private set
    override var contentRatings: List<TvContentRating>? = null; private set
    override var isRecordingProhibited = false; private set

    override val isValid: Boolean get() = channelId >= 0
    override val durationMillis: Long get() = endTimeUtcMillis - startTimeUtcMillis

    override fun getDurationString(context: Context): String =
        durationString ?: Utils.getDurationString(context, startTimeUtcMillis, endTimeUtcMillis, true)
            .also { durationString = it }

    override val canonicalGenres: Array<String?>?
        get() = canonicalGenreIds?.let { ids -> Array(ids.size) { GenreItems.getCanonicalGenre(ids[it]) } }

    override fun hasGenre(genreId: Int): Boolean =
        genreId == GenreItems.ID_ALL_CHANNELS || canonicalGenreIds?.contains(genreId) == true

    override fun hashCode(): Int = java.util.Objects.hash(
        channelId, startTimeUtcMillis, endTimeUtcMillis, title, seriesId, episodeTitle, description,
        longDescription, videoWidth, videoHeight, posterArtUri, thumbnailUri, contentRatings,
        canonicalGenreIds.contentHashCode(), seasonNumber, seasonTitle, episodeNumber, isRecordingProhibited,
    )

    override fun equals(other: Any?): Boolean =
        other is ProgramImpl &&
            packageName == other.packageName &&
            channelId == other.channelId &&
            startTimeUtcMillis == other.startTimeUtcMillis &&
            endTimeUtcMillis == other.endTimeUtcMillis &&
            title == other.title &&
            seriesId == other.seriesId &&
            episodeTitle == other.episodeTitle &&
            description == other.description &&
            longDescription == other.longDescription &&
            videoWidth == other.videoWidth &&
            videoHeight == other.videoHeight &&
            posterArtUri == other.posterArtUri &&
            thumbnailUri == other.thumbnailUri &&
            contentRatings == other.contentRatings &&
            canonicalGenreIds.contentEquals(other.canonicalGenreIds) &&
            seasonNumber == other.seasonNumber &&
            seasonTitle == other.seasonTitle &&
            episodeNumber == other.episodeNumber &&
            isRecordingProhibited == other.isRecordingProhibited

    override fun compareTo(other: Program): Int = startTimeUtcMillis.compareTo(other.startTimeUtcMillis)

    override fun toString(): String = buildString {
        append("Program[").append(id).append("]{channelId=").append(channelId)
        append(", packageName=").append(packageName)
        append(", title=").append(title)
        append(", seriesId=").append(seriesId)
        append(", episodeTitle=").append(episodeTitle)
        append(", seasonNumber=").append(seasonNumber)
        append(", seasonTitle=").append(seasonTitle)
        append(", episodeNumber=").append(episodeNumber)
        append(", startTimeUtcSec=").append(Utils.toTimeString(startTimeUtcMillis))
        append(", endTimeUtcSec=").append(Utils.toTimeString(endTimeUtcMillis))
        append(", videoWidth=").append(videoWidth)
        append(", videoHeight=").append(videoHeight)
        append(", contentRatings=").append(TvContentRatingCache.contentRatingsToString(contentRatings))
        append(", posterArtUri=").append(posterArtUri)
        append(", thumbnailUri=").append(thumbnailUri)
        append(", canonicalGenres=").append(canonicalGenreIds?.contentToString())
        append(", recordingProhibited=").append(isRecordingProhibited)
        append("}")
    }

    fun copyFrom(other: Program) {
        if (this === other) return
        id = other.id
        packageName = other.packageName
        channelId = other.channelId
        title = other.title
        seriesId = other.seriesId
        episodeTitle = other.episodeTitle
        seasonNumber = other.seasonNumber
        seasonTitle = other.seasonTitle
        episodeNumber = other.episodeNumber
        startTimeUtcMillis = other.startTimeUtcMillis
        endTimeUtcMillis = other.endTimeUtcMillis
        durationString = null // bei Bedarf neu berechnen
        description = other.description
        longDescription = other.longDescription
        videoWidth = other.videoWidth
        videoHeight = other.videoHeight
        criticScores = other.criticScores?.toMutableList()
        posterArtUri = other.posterArtUri
        thumbnailUri = other.thumbnailUri
        canonicalGenreIds = other.canonicalGenreIds
        contentRatings = other.contentRatings
        isRecordingProhibited = other.isRecordingProhibited
    }

    override fun prefetchPosterArt(context: Context, posterArtWidth: Int, posterArtHeight: Int) {
        val uri = posterArtUri ?: return
        ImageLoader.prefetchBitmap(context, uri, posterArtWidth, posterArtHeight)
    }

    override fun loadPosterArt(
        context: Context, posterArtWidth: Int, posterArtHeight: Int, callback: ImageLoader.ImageLoaderCallback<*>,
    ): Boolean {
        val uri = posterArtUri ?: return false
        return ImageLoader.loadBitmap(context, uri, posterArtWidth, posterArtHeight, callback)
    }

    override fun toParcelable(): Parcelable = this
    override fun describeContents() = 0

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeLong(id)
        out.writeString(packageName)
        out.writeLong(channelId)
        out.writeString(title)
        out.writeString(seriesId)
        out.writeString(episodeTitle)
        out.writeString(seasonNumber)
        out.writeString(seasonTitle)
        out.writeString(episodeNumber)
        out.writeLong(startTimeUtcMillis)
        out.writeLong(endTimeUtcMillis)
        out.writeString(description)
        out.writeString(longDescription)
        out.writeInt(videoWidth)
        out.writeInt(videoHeight)
        out.writeTypedList(criticScores)
        out.writeString(posterArtUri)
        out.writeString(thumbnailUri)
        out.writeIntArray(canonicalGenreIds)
        val ratings = contentRatings
        out.writeInt(ratings?.size ?: 0)
        ratings?.forEach { out.writeString(it.flattenToString()) }
        out.writeByte(if (isRecordingProhibited) 1 else 0)
    }

    class Builder() {
        private val program = ProgramImpl().apply {
            channelId = Channel.INVALID_ID
            startTimeUtcMillis = -1
            endTimeUtcMillis = -1
        }

        constructor(other: Program) : this() { program.copyFrom(other) }

        fun setId(v: Long) = apply { program.id = v }
        fun setPackageName(v: String?) = apply { program.packageName = v }
        fun setChannelId(v: Long) = apply { program.channelId = v }
        fun setTitle(v: String?) = apply { program.title = v }
        fun setSeriesId(v: String?) = apply { program.seriesId = v }
        fun setEpisodeTitle(v: String?) = apply { program.episodeTitle = v }
        fun setSeasonNumber(v: String?) = apply { program.seasonNumber = v }
        fun setSeasonTitle(v: String?) = apply { program.seasonTitle = v }
        fun setEpisodeNumber(v: String?) = apply { program.episodeNumber = v }
        fun setStartTimeUtcMillis(v: Long) = apply { program.startTimeUtcMillis = v }
        fun setEndTimeUtcMillis(v: Long) = apply { program.endTimeUtcMillis = v }
        fun setDescription(v: String?) = apply { program.description = v }
        fun setLongDescription(v: String?) = apply { program.longDescription = v }
        fun setVideoWidth(v: Int) = apply { program.videoWidth = v }
        fun setVideoHeight(v: Int) = apply { program.videoHeight = v }
        fun setContentRatings(v: List<TvContentRating>?) = apply { program.contentRatings = v }
        fun setPosterArtUri(v: String?) = apply { program.posterArtUri = v }
        fun setThumbnailUri(v: String?) = apply { program.thumbnailUri = v }
        fun setCanonicalGenres(genres: String?) = apply { program.canonicalGenreIds = Utils.getCanonicalGenreIds(genres) }
        fun setRecordingProhibited(v: Boolean) = apply { program.isRecordingProhibited = v }

        /** Nur Bewertungen mit Wert werden übernommen. */
        fun addCriticScore(criticScore: CriticScore) = apply {
            if (criticScore.score != null) {
                (program.criticScores ?: ArrayList<CriticScore>().also { program.criticScores = it }).add(criticScore)
            }
        }

        /** Ohne Titel keine Serien-ID; mit Folgennummer ohne Serien-ID wird eine erzeugt. */
        fun build(): ProgramImpl {
            if (program.title.isNullOrEmpty()) {
                setSeriesId(null)
            } else if (program.seriesId.isNullOrEmpty() && !program.episodeNumber.isNullOrEmpty()) {
                setSeriesId(BaseProgram.generateSeriesId(program.packageName, program.title))
            }
            return ProgramImpl().also { it.copyFrom(program) }
        }
    }

    companion object {
        private const val TAG = "Program"

        private val PROJECTION_BASE = arrayOf(
            Programs._ID,
            Programs.COLUMN_PACKAGE_NAME,
            Programs.COLUMN_CHANNEL_ID,
            Programs.COLUMN_TITLE,
            Programs.COLUMN_EPISODE_TITLE,
            Programs.COLUMN_SHORT_DESCRIPTION,
            Programs.COLUMN_LONG_DESCRIPTION,
            Programs.COLUMN_POSTER_ART_URI,
            Programs.COLUMN_THUMBNAIL_URI,
            Programs.COLUMN_CANONICAL_GENRE,
            Programs.COLUMN_CONTENT_RATING,
            Programs.COLUMN_START_TIME_UTC_MILLIS,
            Programs.COLUMN_END_TIME_UTC_MILLIS,
            Programs.COLUMN_VIDEO_WIDTH,
            Programs.COLUMN_VIDEO_HEIGHT,
            Programs.COLUMN_INTERNAL_PROVIDER_DATA,
        )

        private val PROJECTION_ADDED_IN_NYC = arrayOf(
            Programs.COLUMN_SEASON_DISPLAY_NUMBER,
            Programs.COLUMN_SEASON_TITLE,
            Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
            Programs.COLUMN_RECORDING_PROHIBITED,
        )

        @JvmField val PROJECTION: Array<String> = PROJECTION_BASE + PROJECTION_ADDED_IN_NYC

        @JvmField
        val PARTIAL_PROJECTION = arrayOf(
            Programs._ID,
            Programs.COLUMN_CHANNEL_ID,
            Programs.COLUMN_TITLE,
            Programs.COLUMN_EPISODE_TITLE,
            Programs.COLUMN_CANONICAL_GENRE,
            Programs.COLUMN_START_TIME_UTC_MILLIS,
            Programs.COLUMN_END_TIME_UTC_MILLIS,
        )

        @JvmStatic
        fun getColumnIndex(column: String): Int = PROJECTION.indexOf(column)

        /** Reihenfolge muss zu PROJECTION (+ optional series_id) passen. */
        @JvmStatic
        fun fromCursor(cursor: Cursor): Program {
            val b = Builder()
            var i = 0
            b.setId(cursor.getLong(i++))
            b.setPackageName(cursor.getString(i++))
            b.setChannelId(cursor.getLong(i++))
            b.setTitle(cursor.getString(i++))
            b.setEpisodeTitle(cursor.getString(i++))
            b.setDescription(cursor.getString(i++))
            b.setLongDescription(cursor.getString(i++))
            b.setPosterArtUri(cursor.getString(i++))
            b.setThumbnailUri(cursor.getString(i++))
            b.setCanonicalGenres(cursor.getString(i++))
            b.setContentRatings(TvContentRatingCache.getRatings(cursor.getString(i++)))
            b.setStartTimeUtcMillis(cursor.getLong(i++))
            b.setEndTimeUtcMillis(cursor.getLong(i++))
            b.setVideoWidth(cursor.getLong(i++).toInt())
            b.setVideoHeight(cursor.getLong(i++).toInt())
            i++ // COLUMN_INTERNAL_PROVIDER_DATA: nur für den eingebauten Tuner
            b.setSeasonNumber(cursor.getString(i++))
            b.setSeasonTitle(cursor.getString(i++))
            b.setEpisodeNumber(cursor.getString(i++))
            b.setRecordingProhibited(cursor.getInt(i++) == 1)
            if (TvProviderUtils.getProgramHasSeriesIdColumn()) {
                val seriesId = cursor.getString(i)
                if (!seriesId.isNullOrEmpty()) b.setSeriesId(seriesId)
            }
            return b.build()
        }

        @JvmStatic
        fun fromCursorPartialProjection(cursor: Cursor): Program {
            var i = 0
            return Builder()
                .setId(cursor.getLong(i++))
                .setChannelId(cursor.getLong(i++))
                .setTitle(cursor.getString(i++))
                .setEpisodeTitle(cursor.getString(i++))
                .setCanonicalGenres(cursor.getString(i++))
                .setStartTimeUtcMillis(cursor.getLong(i++))
                .setEndTimeUtcMillis(cursor.getLong(i))
                .build()
        }

        @JvmStatic
        fun fromParcel(p: Parcel): ProgramImpl = ProgramImpl().apply {
            id = p.readLong()
            packageName = p.readString()
            channelId = p.readLong()
            title = p.readString()
            seriesId = p.readString()
            episodeTitle = p.readString()
            seasonNumber = p.readString()
            seasonTitle = p.readString()
            episodeNumber = p.readString()
            startTimeUtcMillis = p.readLong()
            endTimeUtcMillis = p.readLong()
            description = p.readString()
            longDescription = p.readString()
            videoWidth = p.readInt()
            videoHeight = p.readInt()
            criticScores = p.createTypedArrayList(CriticScore.CREATOR)
            posterArtUri = p.readString()
            thumbnailUri = p.readString()
            canonicalGenreIds = p.createIntArray()
            val length = p.readInt()
            contentRatings = List(length) { TvContentRating.unflattenFromString(p.readString()) }
            isRecordingProhibited = p.readByte() != 0.toByte()
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Program> {
            override fun createFromParcel(p: Parcel): Program = fromParcel(p)
            override fun newArray(size: Int) = arrayOfNulls<Program>(size)
        }

        @JvmStatic
        @WorkerThread
        fun toContentValues(program: Program, context: Context): ContentValues = ContentValues().apply {
            put(Programs.COLUMN_CHANNEL_ID, program.channelId)
            if (!program.packageName.isNullOrEmpty()) put(Programs.COLUMN_PACKAGE_NAME, program.packageName)
            putValue(Programs.COLUMN_TITLE, program.title)
            putValue(Programs.COLUMN_EPISODE_TITLE, program.episodeTitle)
            putValue(Programs.COLUMN_SEASON_DISPLAY_NUMBER, program.seasonNumber)
            putValue(Programs.COLUMN_EPISODE_DISPLAY_NUMBER, program.episodeNumber)
            if (TvProviderUtils.checkSeriesIdColumn(context, Programs.CONTENT_URI)) {
                putValue(BaseProgram.COLUMN_SERIES_ID, program.seriesId)
            }
            putValue(Programs.COLUMN_SHORT_DESCRIPTION, program.description)
            putValue(Programs.COLUMN_LONG_DESCRIPTION, program.longDescription)
            putValue(Programs.COLUMN_POSTER_ART_URI, program.posterArtUri)
            putValue(Programs.COLUMN_THUMBNAIL_URI, program.thumbnailUri)
            val genres = program.canonicalGenres
            putValue(
                Programs.COLUMN_CANONICAL_GENRE,
                if (!genres.isNullOrEmpty()) Programs.Genres.encode(*genres.map { it.orEmpty() }.toTypedArray()) else "",
            )
            putValue(Programs.COLUMN_CONTENT_RATING, TvContentRatingCache.contentRatingsToString(program.contentRatings))
            put(Programs.COLUMN_START_TIME_UTC_MILLIS, program.startTimeUtcMillis)
            put(Programs.COLUMN_END_TIME_UTC_MILLIS, program.endTimeUtcMillis)
            putNull(Programs.COLUMN_INTERNAL_PROVIDER_DATA) // nur eingebauter Tuner
        }

        private fun ContentValues.putValue(key: String, value: String?) {
            if (value.isNullOrEmpty()) putNull(key) else put(key, value)
        }
    }
}
