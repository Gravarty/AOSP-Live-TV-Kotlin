package com.android.tv.recommendation

import com.android.tv.data.api.Program
import java.text.BreakIterator
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Zufallswert (für Vorschau-Kanäle). */
class RandomEvaluator : Recommender.Evaluator() {
    override fun evaluateChannel(channelId: Long): Double = Math.random()
}

/** Kürzlich und länger (3–7 min) gesehene Kanäle bevorzugen. */
class RecentChannelEvaluator : Recommender.Evaluator() {
    private var lastWatchLogUpdateTimeMs = System.currentTimeMillis()

    override fun onNewWatchLog(channelRecord: ChannelRecord) {
        lastWatchLogUpdateTimeMs = System.currentTimeMillis()
    }

    override fun evaluateChannel(channelId: Long): Double {
        val cr = recommender?.getChannelRecord(channelId) ?: return NOT_RECOMMENDED
        var maxScore = 0.0
        for (watched in cr.getWatchHistory().reversed()) {
            val recentWatchScore = watched.watchEndTimeMs.toDouble() / lastWatchLogUpdateTimeMs
            val watchDuration = watched.watchedDurationMs.toDouble()
            val watchDurationScore = when {
                watchDuration < WATCH_DURATION_MS_LOWER_BOUND -> MAX_SCORE_FOR_LOWER_BOUND
                watchDuration < WATCH_DURATION_MS_UPPER_BOUND ->
                    (watchDuration - WATCH_DURATION_MS_LOWER_BOUND) /
                        (WATCH_DURATION_MS_UPPER_BOUND - WATCH_DURATION_MS_LOWER_BOUND) + MAX_SCORE_FOR_LOWER_BOUND
                else -> 1.0
            }
            maxScore = max(maxScore, watchDurationScore * recentWatchScore)
        }
        return if (maxScore > 0.0) maxScore else NOT_RECOMMENDED
    }

    companion object {
        private val WATCH_DURATION_MS_LOWER_BOUND = TimeUnit.MINUTES.toMillis(3)
        private val WATCH_DURATION_MS_UPPER_BOUND = TimeUnit.MINUTES.toMillis(7)
        private const val MAX_SCORE_FOR_LOWER_BOUND = 0.1
    }
}

/** Anteil der Sehdauer eines Kanals an der Gesamtzeit (mind. 1 Tag). */
class FavoriteChannelEvaluator : Recommender.Evaluator() {
    private var earliestWatchStartTimeMs = System.currentTimeMillis()

    override fun onChannelRecordListChanged(channelRecords: List<ChannelRecord>) {
        for (cr in channelRecords) {
            val watched = cr.getWatchHistory()
            if (watched.isNotEmpty() && earliestWatchStartTimeMs > watched[0].watchStartTimeMs) {
                earliestWatchStartTimeMs = watched[0].watchStartTimeMs
            }
        }
    }

    override fun evaluateChannel(channelId: Long): Double {
        val cr = recommender?.getChannelRecord(channelId) ?: return NOT_RECOMMENDED
        if (cr.totalWatchDurationMs == 0L) return NOT_RECOMMENDED
        val watchPeriodMs = System.currentTimeMillis() - earliestWatchStartTimeMs
        return cr.totalWatchDurationMs.toDouble() / max(watchPeriodMs, MIN_WATCH_PERIOD_MS)
    }

    companion object {
        private const val MIN_WATCH_PERIOD_MS = 1000L * 60 * 60 * 24
    }
}

/** Gewohnheiten: ähnlicher Titel und gleiche Tageszeit/Wochentag wie früher Gesehenes. */
class RoutineWatchEvaluator : Recommender.Evaluator() {

