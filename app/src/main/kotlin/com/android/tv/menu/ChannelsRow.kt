package com.android.tv.menu

import android.content.Context
import com.android.tv.R
import com.android.tv.data.ProgramDataManager
import com.android.tv.recommendation.RecentChannelEvaluator
import com.android.tv.recommendation.Recommender

/** Kanal-Zeile: Guide/Setup/DVR/App-Link und zuletzt gesehene bzw. empfohlene Kanäle. */
class ChannelsRow(context: Context, menu: Menu, programDataManager: ProgramDataManager) :
    ItemListRow(context, menu, R.string.menu_title_channels, R.dimen.card_layout_height, PlaceholderAdapter(context)) {

    private var tvRecommendation: Recommender?
    private val channelsAdapter: ChannelsRowAdapter
    private val channelsPosterPrefetcher: ChannelsPosterPrefetcher

    init {
        // Bugfix: Listener kann feuern, bevor Adapter/Prefetcher existieren (Original: NPE)
        var adapterRef: ChannelsRowAdapter? = null
        var prefetcherRef: ChannelsPosterPrefetcher? = null
        tvRecommendation = Recommender(context, object : Recommender.Listener {
            override fun onRecommenderReady() {
                adapterRef?.update()
                prefetcherRef?.prefetch()
            }

            override fun onRecommendationChanged() {
                adapterRef?.update()
                prefetcherRef?.prefetch()
            }
        }, true).also { it.registerEvaluator(RecentChannelEvaluator()) }
        channelsAdapter = ChannelsRowAdapter(context, tvRecommendation!!, MIN_COUNT_FOR_RECENT_CHANNELS, MAX_COUNT_FOR_RECENT_CHANNELS)
        adapterRef = channelsAdapter
        adapter = channelsAdapter
        channelsPosterPrefetcher = ChannelsPosterPrefetcher(context, programDataManager, channelsAdapter)
        prefetcherRef = channelsPosterPrefetcher
    }

    override fun release() {
        super.release()
        tvRecommendation?.release()
        tvRecommendation = null
        channelsPosterPrefetcher.cancel()
        channelsAdapter.release()
    }

    override fun onRecentChannelsChanged() = channelsPosterPrefetcher.prefetch()

    override fun getId(): String = ID

    /** Nur bis der echte Adapter im init gesetzt ist (Original übergab null). */
    private class PlaceholderAdapter(context: Context) : ItemListRowView.ItemListAdapter<Any>(context) {
        override fun update() {}
        override fun getLayoutResId(viewType: Int) = 0
    }

    companion object {
        val ID: String = ChannelsRow::class.java.name
        const val MIN_COUNT_FOR_RECENT_CHANNELS = 5
        const val MAX_COUNT_FOR_RECENT_CHANNELS = 10
    }
}
