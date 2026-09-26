package com.android.tv.util

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.tv.TvContract
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.Programs.Genres
import android.media.tv.TvInputInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import androidx.annotation.WorkerThread
import androidx.preference.PreferenceManager
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.util.Clock
import com.android.tv.data.GenreItems
import com.android.tv.data.ProgramImpl
import com.android.tv.data.StreamInfo
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Port von com.android.tv.util.Utils. */
object Utils {
    private const val TAG = "Utils"
    private const val DEBUG = false

    const val EXTRA_KEY_ACTION = "action"
    const val EXTRA_ACTION_SHOW_TV_INPUT = "show_tv_input"
    const val EXTRA_KEY_FROM_LAUNCHER = "from_launcher"
    const val EXTRA_KEY_RECORDED_PROGRAM_ID = "recorded_program_id"
    const val EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME = "recorded_program_seek_time"
    const val EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED = "recorded_program_pin_checked"

    private const val PATH_CHANNEL = "channel"
    private const val PATH_PROGRAM = "program"
    private const val PATH_RECORDED_PROGRAM = "recorded_program"
    private const val PREF_KEY_LAST_WATCHED_CHANNEL_ID = "last_watched_channel_id"
    private const val PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT = "last_watched_channel_id_for_input_"
    private const val PREF_KEY_LAST_WATCHED_CHANNEL_URI = "last_watched_channel_uri"
    private const val PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID = "last_watched_tuner_input_id"
    private const val PREF_KEY_RECORDING_FAILED_REASONS = "recording_failed_reasons"
    private const val PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET = "failed_scheduled_recording_info_set"

    private const val VIDEO_SD_WIDTH = 704
    private const val VIDEO_SD_HEIGHT = 480
    private const val VIDEO_HD_WIDTH = 1280
    private const val VIDEO_HD_HEIGHT = 720
    private const val VIDEO_FULL_HD_WIDTH = 1920
    private const val VIDEO_FULL_HD_HEIGHT = 1080
    private const val VIDEO_ULTRA_HD_WIDTH = 2048
    private const val VIDEO_ULTRA_HD_HEIGHT = 1536

