package com.android.tv.data.api

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.UiThread
import com.android.tv.util.images.ImageLoader
import java.io.Serializable

/** Eine Sendung aus dem TvProvider. */
interface Program : BaseProgram, Comparable<Program> {
    val packageName: String?
    val seasonTitle: String?
    fun getDurationString(context: Context): String
    val videoWidth: Int
    val videoHeight: Int
    val criticScores: List<CriticScore>?
    val isRecordingProhibited: Boolean
    val canonicalGenres: Array<String?>?
    fun hasGenre(genreId: Int): Boolean
    fun prefetchPosterArt(context: Context, posterArtWidth: Int, posterArtHeight: Int)

    @UiThread
    fun loadPosterArt(
        context: Context, posterArtWidth: Int, posterArtHeight: Int, callback: ImageLoader.ImageLoaderCallback<*>,
    ): Boolean

    fun toParcelable(): Parcelable

    class CriticScore(
        @JvmField val source: String?,
        @JvmField val score: String?,
        @JvmField val logoUrl: String?,
    ) : Serializable, Parcelable {
        override fun describeContents() = 0
        override fun writeToParcel(out: Parcel, flags: Int) {
            out.writeString(source)
            out.writeString(score)
            out.writeString(logoUrl)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<CriticScore> {
                override fun createFromParcel(p: Parcel) = CriticScore(p.readString(), p.readString(), p.readString())
                override fun newArray(size: Int) = arrayOfNulls<CriticScore>(size)
            }
        }
    }

    companion object {
        @JvmStatic
        fun isProgramValid(program: Program?): Boolean = program != null && program.isValid

        @JvmStatic
        fun isDuplicate(p1: Program?, p2: Program?): Boolean =
            p1 != null && p2 != null &&
                p1.channelId == p2.channelId &&
                p1.startTimeUtcMillis == p2.startTimeUtcMillis &&
                p1.endTimeUtcMillis == p2.endTimeUtcMillis

        @JvmStatic
        fun isOverlapping(p1: Program?, p2: Program?): Boolean =
            p1 != null && p2 != null &&
                p1.startTimeUtcMillis < p2.endTimeUtcMillis &&
                p1.endTimeUtcMillis > p2.startTimeUtcMillis

        @JvmStatic
        fun sameChannel(p1: Program?, p2: Program?): Boolean =
            p1 != null && p2 != null && p1.channelId == p2.channelId
    }
}
