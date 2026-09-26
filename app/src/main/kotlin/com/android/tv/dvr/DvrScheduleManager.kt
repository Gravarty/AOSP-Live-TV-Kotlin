package com.android.tv.dvr

import android.content.Context
import android.media.tv.TvInputInfo
import android.util.ArraySet
import android.util.Range
import androidx.annotation.MainThread
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.util.Utils
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Aufnahmepläne je Input und Konflikterkennung (mehr Aufnahmen als Tuner). Höhere Priorität
 * gewinnt; verdrängte Aufnahmen können teilweise (später beginnend) noch laufen.
 */
@MainThread
class DvrScheduleManager(private val context: Context) {

    private val dataManager: DvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
    private val channelDataManager: ChannelDataManager = TvSingletons.getSingletons(context).getChannelDataManager()
    private val inputScheduleMap = HashMap<String, MutableList<ScheduledRecording>>()
    private val inputConflictInfoMap = HashMap<String, MutableMap<Long, ConflictInfo>>()
    var isInitialized = false
        private set
    private val onInitializeListeners = CopyOnWriteArraySet<OnInitializeListener>()
    private val scheduledRecordingListeners = ArraySet<ScheduledRecordingListener>()
    private val onConflictStateChangeListeners = ArraySet<OnConflictStateChangeListener>()

