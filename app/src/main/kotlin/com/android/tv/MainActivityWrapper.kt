package com.android.tv

import androidx.annotation.MainThread
import com.android.tv.data.api.Channel
import javax.inject.Inject
import javax.inject.Singleton

/** Hält die aktuelle MainActivity und meldet Kanalwechsel an interessierte Komponenten. */
@MainThread
@Singleton
class MainActivityWrapper @Inject constructor() {

    fun interface OnCurrentChannelChangeListener {
        /** Kanal der MainActivity geändert (null = keine Wiedergabe). */
        fun onCurrentChannelChange(channel: Channel?)
    }

    var mainActivity: MainActivity? = null
        private set
    private val listeners = LinkedHashSet<OnCurrentChannelChangeListener>()

    fun isCurrent(activity: MainActivity?): Boolean = activity != null && mainActivity === activity

    fun onMainActivityCreated(activity: MainActivity) { mainActivity = activity }

    fun onMainActivityDestroyed(activity: MainActivity) {
        if (mainActivity === activity) mainActivity = null
    }

    fun notifyCurrentChannelChange(caller: MainActivity, channel: Channel?) {
        if (mainActivity === caller) listeners.toList().forEach { it.onCurrentChannelChange(channel) }
    }

    val isCreated: Boolean get() = mainActivity != null
    val isStarted: Boolean get() = mainActivity?.isActivityStarted == true
    val isResumed: Boolean get() = mainActivity?.isActivityResumed == true

    fun addOnCurrentChannelChangeListener(l: OnCurrentChannelChangeListener) { listeners.add(l) }
    fun removeOnCurrentChannelChangeListener(l: OnCurrentChannelChangeListener) { listeners.remove(l) }
}
