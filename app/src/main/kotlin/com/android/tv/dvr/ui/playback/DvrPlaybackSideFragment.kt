package com.android.tv.dvr.ui.playback

import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.text.TextUtils
import android.transition.Transition
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.util.TvSettings
import java.util.Locale

/** Seitenmenü der DVR-Wiedergabe für Untertitel- bzw. Tonspurwahl. */
class DvrPlaybackSideFragment : GuidedStepSupportFragment() {

    private lateinit var trackInfos: List<TvTrackInfo>
    private var selectedTrackId: String? = null
    private var selectedTrack: TvTrackInfo? = null
    private var trackType = 0
    private lateinit var overlayFragment: DvrPlaybackOverlayFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = requireArguments()
        trackInfos = BundleCompat.getParcelableArrayList(args, TRACK_INFOS, TvTrackInfo::class.java).orEmpty()
        trackType = trackInfos[0].type
        selectedTrackId = args.getString(SELECTED_TRACK_ID)
        overlayFragment = parentFragmentManager.findFragmentById(R.id.dvr_playback_controls_fragment) as DvrPlaybackOverlayFragment
        super.onCreate(savedInstanceState)
    }

    override fun onCreateBackgroundView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val backgroundView = super.onCreateBackgroundView(inflater, container, savedInstanceState)
        backgroundView?.setBackgroundColor(resources.getColor(R.color.lb_playback_controls_background_light, null))
        return backgroundView
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_ID_NO_SUBTITLE.toLong())
                    .title(getString(R.string.closed_caption_option_item_off))
                    .checkSetId(CHECK_SET_ID)
                    .checked(selectedTrackId == null)
                    .build(),
            )
        }
        for ((i, info) in trackInfos.withIndex()) {
            val checked = TextUtils.equals(info.id, selectedTrackId)
            val action = GuidedAction.Builder(requireContext())
                .id(i.toLong())
                .title(getTrackLabel(info, i))
                .checkSetId(CHECK_SET_ID)
                .checked(checked)
                .build()
            actions.add(action)
            if (checked) selectedTrack = info
        }
    }

    override fun onGuidedActionFocused(action: GuidedAction) {
        val actionId = action.id.toInt()
        overlayFragment.selectTrack(trackType, if (actionId < 0) null else trackInfos[actionId])
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val actionId = action.id.toInt()
        selectedTrack = if (actionId < 0) null else trackInfos[actionId]
        TvSettings.setDvrPlaybackTrackSettings(requireContext(), trackType, selectedTrack)
        parentFragmentManager.popBackStack()
    }

    override fun onStart() {
        super.onStart()
        // Workaround: Blendet das Overlay aus, geht der Fokus verloren. Daher Ausblenden
        // abschalten, solange das Seitenmenü bedient wird.
        overlayFragment.setControlsOverlayAutoHideEnabled(false)
    }

    override fun onStop() {
        super.onStop()
        // Ausblenden wieder erlauben und die gewählte Spur anwenden
        overlayFragment.setControlsOverlayAutoHideEnabled(true)
        overlayFragment.selectTrack(trackType, selectedTrack)
    }

    private fun getTrackLabel(track: TvTrackInfo, trackIndex: Int): String {
        val language = track.language
        if (language != null) return Locale(language).displayName
        return if (track.type == TvTrackInfo.TYPE_SUBTITLE) {
            getString(R.string.closed_caption_unknown_language, trackIndex + 1)
        } else {
            getString(R.string.multi_audio_unknown_language)
        }
    }

    override fun onProvideFragmentTransitions() {
        super.onProvideFragmentTransitions()
        // Hintergrund-Scrim aus der Transition nehmen, sonst flackert es beim gleichzeitigen
        // Ausblenden des Overlays und Einschieben des Seitenmenüs.
        (enterTransition as? Transition)?.excludeTarget(R.id.guidedstep_background, true)
    }

    companion object {
        /** Schlüssel für die übergebenen Spuren. */
        const val TRACK_INFOS = "dvr_key_track_infos"
        /** Schlüssel für die ID der gewählten Spur. */
        const val SELECTED_TRACK_ID = "dvr_key_selected_track_id"
        private const val ACTION_ID_NO_SUBTITLE = -1
        private const val CHECK_SET_ID = 1
    }
}