    override fun evaluateChannel(channelId: Long): Double {
        val cr = recommender?.getChannelRecord(channelId) ?: return NOT_RECOMMENDED
        val currentProgram = cr.getCurrentProgram() ?: return NOT_RECOMMENDED
        val watchHistory = cr.getWatchHistory()
        if (watchHistory.isEmpty()) return NOT_RECOMMENDED
        var watchedProgram = watchHistory.last().program
        if (currentProgram.startTimeUtcMillis - watchedProgram.startTimeUtcMillis >= MAX_DIFF_MS_FOR_OLD_PROGRAM) {
            return NOT_RECOMMENDED
        }
        var maxScore = NOT_RECOMMENDED
        var watchedDurationMs = watchHistory.last().watchedDurationMs
        for (i in watchHistory.size - 2 downTo 0) {
            if (watchedProgram.startTimeUtcMillis == watchHistory[i].program.startTimeUtcMillis) {
                watchedDurationMs += watchHistory[i].watchedDurationMs
            } else {
                val score = calculateRoutineWatchScore(currentProgram, watchedProgram, watchedDurationMs)
                if (score >= REQUIRED_MIN_SCORE && score > maxScore) maxScore = score
                watchedProgram = watchHistory[i].program
                watchedDurationMs = watchHistory[i].watchedDurationMs
                if (currentProgram.startTimeUtcMillis - watchedProgram.startTimeUtcMillis >= MAX_DIFF_MS_FOR_OLD_PROGRAM) {
                    return maxScore
                }
            }
        }
        val score = calculateRoutineWatchScore(currentProgram, watchedProgram, watchedDurationMs)
        if (score >= REQUIRED_MIN_SCORE && score > maxScore) maxScore = score
        return maxScore
    }

    internal class ProgramTime(val startTimeOfDayInSec: Int, val endTimeOfDayInSec: Int, val weekDay: Int, val dayChanged: Boolean) {
        companion object {
            fun createFromProgram(p: Program): ProgramTime {
                val time = Calendar.getInstance()
                time.timeInMillis = p.startTimeUtcMillis
                val weekDay = time.get(Calendar.DAY_OF_WEEK)
                val start = getTimeOfDayInSec(time)
                time.timeInMillis = p.endTimeUtcMillis
                val dayChanged = weekDay != time.get(Calendar.DAY_OF_WEEK)
                // Lange Sendungen auf 12 h begrenzen
                val end = start + (min(p.endTimeUtcMillis - p.startTimeUtcMillis, TimeUnit.HOURS.toMillis(12)) / 1000).toInt()
                return ProgramTime(start, end, weekDay, dayChanged)
            }
        }
    }

