package com.android.tv.onboarding

import android.content.Context
import android.graphics.Typeface
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager.TvInputCallback
import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.ui.setup.SetupGuidedStepFragment
import com.android.tv.common.ui.setup.SetupMultiPaneFragment
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.TvInputNewComparator
import com.android.tv.ui.GuidedActionsStylistWithDivider
import com.android.tv.util.OnboardingUtils
import com.android.tv.util.SetupUtils
import com.android.tv.util.TvInputManagerHelper

/**
 * Kanalquellen: neue, noch nicht und bereits eingerichtete Inputs mit Kanalanzahl.
 * Entfällt: Netzwerk-Tuner-Suche (eingebauter Tuner).
 */
class SetupSourcesFragment : SetupMultiPaneFragment() {

    override fun onEnterTransitionEnd() {
        (getContentFragment() as? ContentFragment)?.executePendingAction()
    }

    override fun onCreateContentFragment(): SetupGuidedStepFragment = ContentFragment().apply {
        arguments = Bundle().apply { putBoolean(SetupGuidedStepFragment.KEY_THREE_PANE, true) }
    }

    override fun getActionCategory() = ACTION_CATEGORY

    /** Für das innere Fragment (protected in SetupFragment). */
    internal fun dispatchAction(actionId: Int, params: Bundle? = null) = onActionClick(ACTION_CATEGORY, actionId, params)

    internal val isParentEnterTransitionRunning: Boolean get() = isEnterTransitionRunning

    class ContentFragment : SetupGuidedStepFragment() {
        private lateinit var inputManager: TvInputManagerHelper
        private lateinit var channelDataManager: ChannelDataManager
        private lateinit var setupUtils: SetupUtils
        private var inputs: MutableList<TvInputInfo> = ArrayList()
        private var inputsBuilt = false
        private var knownInputStartIndex = 0
        private var doneInputStartIndex = 0
        private var parent: SetupSourcesFragment? = null
        private var newlyAddedInputId: String? = null
        private var pendingAction = PENDING_ACTION_NONE

        private val inputCallback = object : TvInputCallback() {
            override fun onInputAdded(inputId: String) = handleInputChanged()
            override fun onInputRemoved(inputId: String) = handleInputChanged()
            override fun onInputUpdated(inputId: String) = handleInputChanged()
            override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) = handleInputChanged()

            private fun handleInputChanged() {
                // Während des Übergangs nicht aktualisieren (sonst ruckelt es)
                if (parent?.isParentEnterTransitionRunning == true) {
                    pendingAction = PENDING_ACTION_INPUT_CHANGED
                    return
                }
                buildInputs()
                updateActions()
            }
        }

        private val channelDataManagerListener = object : ChannelDataManager.Listener {
            override fun onLoadFinished() = handleChannelChanged()
            override fun onChannelListUpdated() = handleChannelChanged()
            override fun onChannelBrowsableChanged() = handleChannelChanged()

            private fun handleChannelChanged() {
                if (parent?.isParentEnterTransitionRunning == true) {
                    if (pendingAction != PENDING_ACTION_INPUT_CHANGED) pendingAction = PENDING_ACTION_CHANNEL_CHANGED
                    return
                }
                updateActions()
            }
        }

        override fun onAttach(context: Context) {
            val singletons = TvSingletons.getSingletons(context)
            inputManager = singletons.getTvInputManagerHelper()
            channelDataManager = singletons.getChannelDataManager()
            setupUtils = singletons.getSetupUtils()
            super.onAttach(context)
            buildInputs()
            inputManager.addCallback(inputCallback)
            channelDataManager.addListener(channelDataManagerListener)
            parent = parentFragment as SetupSourcesFragment?
        }

        override fun onDetach() {
            channelDataManager.removeListener(channelDataManagerListener)
            inputManager.removeCallback(inputCallback)
            super.onDetach()
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?) =
            Guidance(getString(R.string.setup_sources_text), getString(R.string.setup_sources_description2), null, null)

