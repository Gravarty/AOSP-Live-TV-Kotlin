package com.android.tv.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import com.android.tv.R
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.ChannelImpl
import com.android.tv.data.ProgramDataManager
import com.android.tv.data.api.Channel

/** Lädt Logos/Poster der Kanalkarten 500 ms verzögert vor. */
class ChannelsPosterPrefetcher(
    context: Context,
    private val programDataManager: ProgramDataManager,
    private val channelsAdapter: ChannelsRowAdapter,
) {
    private val context = context.applicationContext
    private val posterArtWidth = context.resources.getDimensionPixelSize(R.dimen.card_image_layout_width)
    private val posterArtHeight = context.resources.getDimensionPixelSize(R.dimen.card_image_layout_height)
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_PREFETCH_IMAGE) doPrefetchImages()
        true
    }
    private var isCanceled = false

    fun prefetch() {
        SoftPreconditions.checkState(!isCanceled, TAG, "Prefetch called after cancel was called.")
        if (isCanceled) return
        handler.removeMessages(MSG_PREFETCH_IMAGE)
        handler.sendMessageDelayed(handler.obtainMessage(MSG_PREFETCH_IMAGE), ONDEMAND_POSTER_PREFETCH_DELAY_MILLIS)
    }

    fun cancel() {
        isCanceled = true
        handler.removeCallbacksAndMessages(null)
    }

    @MainThread
    private fun doPrefetchImages() {
        for (item in channelsAdapter.itemList) {
            if (isCanceled) return
            val channel = item.channel
            if (!ChannelImpl.isValid(channel)) continue
            channel!!.prefetchImage(context, Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO, posterArtWidth, posterArtHeight)
            programDataManager.getCurrentProgram(channel.id)?.prefetchPosterArt(context, posterArtWidth, posterArtHeight)
        }
    }

    companion object {
        private const val TAG = "PosterPrefetcher"
        private const val MSG_PREFETCH_IMAGE = 1000
        private const val ONDEMAND_POSTER_PREFETCH_DELAY_MILLIS = 500L
    }
}
