package com.android.tv.dvr.ui

import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidedAction

/**
 * [GuidedStepSupportFragment], der Klicks an [onTrackedGuidedActionClicked] weiterreicht.
 *
 * Analytics (Tracker) entfällt; Präfix und Labels bleiben als überschreibbare Hooks erhalten,
 * damit Unterklassen die API des Originals behalten.
 */
abstract class TrackedGuidedStepFragment : GuidedStepSupportFragment() {

    final override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        // Abweichung: kein Tracker.sendMenuClicked (Analytics entfernt)
        onTrackedGuidedActionClicked(action)
    }

    /** Label der Aktion (früher für Analytics). */
    open fun getTrackerLabelForGuidedAction(action: GuidedAction): String =
        when (val actionId = action.id) {
            GuidedAction.ACTION_ID_CANCEL -> "cancel"
            GuidedAction.ACTION_ID_NEXT -> "next"
            GuidedAction.ACTION_ID_CURRENT -> "current"
            GuidedAction.ACTION_ID_OK -> "ok"
            GuidedAction.ACTION_ID_FINISH -> "finish"
            GuidedAction.ACTION_ID_CONTINUE -> "continue"
            GuidedAction.ACTION_ID_YES -> "yes"
            GuidedAction.ACTION_ID_NO -> "no"
            else -> "unknown-$actionId"
        }

    /** Wird von [onGuidedActionClicked] aufgerufen. */
    abstract fun onTrackedGuidedActionClicked(action: GuidedAction)

    /** Präfix fürs Tracking, meist der Klassenname. Abweichung: nicht mehr abstrakt (Analytics entfernt). */
    open fun getTrackerPrefix(): String = javaClass.simpleName
}