        override fun onCreateActionsStylist(): GuidedActionsStylist = SetupSourceGuidedActionsStylist()

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) = createActionsInternal(actions)

        /** Inputs sortieren (neu → nicht eingerichtet → Rest) und neue als bekannt markieren. */
        private fun buildInputs() {
            val oldInputs = if (inputsBuilt) inputs else null
            inputs = inputManager.getTvInputInfos(true, true).toMutableList()
            inputsBuilt = true
            if (oldInputs != null) {
                val newList = ArrayList(inputs).apply { removeAll(oldInputs.toSet()) }
                newlyAddedInputId = newList.firstOrNull()?.id?.takeIf { setupUtils.isNewInput(it) }
            }
            inputs.sortWith(TvInputNewComparator(setupUtils, inputManager))
            knownInputStartIndex = 0
            doneInputStartIndex = 0
            for (input in inputs) {
                if (setupUtils.isNewInput(input.id)) {
                    setupUtils.markAsKnownInput(input.id)
                    ++knownInputStartIndex
                }
                if (!setupUtils.isSetupDone(input.id)) ++doneInputStartIndex
            }
        }

        private fun updateActions() {
            val actions = ArrayList<GuidedAction>()
            createActionsInternal(actions)
            setActions(actions)
        }

        private fun createActionsInternal(actions: MutableList<GuidedAction>) {
            var newPosition = -1
            var position = 0
            if (doneInputStartIndex > 0) actions.add(createHeader(R.string.setup_category_new))
            for ((i, input) in inputs.withIndex()) {
                if (i == doneInputStartIndex) {
                    ++position
                    actions.add(createHeader(R.string.setup_category_done))
                }
                val inputId = input.id
                val channelCount = channelDataManager.getBrowsableChannelCountForInput(inputId)
                val description = when {
                    setupUtils.isSetupDone(inputId) || channelCount > 0 ->
                        if (channelCount == 0) getString(R.string.setup_input_no_channels)
                        else resources.getQuantityString(R.plurals.setup_input_channels, channelCount, channelCount)
                    i >= knownInputStartIndex -> getString(R.string.setup_input_setup_now)
                    else -> getString(R.string.setup_input_new)
                }
                ++position
                if (inputId == newlyAddedInputId) newPosition = position
                actions.add(GuidedAction.Builder(activity).id((ACTION_INPUT_START + i).toLong())
                    .title(input.loadLabel(requireActivity()).toString()).description(description).build())
            }
            if (inputs.isNotEmpty()) {
                ++position
                actions.add(GuidedActionsStylistWithDivider.createDividerAction(requireContext()))
            }
            if (OnboardingUtils.createOnlineStoreIntent() != null) {
                ++position
                actions.add(GuidedAction.Builder(activity).id(ACTION_ONLINE_STORE.toLong())
                    .title(getString(R.string.setup_store_action_title))
                    .description(getString(R.string.setup_store_action_description))
                    .icon(R.drawable.ic_app_store).build())
            }
            // Neu hinzugekommenen Input auswählen
            if (newPosition != -1) guidedActionsStylist.actionsGridView?.selectedPosition = newPosition
        }

        private fun createHeader(descriptionRes: Int) = GuidedAction.Builder(activity).id(ACTION_HEADER.toLong())
            .title(null).description(getString(descriptionRes)).focusable(false).infoOnly(true).build()

        override fun getActionCategory() = ACTION_CATEGORY

        override fun onGuidedActionClicked(action: GuidedAction) {
            if (action.id == ACTION_ONLINE_STORE.toLong()) {
                parent?.dispatchAction(ACTION_ONLINE_STORE)
                return
            }
            val index = action.id.toInt() - ACTION_INPUT_START
            if (index in inputs.indices) {
                parent?.dispatchAction(ACTION_SETUP_INPUT, Bundle().apply { putString(ACTION_PARAM_KEY_INPUT_ID, inputs[index].id) })
            }
        }

        internal fun executePendingAction() {
            when (pendingAction) {
                PENDING_ACTION_INPUT_CHANGED -> {
                    buildInputs()
                    updateActions()
                }
                PENDING_ACTION_CHANNEL_CHANGED -> updateActions()
            }
            pendingAction = PENDING_ACTION_NONE
        }

        /** Überschriften hervorgehoben, Input-Beschreibungen gedimmt. */
        private inner class SetupSourceGuidedActionsStylist : GuidedActionsStylistWithDivider() {
            override fun onBindViewHolder(vh: ViewHolder, action: GuidedAction) {
                super.onBindViewHolder(vh, action)
                vh.descriptionView?.let { d ->
                    if (action.id == ACTION_HEADER.toLong()) {
                        d.alpha = ALPHA_CATEGORY
                        d.setTextColor(resources.getColor(R.color.setup_category, null))
                        d.typeface = Typeface.create(getString(R.string.condensed_font), 0)
                    } else {
                        d.alpha = ALPHA_INPUT_DESCRIPTION
                        d.setTextColor(resources.getColor(R.color.common_setup_input_description, null))
                        d.typeface = Typeface.create(getString(R.string.font), 0)
                    }
                }
                setActionAccessibilityDelegate(vh, action)
            }
        }

        companion object {
            private const val ACTION_HEADER = 3
            private const val ACTION_INPUT_START = 4
            private const val PENDING_ACTION_NONE = 0
            private const val PENDING_ACTION_INPUT_CHANGED = 1
            private const val PENDING_ACTION_CHANNEL_CHANGED = 2
            private const val ALPHA_CATEGORY = 1.0f
            private const val ALPHA_INPUT_DESCRIPTION = 0.5f
        }
    }

    companion object {
        const val ACTION_CATEGORY = "com.android.tv.onboarding.SetupSourcesFragment"
        const val ACTION_ONLINE_STORE = 1
        const val ACTION_SETUP_INPUT = 2
        const val ACTION_PARAM_KEY_INPUT_ID = "input_id"
    }
}
