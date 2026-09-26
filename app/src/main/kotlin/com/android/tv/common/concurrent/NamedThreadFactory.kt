package com.android.tv.common.concurrent

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** ThreadFactory mit Namenspräfix ("<prefix>-<n>"). */
class NamedThreadFactory(prefix: String) : ThreadFactory {
    private val count = AtomicInteger(0)
    private val defaultFactory = Executors.defaultThreadFactory()
    private val prefix = "$prefix-"

    override fun newThread(runnable: Runnable): Thread =
        defaultFactory.newThread(runnable).apply { name = prefix + count.getAndIncrement() }

    fun namedWithPrefix(thread: Thread): Boolean = thread.name.startsWith(prefix)
}
