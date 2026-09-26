package com.android.tv.onboarding

import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.ui.setup.SetupActionHelper

/** Hinweis "Neue Quellen verfügbar" mit Einrichten/Überspringen. */
class NewSourcesFragment : Fragment() {
    init {
        allowEnterTransitionOverlap = false
        allowReturnTransitionOverlap = false
        enterTransition = Slide(Gravity.BOTTOM)
        exitTransition = Slide(Gravity.BOTTOM)
        reenterTransition = Slide(Gravity.BOTTOM)
        returnTransition = Slide(Gravity.BOTTOM)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_new_sources, container, false)
        initializeButton(view.findViewById(R.id.setup), ACTION_SETUP)
        initializeButton(view.findViewById(R.id.skip), ACTION_SKIP)
        // Einmal gezeigt: alle aktuellen Quellen gelten als erkannt
        val singletons = TvSingletons.getSingletons(requireActivity())
        singletons.getSetupUtils().markAllInputsRecognized(singletons.getTvInputManagerHelper())
        view.requestFocus()
        return view
    }

    private fun initializeButton(view: View, actionId: Int) {
        view.setOnClickListener(SetupActionHelper.createOnClickListenerForAction(this, ACTION_CATEOGRY, actionId, null))
    }

    companion object {
        // Schreibfehler "CATEOGRY" aus dem Original beibehalten
        const val ACTION_CATEOGRY = "com.android.tv.onboarding.NewSourcesFragment"
        const val ACTION_SETUP = 1
        const val ACTION_SKIP = 2
    }
}