    private const val RECORDING_FAILED_REASON_NONE = 0L
    private val HALF_MINUTE_MS = TimeUnit.SECONDS.toMillis(30)
    private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)

    private enum class AspectRatio(val width: Int, val height: Int) {
        ASPECT_RATIO_4_3(4, 3),
        ASPECT_RATIO_16_9(16, 9),
        ASPECT_RATIO_21_9(21, 9);

        override fun toString() = "$width:$height"
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    @JvmStatic
    fun buildSelectionForIds(idName: String, ids: List<Long>): String =
        ids.joinToString(",", prefix = "$idName in (", postfix = ")")

    @JvmStatic
    @WorkerThread
    fun getInputIdForChannel(context: Context, channelId: Long): String? {
        if (channelId == Channel.INVALID_ID) return null
        try {
            context.contentResolver.query(
                TvContract.buildChannelUri(channelId), arrayOf(Channels.COLUMN_INPUT_ID), null, null, null,
            )?.use { if (it.moveToNext()) return intern(it.getString(0)) }
        } catch (e: Exception) {
            Log.e(TAG, "Error get input id for channel", e)
        }
        return null
    }

    @JvmStatic
    fun setLastWatchedChannel(context: Context, channel: Channel?) {
        if (channel == null) {
            Log.e(TAG, "setLastWatchedChannel: channel cannot be null")
            return
        }
        prefs(context).edit().putString(PREF_KEY_LAST_WATCHED_CHANNEL_URI, channel.uri.toString()).apply()
        if (!channel.isPassthrough) {
            val channelId = channel.id
            require(channelId >= 0) { "channelId should be equal to or larger than 0" }
            prefs(context).edit()
                .putLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, channelId)
                .putLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT + channel.inputId, channelId)
                .putString(PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID, channel.inputId)
                .apply()
        }
    }

    @JvmStatic
    fun setRecordingFailedReason(context: Context, reason: Int) {
        val reasons = getRecordingFailedReasons(context) or (1L shl reason)
        prefs(context).edit().putLong(PREF_KEY_RECORDING_FAILED_REASONS, reasons).apply()
    }

    @JvmStatic
    fun addFailedScheduledRecordingInfo(context: Context, scheduledRecordingInfo: String) {
        // Kopie: das von getStringSet() gelieferte Set darf nicht verändert werden (Bug im Original).
        val set = HashSet(getFailedScheduledRecordingInfoSet(context))
        set.add(scheduledRecordingInfo)
        prefs(context).edit().putStringSet(PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET, set).apply()
    }

    @JvmStatic
    fun clearFailedScheduledRecordingInfoSet(context: Context) {
        prefs(context).edit().remove(PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET).apply()
    }

    @JvmStatic
    fun clearRecordingFailedReason(context: Context, reason: Int) {
        val reasons = getRecordingFailedReasons(context) and (1L shl reason).inv()
        prefs(context).edit().putLong(PREF_KEY_RECORDING_FAILED_REASONS, reasons).apply()
    }

    @JvmStatic
    fun getLastWatchedChannelId(context: Context): Long =
        prefs(context).getLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, Channel.INVALID_ID)

    @JvmStatic
    fun getLastWatchedChannelIdForInput(context: Context, inputId: String): Long =
        prefs(context).getLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT + inputId, Channel.INVALID_ID)

    @JvmStatic
    fun getLastWatchedChannelUri(context: Context): String? =
        prefs(context).getString(PREF_KEY_LAST_WATCHED_CHANNEL_URI, null)

    @JvmStatic
    fun getLastWatchedTunerInputId(context: Context): String? =
        prefs(context).getString(PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID, null)

    private fun getRecordingFailedReasons(context: Context): Long =
        prefs(context).getLong(PREF_KEY_RECORDING_FAILED_REASONS, RECORDING_FAILED_REASON_NONE)

    @JvmStatic
    fun getFailedScheduledRecordingInfoSet(context: Context): Set<String> =
        prefs(context).getStringSet(PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET, emptySet()) ?: emptySet()

    @JvmStatic
    fun hasRecordingFailedReason(context: Context, reason: Int): Boolean =
        getRecordingFailedReasons(context) and (1L shl reason) != 0L

    @JvmStatic
    fun isChannelUriForInput(uri: Uri?): Boolean =
        // Bugfix: URIs ohne Pfad führten zu IndexOutOfBounds
        isTvUri(uri) && PATH_CHANNEL == uri!!.pathSegments.firstOrNull() && !uri.getQueryParameter("input").isNullOrEmpty()

    @JvmStatic
    fun isChannelUriForOneChannel(uri: Uri?): Boolean =
        isChannelUriForTunerInput(uri) || (uri != null && TvContract.isChannelUriForPassthroughInput(uri))

    @JvmStatic
    fun isChannelUriForTunerInput(uri: Uri?): Boolean =
        isTvUri(uri) && isTwoSegmentUriStartingWith(uri!!, PATH_CHANNEL)

    private fun isTvUri(uri: Uri?): Boolean =
        uri != null && ContentResolver.SCHEME_CONTENT == uri.scheme && TvContract.AUTHORITY == uri.authority

    private fun isTwoSegmentUriStartingWith(uri: Uri, pathSegment: String): Boolean {
        val segments = uri.pathSegments
        return segments.size == 2 && pathSegment == segments[0]
    }

    @JvmStatic
    fun isProgramsUri(uri: Uri?): Boolean = isTvUri(uri) && PATH_PROGRAM == uri!!.pathSegments.firstOrNull()

    @JvmStatic
    fun isRecordedProgramsUri(uri: Uri?): Boolean = isTvUri(uri) && PATH_RECORDED_PROGRAM == uri!!.pathSegments.firstOrNull()

    /** Sendung eines Kanals zu einem Zeitpunkt (DB-Zugriff, nicht auf dem Main-Thread aufrufen). */
    @JvmStatic
    @WorkerThread
    fun getProgramAt(context: Context, channelId: Long, timeMs: Long): Program? {
        if (channelId == Channel.INVALID_ID) {
            Log.e(TAG, "getCurrentProgramAt - channelId is invalid")
            return null
        }
        if (context.mainLooper.thread == Thread.currentThread()) {
            Log.w(TAG, "getCurrentProgramAt called on main thread")
        }
        val uri = TvContract.buildProgramsUriForChannel(TvContract.buildChannelUri(channelId), timeMs, timeMs)
        var projection = ProgramImpl.PROJECTION
        if (TvProviderUtils.checkSeriesIdColumn(context, TvContract.Programs.CONTENT_URI) && isProgramsUri(uri)) {
            projection = TvProviderUtils.addExtraColumnsToProjection(
                projection, TvProviderUtils.EXTRA_PROGRAM_COLUMN_SERIES_ID)
        }
        context.contentResolver.query(uri, projection, null, null, null)?.use {
            if (it.moveToNext()) return ProgramImpl.fromCursor(it)
        }
        return null
    }

    @JvmStatic
    @WorkerThread
    fun getCurrentProgram(context: Context, channelId: Long): Program? =
        getProgramAt(context, channelId, System.currentTimeMillis())

    /** Auf volle Minuten gerundet (+30 s). */
    @JvmStatic
    fun getRoundOffMinsFromMs(millis: Long): Int = TimeUnit.MILLISECONDS.toMinutes(millis + HALF_MINUTE_MS).toInt()

    @JvmStatic
    fun getDurationString(context: Context, startUtcMillis: Long, endUtcMillis: Long, useShortFormat: Boolean): String =
        getDurationString(context, TvSingletons.getSingletons(context).getClock(), startUtcMillis, endUtcMillis, useShortFormat)

    @JvmStatic
    fun getDurationString(
        context: Context, clock: Clock, startUtcMillis: Long, endUtcMillis: Long, useShortFormat: Boolean,
    ): String = getDurationString(context, clock.currentTimeMillis(), startUtcMillis, endUtcMillis, useShortFormat, 0)

    @JvmStatic
    fun getDurationString(
        context: Context, baseMillis: Long, startUtcMillis: Long, endUtcMillis: Long,
        useShortFormat: Boolean, flags: Int,
    ): String = getDurationString(
        context, startUtcMillis, endUtcMillis, useShortFormat,
        !isInGivenDay(baseMillis, startUtcMillis), true, flags)

    /** Formatiert Start–Ende. 0-Uhr-Sonderfälle wie im Original (b/28740989). */
    @JvmStatic
    fun getDurationString(
        context: Context, startUtcMillis: Long, endUtcMillis: Long, useShortFormat: Boolean,
        showDate: Boolean, showTime: Boolean, flags: Int,
    ): String {
        var f = flags or DateUtils.FORMAT_ABBREV_MONTH or (if (useShortFormat) DateUtils.FORMAT_NUMERIC_DATE else 0)
        if (!(showTime || showDate)) Log.w(TAG, "getDurationString: showTime or showDate must be set")
        if (showTime) f = f or DateUtils.FORMAT_SHOW_TIME
        if (showDate) f = f or DateUtils.FORMAT_SHOW_DATE
        if (!showDate || f and DateUtils.FORMAT_SHOW_YEAR == 0) {
            // Jahr nur bei explizitem FORMAT_SHOW_YEAR
            f = f or DateUtils.FORMAT_NO_YEAR
        }
        if (startUtcMillis != endUtcMillis && useShortFormat) {
            // 0:00 als Start = Tagesbeginn, als Ende = Tagesende
            if (!isInGivenDay(startUtcMillis, endUtcMillis - 1) &&
                endUtcMillis - startUtcMillis < TimeUnit.HOURS.toMillis(11)
            ) {
                // Kurzformat ohne Datum: einen Tag abziehen, sonst zeigt DateUtils das Datum an.
                return DateUtils.formatDateRange(context, startUtcMillis, endUtcMillis - ONE_DAY_MS, f)
            }
        }
        // +1 ms gegen DateUtils-Bug bei 0:00–0:00
        val dateRange = DateUtils.formatDateRange(context, startUtcMillis, endUtcMillis, f)
        return if (startUtcMillis == endUtcMillis || dateRange.contains("–")) dateRange
        else DateUtils.formatDateRange(context, startUtcMillis, endUtcMillis + 1, f)
    }

    @JvmStatic
    fun isInGivenDay(dayToMatchInMillis: Long, subjectTimeInMillis: Long): Boolean {
        val timeZone = Calendar.getInstance().timeZone
        var offset = timeZone.rawOffset.toLong()
        if (timeZone.inDaylightTime(Date(dayToMatchInMillis))) offset += timeZone.dstSavings
        return floorTime(dayToMatchInMillis + offset, ONE_DAY_MS) == floorTime(subjectTimeInMillis + offset, ONE_DAY_MS)
    }

    @JvmStatic
    fun computeDateDifference(startTimeMs: Long, endTimeMs: Long): Int {
        val from = Calendar.getInstance().apply { time = Date(startTimeMs); resetCalendar(this) }
        val to = Calendar.getInstance().apply { time = Date(endTimeMs); resetCalendar(this) }
        return ((to.timeInMillis - from.timeInMillis) / ONE_DAY_MS).toInt()
    }

    private fun resetCalendar(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    @JvmStatic
    fun getLastMillisecondOfDay(millis: Long): Long = Calendar.getInstance().run {
        time = Date(millis)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
        timeInMillis
    }

    @JvmStatic
    fun getFirstMillisecondOfDay(millis: Long): Long =
        Calendar.getInstance().run { time = Date(millis); resetCalendar(this); timeInMillis }

    @JvmStatic
    fun getAspectRatioString(width: Int, height: Int): String {
        if (width == 0 || height == 0) return ""
        return AspectRatio.values().firstOrNull {
            abs(it.height.toFloat() / it.width - height.toFloat() / width) < 0.05f
        }?.toString() ?: ""
    }

    @JvmStatic
    fun getAspectRatioString(videoDisplayAspectRatio: Float): String {
        if (videoDisplayAspectRatio <= 0) return ""
        return AspectRatio.values().firstOrNull {
            abs(it.width.toFloat() / it.height - videoDisplayAspectRatio) < 0.05f
        }?.toString() ?: ""
    }

    @JvmStatic
    fun getVideoDefinitionLevelFromSize(width: Int, height: Int): Int = when {
        width >= VIDEO_ULTRA_HD_WIDTH && height >= VIDEO_ULTRA_HD_HEIGHT -> StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD
        width >= VIDEO_FULL_HD_WIDTH && height >= VIDEO_FULL_HD_HEIGHT -> StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD
        width >= VIDEO_HD_WIDTH && height >= VIDEO_HD_HEIGHT -> StreamInfo.VIDEO_DEFINITION_LEVEL_HD
        width >= VIDEO_SD_WIDTH && height >= VIDEO_SD_HEIGHT -> StreamInfo.VIDEO_DEFINITION_LEVEL_SD
        else -> StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN
    }

    @JvmStatic
    fun getVideoDefinitionLevelString(context: Context, videoFormat: Int): String = when (videoFormat) {
        StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD -> context.getString(R.string.video_definition_level_ultra_hd)
        StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD -> context.getString(R.string.video_definition_level_full_hd)
        StreamInfo.VIDEO_DEFINITION_LEVEL_HD -> context.getString(R.string.video_definition_level_hd)
        StreamInfo.VIDEO_DEFINITION_LEVEL_SD -> context.getString(R.string.video_definition_level_sd)
        else -> ""
    }

    @JvmStatic
    fun getAudioChannelString(context: Context, channelCount: Int): String = when (channelCount) {
        1 -> context.getString(R.string.audio_channel_mono)
        2 -> context.getString(R.string.audio_channel_stereo)
        6 -> context.getString(R.string.audio_channel_5_1)
        8 -> context.getString(R.string.audio_channel_7_1)
        else -> ""
    }

    @JvmStatic
    fun isEqualLanguage(lang1: String?, lang2: String?): Boolean {
        if (lang1 == null) return lang2 == null
        if (lang2 == null) return false
        return try {
            Locale(lang1).isO3Language == Locale(lang2).isO3Language
        } catch (ignored: Exception) {
            false
        }
    }

    @JvmStatic
    fun isIntentAvailable(context: Context, intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()

    /** Eigenes Label des Inputs, sonst Standard-Label. */
    @JvmStatic
    fun loadLabel(context: Context, input: TvInputInfo?): String? {
        if (input == null) return null
        val inputManager = TvSingletons.getSingletons(context).getTvInputManagerHelper()
        val custom = inputManager.loadCustomLabel(input)
        return if (!custom.isNullOrEmpty()) custom else inputManager.loadLabel(input)
    }

    @JvmStatic
    @WorkerThread
    fun enableAllChannels(context: Context) {
        val values = ContentValues().apply { put(Channels.COLUMN_BROWSABLE, 1) }
        context.contentResolver.update(Channels.CONTENT_URI, values, null, null)
    }

    @JvmStatic
    @JvmOverloads
    fun toTimeString(timeMillis: Long, fullFormat: Boolean = true): String =
        if (fullFormat) Date(timeMillis).toString()
        else DateUtils.formatSameDayTime(timeMillis, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()

    @JvmStatic
    fun toRectString(view: View): String =
        "{l=${view.left},r=${view.right},t=${view.top},b=${view.bottom},w=${view.width},h=${view.height}}"

    @JvmStatic
    fun floorTime(timeMs: Long, timeUnit: Long): Long = timeMs - timeMs % timeUnit

    @JvmStatic
    fun ceilTime(timeMs: Long, timeUnit: Long): Long = timeMs + timeUnit - timeMs % timeUnit

    @JvmStatic
    fun intern(string: String?): String? = string?.intern()

    @JvmStatic
    fun isIndexValid(collection: Collection<*>?, index: Int): Boolean =
        collection != null && index >= 0 && index < collection.size

    @JvmStatic
    fun getTextForLocale(context: Context, locale: Locale, resourceId: Int): CharSequence {
        val current = context.resources.configuration.locales[0]
        if (locale == current) return context.getText(resourceId)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).getText(resourceId)
    }

    @JvmStatic
    fun isInternalTvInput(context: Context, inputId: String): Boolean {
        val component = ComponentName.unflattenFromString(inputId) ?: return false
        return context.packageName == component.packageName
    }

    @JvmStatic
    fun getTvInputInfoForProgram(context: Context, program: Program?): TvInputInfo? {
        if (!Program.isProgramValid(program)) return null
        return getTvInputInfoForChannelId(context, program!!.channelId)
    }

    @JvmStatic
    fun getTvInputInfoForChannelId(context: Context, channelId: Long): TvInputInfo? {
        val singletons = TvSingletons.getSingletons(context)
        val channel = singletons.getChannelDataManager().getChannel(channelId) ?: return null
        return singletons.getTvInputManagerHelper().getTvInputInfo(channel.inputId)
    }

    @JvmStatic
    fun getTvInputInfoForInputId(context: Context, inputId: String): TvInputInfo? =
        TvSingletons.getSingletons(context).getTvInputManagerHelper().getTvInputInfo(inputId)

    @JvmStatic
    fun getCanonicalGenreIds(genres: String?): IntArray? {
        if (genres.isNullOrEmpty()) return null
        return getCanonicalGenreIds(Genres.decode(genres))
    }

    /** Unbekannte Genres werden übersprungen. */
    @JvmStatic
    fun getCanonicalGenreIds(canonicalGenres: Array<String>?): IntArray? {
        if (canonicalGenres.isNullOrEmpty()) return null
        return canonicalGenres.map { GenreItems.getId(it) }
            .filter { it != GenreItems.ID_ALL_CHANNELS }
            .toIntArray()
    }

    @JvmStatic
    fun getCanonicalGenre(canonicalGenreIds: IntArray?): String? {
        if (canonicalGenreIds == null || canonicalGenreIds.isEmpty()) return null
        return Genres.encode(*Array(canonicalGenreIds.size) { GenreItems.getCanonicalGenre(canonicalGenreIds[it]).orEmpty() })
    }

    /** Führt [runnable] auf dem Main-Thread aus und wartet auf das Ende. */
    @JvmStatic
    fun runInMainThreadAndWait(runnable: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
            return
        }
        val task = FutureTask(runnable, null)
        mainHandler.post(task)
        try {
            task.get()
        } catch (e: InterruptedException) {
            Log.e(TAG, "failed to finish the execution", e)
        } catch (e: ExecutionException) {
            Log.e(TAG, "failed to finish the execution", e)
        }
    }
}
