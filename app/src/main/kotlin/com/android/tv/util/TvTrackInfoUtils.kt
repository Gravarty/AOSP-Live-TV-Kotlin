package com.android.tv.util

import android.content.Context
import android.media.tv.TvTrackInfo
import android.os.LocaleList
import android.text.TextUtils
import android.util.Log
import com.android.tv.R
import java.util.Locale

/** Statische Hilfen für [TvTrackInfo]. */
object TvTrackInfoUtils {
    private const val TAG = "TvTrackInfoUtils"
    private const val AUDIO_CHANNEL_NONE = 0
    private const val AUDIO_CHANNEL_MONO = 1
    private const val AUDIO_CHANNEL_STEREO = 2
    private const val AUDIO_CHANNEL_SURROUND_6 = 6
    private const val AUDIO_CHANNEL_SURROUND_8 = 8

    /**
     * Vergleicht, wie gut zwei Spuren zu [languages], [channelCount] und [id] passen (in dieser
     * Rangfolge). Sortiert steht die schlechteste Übereinstimmung vorne.
     * Ergebnis: -1 lhs schlechter, 0 gleich gut, 1 lhs besser.
     */
    @JvmStatic
    fun createComparator(id: String?, languages: List<String?>, channelCount: Int): Comparator<TvTrackInfo?> =
        Comparator { lhs, rhs ->
            if (lhs == rhs) return@Comparator 0
            if (lhs == null) return@Comparator -1
            if (rhs == null) return@Comparator 1
            // Erste passende Sprache suchen; frühere Treffer sind besser, kein Treffer = Listenende
            var lhsLangIndex = languages.indexOfFirst { Utils.isEqualLanguage(lhs.language, it) }
            if (lhsLangIndex == -1) lhsLangIndex = languages.size
            var rhsLangIndex = languages.indexOfFirst { Utils.isEqualLanguage(rhs.language, it) }
            if (rhsLangIndex == -1) rhsLangIndex = languages.size
            if (lhsLangIndex != rhsLangIndex) {
                // Kleinerer Index gewinnt
                return@Comparator rhsLangIndex.compareTo(lhsLangIndex)
            }
            val lhsCountMatch = lhs.type != TvTrackInfo.TYPE_AUDIO || lhs.audioChannelCount == channelCount
            val rhsCountMatch = rhs.type != TvTrackInfo.TYPE_AUDIO || rhs.audioChannelCount == channelCount
            when {
                lhsCountMatch && rhsCountMatch -> (lhs.id == id).compareTo(rhs.id == id)
                lhsCountMatch || rhsCountMatch -> lhsCountMatch.compareTo(rhsCountMatch)
                else -> lhs.audioChannelCount.compareTo(rhs.audioChannelCount)
            }
        }

    /**
     * Liefert die am besten passende Spur (bzw. null, wenn [tracks] null ist).
     * Ohne [language] zählen die System-Sprachen in ihrer Rangfolge.
     */
    @JvmStatic
    fun getBestTrackInfo(tracks: List<TvTrackInfo>?, id: String?, language: String?, channelCount: Int): TvTrackInfo? {
        if (tracks == null) return null
        val languages = ArrayList<String?>()
        if (language == null) {
            // minSdk 30: LocaleList ist immer verfügbar, der Zweig für ältere Versionen entfällt
            val locales = LocaleList.getDefault()
            for (i in 0 until locales.size()) languages.add(locales.get(i).language)
        } else {
            languages.add(language)
        }
        val comparator = createComparator(id, languages, channelCount)
        var best: TvTrackInfo? = null
        for (track in tracks) {
            if (comparator.compare(track, best) > 0) best = track
        }
        return best
    }

    @JvmStatic
    fun needToShowSampleRate(context: Context, tracks: List<TvTrackInfo>): Boolean {
        val multiAudioStrings = HashSet<String>()
        for (track in tracks) {
            val multiAudioString = getMultiAudioString(context, track, false)
            if (!multiAudioStrings.add(multiAudioString)) return true
        }
        return false
    }

    @JvmStatic
    fun getMultiAudioString(context: Context, track: TvTrackInfo, showSampleRate: Boolean): String {
        require(track.type == TvTrackInfo.TYPE_AUDIO) { "Not an audio track: " + toString(track) }
        var language = context.getString(R.string.multi_audio_unknown_language)
        val trackLanguage = track.language
        if (!TextUtils.isEmpty(trackLanguage)) {
            language = Locale(trackLanguage!!).displayName
        } else {
            Log.d(TAG, "No language information found for the audio track: " + toString(track))
        }

        val metadata = StringBuilder()
        when (track.audioChannelCount) {
            AUDIO_CHANNEL_NONE -> {}
            AUDIO_CHANNEL_MONO -> metadata.append(context.getString(R.string.multi_audio_channel_mono))
            AUDIO_CHANNEL_STEREO -> metadata.append(context.getString(R.string.multi_audio_channel_stereo))
            AUDIO_CHANNEL_SURROUND_6 -> metadata.append(context.getString(R.string.multi_audio_channel_surround_6))
            AUDIO_CHANNEL_SURROUND_8 -> metadata.append(context.getString(R.string.multi_audio_channel_surround_8))
            else -> if (track.audioChannelCount > 0) {
                metadata.append(context.getString(R.string.multi_audio_channel_suffix, track.audioChannelCount))
            } else {
                Log.d(TAG, "Invalid audio channel count (${track.audioChannelCount}) found for the audio track: " + toString(track))
            }
        }
        if (showSampleRate) {
            val sampleRate = track.audioSampleRate
            if (sampleRate > 0) {
                if (metadata.isNotEmpty()) metadata.append(", ")
                val integerPart = sampleRate / 1000
                val tenths = (sampleRate % 1000) / 100
                metadata.append(integerPart)
                if (tenths != 0) {
                    metadata.append(".")
                    metadata.append(tenths)
                }
                metadata.append("kHz")
            }
        }

        if (metadata.isEmpty()) return language
        return context.getString(R.string.multi_audio_display_string_with_channel, language, metadata.toString())
    }

    private fun trackTypeToString(trackType: Int): String = when (trackType) {
        TvTrackInfo.TYPE_AUDIO -> "Audio"
        TvTrackInfo.TYPE_VIDEO -> "Video"
        TvTrackInfo.TYPE_SUBTITLE -> "Subtitle"
        else -> "Invalid Type"
    }

    @JvmStatic
    fun toString(info: TvTrackInfo): String {
        val trackType = info.type
        return "TvTrackInfo{" +
            "type=" + trackTypeToString(trackType) +
            ", id=" + info.id +
            ", language=" + info.language +
            ", description=" + info.description +
            (if (trackType == TvTrackInfo.TYPE_AUDIO) {
                ", audioChannelCount=" + info.audioChannelCount + ", audioSampleRate=" + info.audioSampleRate
            } else "") +
            (if (trackType == TvTrackInfo.TYPE_VIDEO) {
                ", videoWidth=" + info.videoWidth +
                    ", videoHeight=" + info.videoHeight +
                    ", videoFrameRate=" + info.videoFrameRate +
                    ", videoPixelAspectRatio=" + info.videoPixelAspectRatio
            } else "") +
            "}"
    }
}