    companion object {
        private const val REQUIRED_MIN_SCORE = 0.15
        internal const val MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK = 0.7
        private const val TITLE_MATCH_WEIGHT = 0.5
        private const val TIME_MATCH_WEIGHT = 1 - TITLE_MATCH_WEIGHT
        private val DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM = TimeUnit.DAYS.toMillis(14)
        private val MAX_DIFF_MS_FOR_OLD_PROGRAM = TimeUnit.DAYS.toMillis(56)

        private fun calculateRoutineWatchScore(currentProgram: Program, watchedProgram: Program, watchedDurationMs: Long): Double {
            val timeMatchScore = calculateTimeMatchScore(currentProgram, watchedProgram)
            val titleMatchScore = calculateTitleMatchScore(currentProgram.title, watchedProgram.title)
            val watchDurationScore = calculateWatchDurationScore(watchedProgram, watchedDurationMs)
            val diffMs = currentProgram.startTimeUtcMillis - watchedProgram.startTimeUtcMillis
            // Ältere Sendungen (14–56 Tage) zählen weniger
            val multiplierForOldProgram = if (diffMs < MAX_DIFF_MS_FOR_OLD_PROGRAM) {
                1.0 - max(diffMs - DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM, 0).toDouble() /
                    (MAX_DIFF_MS_FOR_OLD_PROGRAM - DIFF_MS_TOLERANCE_FOR_OLD_PROGRAM)
            } else {
                0.0
            }
            return (titleMatchScore * TITLE_MATCH_WEIGHT + timeMatchScore * TIME_MATCH_WEIGHT) *
                watchDurationScore * multiplierForOldProgram
        }

        /** F-Maß der längsten gemeinsamen Wortfolge. */
        internal fun calculateTitleMatchScore(title1: String?, title2: String?): Double {
            if (title1.isNullOrEmpty() || title2.isNullOrEmpty()) return 0.0
            val words1 = splitTextToWords(title1)
            val words2 = splitTextToWords(title2)
            if (words1.isEmpty() || words2.isEmpty()) return 0.0
            val maxLen = calculateMaximumMatchedWordSequenceLength(words1, words2)
            val precision = maxLen.toDouble() / words1.size
            val recall = maxLen.toDouble() / words2.size
            return 2.0 * precision * recall / (precision + recall)
        }

        internal fun calculateMaximumMatchedWordSequenceLength(toSearchWords: List<String>, toMatchWords: List<String>): Int {
            val matchedWordSeqLen = IntArray(toMatchWords.size)
            var maxLen = 0
            for (word in toSearchWords) {
                for (j in toMatchWords.indices.reversed()) {
                    if (word == toMatchWords[j]) {
                        matchedWordSeqLen[j] = if (j > 0) matchedWordSeqLen[j - 1] + 1 else 1
                    } else {
                        maxLen = max(maxLen, matchedWordSeqLen[j])
                        matchedWordSeqLen[j] = 0
                    }
                }
            }
            return max(maxLen, matchedWordSeqLen.maxOrNull() ?: 0)
        }

        private fun calculateTimeMatchScore(p1: Program, p2: Program): Double {
            val t1 = ProgramTime.createFromProgram(p1)
            val t2 = ProgramTime.createFromProgram(p2)
            val dupTimeScore = calculateOverlappedIntervalScore(t1, t2)
            val precision = dupTimeScore / (t1.endTimeOfDayInSec - t1.startTimeOfDayInSec)
            val recall = dupTimeScore / (t2.endTimeOfDayInSec - t2.startTimeOfDayInSec)
            return 2.0 * precision * recall / (precision + recall)
        }

        internal fun calculateOverlappedIntervalScore(t1: ProgramTime, t2: ProgramTime): Double {
            if (t1.dayChanged && !t2.dayChanged) return calculateOverlappedIntervalScore(t2, t1)
            var sameDay = false
            var score = max(0, min(t1.endTimeOfDayInSec, t2.endTimeOfDayInSec) - max(t1.startTimeOfDayInSec, t2.startTimeOfDayInSec)).toDouble()
            if (score > 0) {
                sameDay = t1.weekDay == t2.weekDay
            } else if (t1.dayChanged != t2.dayChanged) {
                // t2 geht über Mitternacht: Teil am Folgetag vergleichen
                score = max(0, min(t1.endTimeOfDayInSec, t2.endTimeOfDayInSec - 24 * 60 * 60) - t1.startTimeOfDayInSec).toDouble()
                sameDay = t1.weekDay == (t2.weekDay % 7) + 1
            }
            if (!sameDay) score *= MULTIPLIER_FOR_UNMATCHED_DAY_OF_WEEK
            return score
        }

        private fun calculateWatchDurationScore(program: Program, durationMs: Long): Double =
            durationMs.toDouble() / (program.endTimeUtcMillis - program.startTimeUtcMillis)

        internal fun getTimeOfDayInSec(time: Calendar): Int =
            time.get(Calendar.HOUR_OF_DAY) * 60 * 60 + time.get(Calendar.MINUTE) * 60 + time.get(Calendar.SECOND)

        internal fun splitTextToWords(text: String): List<String> {
            val words = ArrayList<String>()
            val boundary = BreakIterator.getWordInstance()
            boundary.setText(text)
            var start = boundary.first()
            var end = boundary.next()
            while (end != BreakIterator.DONE) {
                val word = text.substring(start, end)
                if (Character.isLetterOrDigit(word[0])) words.add(word)
                start = end
                end = boundary.next()
            }
            return words
        }
    }
}
