package com.android.tv.search

import android.content.Context
import android.content.Intent
import android.media.tv.TvContentRating
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.MainThread
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.search.LocalSearchProvider.SearchResult
import com.android.tv.util.Utils
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/**
 * Suche in ChannelDataManager/ProgramDataManager (Reihenfolge: Nummer, Name/Beschreibung,
 * Sendungstitel, Sendungsbeschreibung). Läuft auf dem Main-Thread, Aufrufer wartet.
 */
class DataManagerSearch(private val context: Context) : SearchInterface {
    private val tvInputManager = context.getSystemService(TvInputManager::class.java)
    private val singletons = TvSingletons.getSingletons(context)
    private val channelDataManager = singletons.getChannelDataManager()
    private val programDataManager = singletons.getProgramDataManager()

    override fun search(query: String, limit: Int, action: Int): List<SearchResult> {
        val task = FutureTask { searchFromDataManagers(query, limit, action) }
        android.os.Handler(android.os.Looper.getMainLooper()).post(task)
        return try {
            task.get()
        } catch (e: InterruptedException) {
            Thread.interrupted()
            emptyList()
        } catch (e: ExecutionException) {
            Log.w(TAG, "Error searching for $query", e)
            emptyList()
        }
    }

    @MainThread
    private fun searchFromDataManagers(rawQuery: String, limit: Int, action: Int): List<SearchResult> {
        val results = ArrayList<SearchResult>()
        if (!channelDataManager.isDbLoadFinished) return results
        // Kanal-/Input-Wechsel per Suche unterstützt das Original nicht
        if (action == SearchInterface.ACTION_TYPE_SWITCH_CHANNEL || action == SearchInterface.ACTION_TYPE_SWITCH_INPUT) return results
        val channelsFound = HashSet<Long>()
        val channelList = channelDataManager.getBrowsableChannelList()
        val query = rawQuery.lowercase()

        fun scan(matches: (Channel) -> Pair<Boolean, Program?>?): Boolean {
            for (channel in channelList) {
                if (channel.id in channelsFound) continue
                val m = matches(channel) ?: continue
                if (m.first) addResult(results, channelsFound, channel, m.second)
                if (results.size >= limit) return true
            }
            return false
        }

        if (TextUtils.isDigitsOnly(query) && scan { (contains(it.displayNumber, query)) to null }) return results
        if (scan { (contains(it.displayName, query) || contains(it.description, query)) to null }) return results
        if (scan { ch ->
                programDataManager.getCurrentProgram(ch.id)?.let { p -> (contains(p.title, query) && !isRatingBlocked(p.contentRatings)) to p }
            }) return results
        scan { ch ->
            programDataManager.getCurrentProgram(ch.id)?.let { p -> (contains(p.description, query) && !isRatingBlocked(p.contentRatings)) to p }
        }
        return results
    }

    private fun contains(string: String?, query: String) = string != null && string.lowercase().contains(query)

    private fun addResult(results: MutableList<SearchResult>, channelsFound: MutableSet<Long>, channel: Channel, programIn: Program?) {
        var program = programIn
        if (program == null) {
            program = programDataManager.getCurrentProgram(channel.id)?.takeIf { !isRatingBlocked(it.contentRatings) }
        }
        val channelId = channel.id
        val result = if (program == null) {
            SearchResult(
                channelId = channelId, channelNumber = channel.displayNumber, title = channel.displayName,
                description = channel.description, imageUri = TvContract.buildChannelLogoUri(channelId).toString(),
                intentAction = Intent.ACTION_VIEW, intentData = buildIntentData(channelId),
                contentType = TvContract.Programs.CONTENT_ITEM_TYPE, isLive = true,
                progressPercentage = SearchInterface.PROGRESS_PERCENTAGE_HIDE)
        } else {
            SearchResult(
                channelId = channelId, channelNumber = channel.displayNumber, title = program.title,
                description = buildProgramDescription(channel.displayNumber, channel.displayName,
                    program.startTimeUtcMillis, program.endTimeUtcMillis),
                imageUri = program.posterArtUri, intentAction = Intent.ACTION_VIEW, intentData = buildIntentData(channelId),
                intentExtraData = TvContract.buildProgramUri(program.id).toString(),
                contentType = TvContract.Programs.CONTENT_ITEM_TYPE, isLive = true,
                videoWidth = program.videoWidth, videoHeight = program.videoHeight, duration = program.durationMillis,
                progressPercentage = getProgressPercentage(program.startTimeUtcMillis, program.endTimeUtcMillis))
        }
        results.add(result)
        channelsFound.add(channelId)
    }

    private fun buildProgramDescription(channelNumber: String?, channelName: String?, start: Long, end: Long): String =
        Utils.getDurationString(context, start, end, false) + System.lineSeparator() + channelNumber + " " + channelName

    private fun getProgressPercentage(start: Long, end: Long): Int {
        val now = System.currentTimeMillis()
        if (start > now || end <= now) return SearchInterface.PROGRESS_PERCENTAGE_HIDE
        return (100 * (now - start) / (end - start)).toInt()
    }

    private fun buildIntentData(channelId: Long) = TvContract.buildChannelUri(channelId).toString()

    /** Öffentliche API: gesperrte Freigabe laut Kindersicherung. */
    private fun isRatingBlocked(ratings: List<TvContentRating>?): Boolean {
        if (ratings.isNullOrEmpty() || !tvInputManager.isParentalControlsEnabled) return false
        return ratings.any {
            try {
                tvInputManager.isRatingBlocked(it)
            } catch (e: IllegalArgumentException) {
                false // Ungültige Freigabe
            }
        }
    }

    companion object {
        private const val TAG = "DataManagerSearch"
    }
}