    init {
        if (dataManager.isDvrScheduleLoadFinished && channelDataManager.isDbLoadFinished) {
            buildData()
        } else {
            dataManager.addDvrScheduleLoadFinishedListener(object : DvrDataManager.OnDvrScheduleLoadFinishedListener {
                override fun onDvrScheduleLoadFinished() {
                    dataManager.removeDvrScheduleLoadFinishedListener(this)
                    if (channelDataManager.isDbLoadFinished && !isInitialized) buildData()
                }
            })
        }
        dataManager.addScheduledRecordingListener(object : ScheduledRecordingListener {
            override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
                if (!isInitialized) return
                for (schedule in scheduledRecordings) {
                    if (!schedule.isNotStarted && !schedule.isInProgress) continue
                    val input = inputFor(schedule.inputId)
                    if (!SoftPreconditions.checkArgument(input != null, TAG, "Input was removed for : %s", schedule)) {
                        forgetInput(schedule.inputId)
                        continue
                    }
                    inputScheduleMap.getOrPut(input!!.id) { ArrayList() }.add(schedule)
                }
                onSchedulesChanged()
                notifyScheduledRecordingAdded(*scheduledRecordings)
            }

            override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
                if (!isInitialized) return
                for (schedule in scheduledRecordings) {
                    val input = inputFor(schedule.inputId)
                    if (input == null) {
                        forgetInput(schedule.inputId)
                        continue
                    }
                    val inputId = input.id
                    inputScheduleMap[inputId]?.let {
                        it.remove(schedule)
                        if (it.isEmpty()) inputScheduleMap.remove(inputId)
                    }
                    inputConflictInfoMap[inputId]?.let {
                        it.remove(schedule.id)
                        if (it.isEmpty()) inputConflictInfoMap.remove(inputId)
                    }
                }
                onSchedulesChanged()
                notifyScheduledRecordingRemoved(*scheduledRecordings)
            }

            override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
                if (!isInitialized) return
                for (schedule in scheduledRecordings) {
                    val input = inputFor(schedule.inputId)
                    if (!SoftPreconditions.checkArgument(input != null, TAG, "Input was removed for : %s", schedule)) {
                        forgetInput(schedule.inputId)
                        continue
                    }
                    val inputId = input!!.id
                    val schedules = inputScheduleMap.getOrPut(inputId) { ArrayList() }
                    // Alten Eintrag ersetzen (nur aktive behalten)
                    val idx = schedules.indexOfFirst { it.id == schedule.id }
                    if (idx >= 0) schedules.removeAt(idx)
                    if (schedule.isNotStarted || schedule.isInProgress) schedules.add(schedule)
                    if (schedules.isEmpty()) inputScheduleMap.remove(inputId)
                    inputConflictInfoMap[inputId]?.get(schedule.id)?.schedule = schedule
                }
                onSchedulesChanged()
                notifyScheduledRecordingStatusChanged(*scheduledRecordings)
            }
        })
        channelDataManager.addListener(object : ChannelDataManager.Listener {
            override fun onLoadFinished() {
                if (dataManager.isDvrScheduleLoadFinished && !isInitialized) buildData()
            }

            override fun onChannelListUpdated() {
                if (dataManager.isDvrScheduleLoadFinished) buildData()
            }

            override fun onChannelBrowsableChanged() {}
        })
    }

    private fun inputFor(inputId: String?): TvInputInfo? = inputId?.let { Utils.getTvInputInfoForInputId(context, it) }

    private fun forgetInput(inputId: String?) {
        inputScheduleMap.remove(inputId)
        inputConflictInfoMap.remove(inputId)
    }

    private fun getStartedRecordings(inputId: String): List<ScheduledRecording> {
        if (!SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")) return emptyList()
        return inputScheduleMap[inputId]?.filter { it.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS } ?: emptyList()
    }

    /** Aktive Pläne je Input aus den Kanälen neu aufbauen. */
    private fun buildData() {
        inputScheduleMap.clear()
        for (schedule in dataManager.getAllScheduledRecordings()) {
            if (!schedule.isNotStarted && !schedule.isInProgress) continue
            val channel = channelDataManager.getChannel(schedule.channelId) ?: continue
            inputScheduleMap.getOrPut(channel.inputId) { ArrayList() }.add(schedule)
        }
        if (!isInitialized) {
            isInitialized = true
            notifyInitialize()
        }
        onSchedulesChanged()
    }

    /** Konflikte neu berechnen und Änderungen melden. */
    private fun onSchedulesChanged() {
        val addedConflicts = ArrayList<ScheduledRecording>()
        val removedConflicts = ArrayList<ScheduledRecording>()
        for (inputId in inputScheduleMap.keys) {
            val oldConflictMap = HashMap<Long, ScheduledRecording>()
            inputConflictInfoMap[inputId]?.values?.forEach { oldConflictMap[it.schedule.id] = it.schedule }
            val conflicts = getConflictingSchedulesInfo(inputId)
            if (conflicts.isEmpty()) {
                inputConflictInfoMap.remove(inputId)
            } else {
                val infos = HashMap<Long, ConflictInfo>()
                for (info in conflicts) {
                    infos[info.schedule.id] = info
                    if (oldConflictMap.remove(info.schedule.id) == null) addedConflicts.add(info.schedule)
                }
                inputConflictInfoMap[inputId] = infos
            }
            removedConflicts.addAll(oldConflictMap.values)
        }
        if (removedConflicts.isNotEmpty()) notifyConflictStateChange(false, *removedConflicts.toTypedArray())
        if (addedConflicts.isNotEmpty()) notifyConflictStateChange(true, *addedConflicts.toTypedArray())
    }

    fun addScheduledRecordingListener(listener: ScheduledRecordingListener) { scheduledRecordingListeners.add(listener) }
    fun removeScheduledRecordingListener(listener: ScheduledRecordingListener) { scheduledRecordingListeners.remove(listener) }

    private fun notifyScheduledRecordingAdded(vararg r: ScheduledRecording) = scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingAdded(*r) }
    private fun notifyScheduledRecordingRemoved(vararg r: ScheduledRecording) = scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingRemoved(*r) }
    private fun notifyScheduledRecordingStatusChanged(vararg r: ScheduledRecording) =
        scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingStatusChanged(*r) }

    fun addOnInitializeListener(listener: OnInitializeListener) { onInitializeListeners.add(listener) }
    fun removeOnInitializeListener(listener: OnInitializeListener) { onInitializeListeners.remove(listener) }
    private fun notifyInitialize() = onInitializeListeners.forEach { it.onInitialize() }

    fun addOnConflictStateChangeListener(listener: OnConflictStateChangeListener) { onConflictStateChangeListeners.add(listener) }
    fun removeOnConflictStateChangeListener(listener: OnConflictStateChangeListener) { onConflictStateChangeListeners.remove(listener) }
    private fun notifyConflictStateChange(conflict: Boolean, vararg r: ScheduledRecording) =
        onConflictStateChangeListeners.toList().forEach { it.onConflictStateChange(conflict, *r) }

    /** Priorität über allen vorhandenen Aufnahmen. */
    fun suggestNewPriority(): Long {
        if (!SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")) return DEFAULT_PRIORITY
        return suggestHighestPriority()
    }

    private fun suggestHighestPriority(): Long {
        var highest = DEFAULT_PRIORITY - PRIORITY_OFFSET
        for (s in dataManager.getAllScheduledRecordings()) if (s.priority > highest) highest = s.priority
        return highest + PRIORITY_OFFSET
    }

    /** Priorität über allen überlappenden Aufnahmen desselben Inputs. */
    fun suggestHighestPriority(schedule: ScheduledRecording): Long {
        val schedules = inputScheduleMap[schedule.inputId] ?: return DEFAULT_PRIORITY
        var highest = Long.MIN_VALUE
        for (r in schedules) if (r != schedule && r.isOverLapping(schedule) && r.priority > highest) highest = r.priority
        if (highest == Long.MIN_VALUE || highest < schedule.priority) return schedule.priority
        return highest + PRIORITY_OFFSET
    }

    fun suggestHighestPriority(inputId: String, period: Range<Long>, basePriority: Long): Long {
        val schedules = inputScheduleMap[inputId] ?: return DEFAULT_PRIORITY
        var highest = Long.MIN_VALUE
        for (r in schedules) if (r.isOverLapping(period) && r.priority > highest) highest = r.priority
        if (highest == Long.MIN_VALUE || highest < basePriority) return basePriority
        return highest + PRIORITY_OFFSET
    }

    fun suggestNewSeriesPriority(): Long {
        if (!SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")) return DEFAULT_SERIES_PRIORITY
        var highest = DEFAULT_SERIES_PRIORITY - PRIORITY_OFFSET
        for (s in dataManager.getSeriesRecordings()) if (s.priority > highest) highest = s.priority
        return highest + PRIORITY_OFFSET
    }

    /** Aufnahmen, die mit einer neuen Aufnahme dieser Sendung kollidieren würden. */
    fun getConflictingSchedules(program: Program): List<ScheduledRecording> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        SoftPreconditions.checkState(Program.isProgramValid(program), TAG, "Program is invalid: $program")
        SoftPreconditions.checkState(program.startTimeUtcMillis < program.endTimeUtcMillis, TAG, "Program duration is empty: $program")
        if (!isInitialized || !Program.isProgramValid(program) || program.startTimeUtcMillis >= program.endTimeUtcMillis) return emptyList()
        val input = Utils.getTvInputInfoForProgram(context, program)
        if (input == null || !input.canRecord() || input.tunerCount <= 0) return emptyList()
        return getConflictingSchedules(input,
            listOf(ScheduledRecording.builder(input.id, program).setPriority(suggestHighestPriority()).build()))
    }

    fun getConflictingSchedules(seriesRecording: SeriesRecording?): List<ScheduledRecording> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        SoftPreconditions.checkState(seriesRecording != null, TAG, "series recording is null")
        if (!isInitialized || seriesRecording == null) return emptyList()
        val input = inputFor(seriesRecording.inputId)
        if (input == null || !input.canRecord() || input.tunerCount <= 0) return emptyList()
        val available = dataManager.getScheduledRecordings(seriesRecording.id).filter { it.isNotStarted || it.isInProgress }
        if (available.isEmpty()) return emptyList()
        return getConflictingSchedules(input, available)
    }

    fun getConflictingSchedules(channelId: Long, startTimeMs: Long, endTimeMs: Long): List<ScheduledRecording> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID")
        SoftPreconditions.checkState(startTimeMs < endTimeMs, TAG, "Recording duration is empty.")
        if (!isInitialized || channelId == Channel.INVALID_ID || startTimeMs >= endTimeMs) return emptyList()
        val input = Utils.getTvInputInfoForChannelId(context, channelId)
        if (input == null || !input.canRecord() || input.tunerCount <= 0) return emptyList()
        return getConflictingSchedules(input,
            listOf(ScheduledRecording.builder(input.id, channelId, startTimeMs, endTimeMs).setPriority(suggestHighestPriority()).build()))
    }

    private fun getConflictingSchedulesInfo(inputId: String): List<ConflictInfo> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        val input = inputFor(inputId)
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for : $inputId")
        if (!isInitialized || input == null) return emptyList()
        val schedules = inputScheduleMap[input.id]
        if (schedules.isNullOrEmpty()) return emptyList()
        return getConflictingSchedulesInfo(schedules, input.tunerCount, null)
    }

    fun isConflicting(schedule: ScheduledRecording): Boolean {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        val input = inputFor(schedule.inputId)
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID : ${schedule.channelId}")
        if (!isInitialized || input == null) return false
        return inputConflictInfoMap[input.id]?.containsKey(schedule.id) == true
    }

    fun isPartiallyConflicting(schedule: ScheduledRecording): Boolean {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        val input = inputFor(schedule.inputId)
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID : ${schedule.channelId}")
        if (!isInitialized || input == null) return false
        return inputConflictInfoMap[input.id]?.get(schedule.id)?.partialConflict == true
    }

    /** Laufende Aufnahmen, die beim Tunen auf [channelId] abbrechen müssten. */
    fun getConflictingSchedulesForTune(channelId: Long): List<ScheduledRecording> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID")
        val input = Utils.getTvInputInfoForChannelId(context, channelId)
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID: $channelId")
        if (!isInitialized || channelId == Channel.INVALID_ID || input == null) return emptyList()
        return getConflictingSchedulesForTune(input.id, channelId, System.currentTimeMillis(), suggestHighestPriority(),
            getStartedRecordings(input.id), input.tunerCount)
    }

    /** Aufnahmen, die beim dauerhaften Schauen von [channelId] nicht laufen könnten. */
    fun getConflictingSchedulesForWatching(channelId: Long): List<ScheduledRecording> {
        SoftPreconditions.checkState(isInitialized, TAG, "Not initialized yet")
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID")
        val input = Utils.getTvInputInfoForChannelId(context, channelId)
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID: $channelId")
        if (!isInitialized || channelId == Channel.INVALID_ID || input == null) return emptyList()
        val schedules = inputScheduleMap[input.id]
        if (schedules.isNullOrEmpty()) return emptyList()
        return getConflictingSchedulesForWatching(input.id, channelId, System.currentTimeMillis(), suggestNewPriority(), schedules, input.tunerCount)
    }

    private fun getConflictingSchedules(input: TvInputInfo, schedulesToAdd: List<ScheduledRecording>): List<ScheduledRecording> {
        if (!input.canRecord() || input.tunerCount <= 0) return emptyList()
        val current = inputScheduleMap[input.id]
        if (current.isNullOrEmpty()) return emptyList()
        return getConflictingSchedules(schedulesToAdd, current, input.tunerCount)
    }

    internal class ConflictInfo(var schedule: ScheduledRecording, val partialConflict: Boolean)

    fun interface OnInitializeListener {
        fun onInitialize()
    }

    fun interface OnConflictStateChangeListener {
        fun onConflictStateChange(conflict: Boolean, vararg schedules: ScheduledRecording)
    }

    companion object {
        private const val TAG = "DvrScheduleManager"
        const val DEFAULT_PRIORITY = Long.MAX_VALUE shr 1
        const val DEFAULT_SERIES_PRIORITY = DEFAULT_PRIORITY shr 1
        private const val PRIORITY_OFFSET = 1024L

        /** Höchste Priorität zuerst, dann früherer Start, dann neuere ID. */
        private val RESULT_COMPARATOR: Comparator<ScheduledRecording> = ScheduledRecording.PRIORITY_COMPARATOR.reversed()
            .then(ScheduledRecording.START_TIME_COMPARATOR).then(ScheduledRecording.ID_COMPARATOR.reversed())
        /** Verdrängungskandidat: niedrigste Priorität, früheres Ende, ältere ID. */
        private val CANDIDATE_COMPARATOR: Comparator<ScheduledRecording> = ScheduledRecording.PRIORITY_COMPARATOR
            .then(ScheduledRecording.END_TIME_COMPARATOR).then(ScheduledRecording.ID_COMPARATOR)

        @JvmStatic
        fun suggestSeriesPriority(order: Int): Long = DEFAULT_SERIES_PRIORITY + order * PRIORITY_OFFSET

        @JvmStatic
        fun getConflictingSchedulesForTune(
            inputId: String, channelId: Long, currentTimeMs: Long, newPriority: Long,
            startedRecordings: List<ScheduledRecording>, tunerCount: Int,
        ): List<ScheduledRecording> {
            val channelFound = startedRecordings.any { it.channelId == channelId }
            val schedules = if (!channelFound) {
                startedRecordings + ScheduledRecording.builder(inputId, channelId, currentTimeMs, currentTimeMs + 1)
                    .setPriority(newPriority).build()
            } else {
                startedRecordings
            }
            return getConflictingSchedules(schedules, tunerCount)
        }

        internal fun getConflictingSchedulesForWatching(
            inputId: String, channelId: Long, currentTimeMs: Long, newPriority: Long,
            schedules: List<ScheduledRecording>, tunerCount: Int,
        ): List<ScheduledRecording> {
            val toCheck = ArrayList(schedules)
            val sameChannel = ArrayList<ScheduledRecording>()
            for (s in schedules) if (s.channelId == channelId) {
                sameChannel.add(s)
                toCheck.remove(s)
            }
            // Schauen belegt einen Tuner bis "unendlich"
            toCheck.add(ScheduledRecording.builder(inputId, channelId, currentTimeMs, Long.MAX_VALUE).setPriority(newPriority).build())
            val result = ArrayList<ScheduledRecording>()
            result.addAll(getConflictingSchedules(sameChannel, 1))
            result.addAll(getConflictingSchedules(toCheck, tunerCount))
            result.sortWith(RESULT_COMPARATOR)
            return result
        }

        internal fun getConflictingSchedules(
            schedulesToAdd: List<ScheduledRecording>, currentSchedules: List<ScheduledRecording>, tunerCount: Int,
        ): List<ScheduledRecording> {
            val toCheck = ArrayList(currentSchedules)
            // Bereits vorhandene gleiche Aufnahmen ersetzen
            toCheck.removeAll { schedule ->
                schedulesToAdd.any { toAdd ->
                    if (schedule.type == ScheduledRecording.TYPE_PROGRAM) toAdd.programId == schedule.programId
                    else toAdd.channelId == schedule.channelId && toAdd.startTimeMs == schedule.startTimeMs && toAdd.endTimeMs == schedule.endTimeMs
                }
            }
            toCheck.addAll(schedulesToAdd)
            val ranges = schedulesToAdd.map { Range(it.startTimeMs, it.endTimeMs) }
            return getConflictingSchedules(toCheck, tunerCount, ranges)
        }

        @JvmStatic
        fun getConflictingSchedules(schedules: List<ScheduledRecording>, tunerCount: Int): List<ScheduledRecording> =
            getConflictingSchedules(schedules, tunerCount, null)

        internal fun getConflictingSchedules(schedules: List<ScheduledRecording>, tunerCount: Int, periods: List<Range<Long>>?) =
            getConflictingSchedulesInfo(schedules, tunerCount, periods).map { it.schedule }

        /**
         * Simuliert die Tuner-Belegung in Startzeit-Reihenfolge: Ist kein Tuner frei, wird die
         * niedrigst priorisierte Aufnahme verdrängt (teilweiser Konflikt) oder die neue ist im
         * Konflikt und wird ab dem frühesten freien Tuner erneut geprüft.
         */
        internal fun getConflictingSchedulesInfo(schedules: List<ScheduledRecording>, tunerCount: Int, periods: List<Range<Long>>?): List<ConflictInfo> {
            val toCheck = ArrayList(schedules)
            toCheck.sortWith(ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR)
            val recordings = ArrayList<ScheduledRecording>()
            val conflicts = HashMap<ScheduledRecording, ConflictInfo>()
            val modified2Original = HashMap<ScheduledRecording, ScheduledRecording>()
            while (toCheck.isNotEmpty()) {
                val schedule = toCheck.removeAt(0)
                recordings.removeAll { it.endTimeMs <= schedule.startTimeMs }
                if (recordings.size < tunerCount) {
                    recordings.add(schedule)
                    modified2Original[schedule]?.let { conflicts[it] = ConflictInfo(it, true) }
                    continue
                }
                val candidate = findReplaceableRecording(recordings, schedule)
                if (candidate != null) {
                    if (!modified2Original.containsKey(candidate)) conflicts[candidate] = ConflictInfo(candidate, true)
                    recordings.remove(candidate)
                    recordings.add(schedule)
                    modified2Original[schedule]?.let { conflicts[it] = ConflictInfo(it, true) }
                } else {
                    if (!modified2Original.containsKey(schedule)) conflicts[schedule] = ConflictInfo(schedule, false)
                    val earliestEndTime = recordings.minOfOrNull { it.endTimeMs } ?: Long.MAX_VALUE
                    if (earliestEndTime < schedule.endTimeMs) {
                        // Rest der Aufnahme ab dem ersten freien Tuner erneut prüfen
                        val modified = ScheduledRecording.buildFrom(schedule).setStartTimeMs(earliestEndTime).build()
                        modified2Original[modified] = modified2Original[schedule] ?: schedule
                        val pos = Collections.binarySearch(toCheck, modified, ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR)
                        toCheck.add(if (pos >= 0) pos else -pos - 1, modified)
                    }
                }
            }
            if (!periods.isNullOrEmpty()) conflicts.keys.retainAll { s -> periods.any { s.isOverLapping(it) } }
            return conflicts.values.sortedWith { l, r -> RESULT_COMPARATOR.compare(l.schedule, r.schedule) }
        }

        private fun findReplaceableRecording(recordings: List<ScheduledRecording>, schedule: ScheduledRecording): ScheduledRecording? {
            var candidate: ScheduledRecording? = null
            for (r in recordings) {
                if (schedule.priority > r.priority && (candidate == null || CANDIDATE_COMPARATOR.compare(candidate, r) > 0)) candidate = r
            }
            return candidate
        }
    }
}
