package com.android.tv.recommendation

import android.content.Context
import android.util.Log
import com.android.tv.data.api.Channel
import java.util.concurrent.TimeUnit

/** Kanal-Empfehlungen aus gewichteten Bewertern (höchster Wert je Kanal zählt). */
class Recommender(
    context: Context,
    private val listener: Listener,
    private val includeRecommendedOnly: Boolean,
) : RecommendationDataManager.Listener {

    interface Listener {
        fun onRecommenderReady()
        fun onRecommendationChanged()
    }

    private val evaluators = ArrayList<EvaluatorWrapper>()
    private val channelSortKey = HashMap<Long, String>()
    private var previousRecommendedChannels: List<Channel> = emptyList()
    private var lastRecommendationUpdatedTimeUtcMillis = 0L
    var isReady = false
        private set

    // Meldet sich beim gemeinsamen DataManager an (Callbacks kommen auf dem Main-Thread, später)
    private val dm: RecommendationDataManager = RecommendationDataManager.acquireManager(context, this)

    fun release() = dm.release(this)

    @JvmOverloads
    fun registerEvaluator(evaluator: Evaluator, baseScore: Double = DEFAULT_BASE_SCORE, weight: Double = DEFAULT_WEIGHT) {
        evaluators.add(EvaluatorWrapper(this, evaluator, baseScore, weight))
    }

    fun recommendChannels(): List<Channel> = recommendChannels(dm.channelRecordCount)

    /** Bis zu [size] Kanäle, absteigend nach Bewertung; setzt die Sortierschlüssel. */
    fun recommendChannels(size: Int): List<Channel> {
        val records = ArrayList<Pair<Channel, Double>>()
        for (cr in dm.getChannelRecords()) {
            var maxScore = Evaluator.NOT_RECOMMENDED
            for (evaluator in evaluators) {
                val score = evaluator.getScaledEvaluatorScore(cr.channel.id)
                if (score > maxScore) maxScore = score
            }
            if (!includeRecommendedOnly || maxScore != Evaluator.NOT_RECOMMENDED) records.add(cr.channel to maxScore)
        }
        val count = minOf(size, records.size)
        records.sortByDescending { it.second }
        channelSortKey.clear()
        val sortKeyFormat = "%0${count.toString().length}d"
        return (0 until count).map { i ->
            channelSortKey[records[i].first.id] = String.format(sortKeyFormat, i)
            records[i].first
        }
    }

    fun getChannel(channelId: Long): Channel? = dm.getChannelRecord(channelId)?.channel
    fun getChannelRecord(channelId: Long): ChannelRecord? = dm.getChannelRecord(channelId)
    fun getChannelSortKey(channelId: Long): String = channelSortKey[channelId] ?: INVALID_CHANNEL_SORT_KEY

    override fun onChannelRecordLoaded() {
        isReady = true
        listener.onRecommenderReady()
        val channels = ArrayList(dm.getChannelRecords())
        evaluators.forEach { it.onChannelListChanged(channels) }
    }

    override fun onNewWatchLog(channelRecord: ChannelRecord) {
        evaluators.forEach { it.onNewWatchLog(channelRecord) }
        checkRecommendationChanged()
    }

    override fun onChannelRecordChanged() {
        if (isReady) {
            val channels = ArrayList(dm.getChannelRecords())
            evaluators.forEach { it.onChannelListChanged(channels) }
        }
        checkRecommendationChanged()
    }

    /** Höchstens alle 5 Minuten neu berechnen. */
    private fun checkRecommendationChanged() {
        val now = System.currentTimeMillis()
        if (now - lastRecommendationUpdatedTimeUtcMillis < MINIMUM_RECOMMENDATION_UPDATE_PERIOD) return
        lastRecommendationUpdatedTimeUtcMillis = now
        val recommended = recommendChannels()
        if (recommended != previousRecommendedChannels) {
            previousRecommendedChannels = recommended
            listener.onRecommendationChanged()
        }
    }

    internal fun setLastRecommendationUpdatedTimeUtcMs(newUpdatedTimeMs: Long) {
        lastRecommendationUpdatedTimeUtcMillis = newUpdatedTimeMs
    }

    /** Bewertet Kanäle mit 0..1 bzw. NOT_RECOMMENDED. */
    abstract class Evaluator {
        protected var recommender: Recommender? = null
            private set

        open fun onChannelRecordListChanged(channelRecords: List<ChannelRecord>) {}
        open fun onNewWatchLog(channelRecord: ChannelRecord) {}
        abstract fun evaluateChannel(channelId: Long): Double

        internal fun attach(recommender: Recommender) { this.recommender = recommender }

        companion object {
            const val NOT_RECOMMENDED = -1.0
        }
    }

    private class EvaluatorWrapper(recommender: Recommender, private val evaluator: Evaluator,
        private val baseScore: Double, private val weight: Double) {
        init {
            evaluator.attach(recommender)
        }

        fun getScaledEvaluatorScore(channelId: Long): Double {
            var score = evaluator.evaluateChannel(channelId)
            if (score < 0.0) {
                if (score != Evaluator.NOT_RECOMMENDED) Log.w(TAG, "Unexpected score ($score) from the recommender$evaluator")
                return Evaluator.NOT_RECOMMENDED
            } else if (score > 1.0) {
                Log.w(TAG, "Unexpected score ($score) from the recommender$evaluator")
                score = 1.0
            }
            return baseScore + score * weight
        }

        fun onNewWatchLog(channelRecord: ChannelRecord) = evaluator.onNewWatchLog(channelRecord)
        fun onChannelListChanged(channelRecords: List<ChannelRecord>) = evaluator.onChannelRecordListChanged(channelRecords)
    }

    companion object {
        private const val TAG = "Recommender"
        private val MINIMUM_RECOMMENDATION_UPDATE_PERIOD = TimeUnit.MINUTES.toMillis(5)
        internal const val INVALID_CHANNEL_SORT_KEY = "INVALID"
        private const val DEFAULT_BASE_SCORE = 0.0
        private const val DEFAULT_WEIGHT = 1.0
    }
}
