package com.android.tv.data

import android.media.tv.TvInputInfo
import com.android.tv.util.SetupUtils
import com.android.tv.util.TvInputManagerHelper

/** Sortiert neue Inputs zuerst, dann nicht eingerichtete, dann Standard-Reihenfolge. */
class TvInputNewComparator(private val setupUtils: SetupUtils, private val inputManager: TvInputManagerHelper) :
    Comparator<TvInputInfo> {
    override fun compare(lhs: TvInputInfo, rhs: TvInputInfo): Int {
        val lhsNew = setupUtils.isNewInput(lhs.id)
        val rhsNew = setupUtils.isNewInput(rhs.id)
        if (lhsNew != rhsNew) return if (lhsNew) -1 else 1
        if (!lhsNew) {
            val lhsDone = setupUtils.isSetupDone(lhs.id)
            val rhsDone = setupUtils.isSetupDone(rhs.id)
            if (lhsDone != rhsDone) return if (lhsDone) 1 else -1
        }
        return inputManager.defaultTvInputInfoComparator.compare(lhs, rhs)
    }
}
