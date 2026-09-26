package com.android.tv.dvr.ui.playback

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.session.PlaybackState
import android.media.tv.TvContentRating
import android.media.tv.TvInputManager
import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.PlaybackSupportFragment
import androidx.leanback.app.PlaybackSupportFragmentGlueHost
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseOnItemViewClickedListener
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.SinglePresenterSelector
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.audio.AudioManagerHelper
import com.android.tv.data.api.BaseProgram
import com.android.tv.dialog.PinDialogFragment
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.ui.SortedArrayAdapter
import com.android.tv.dvr.ui.browse.DvrListRowPresenter
import com.android.tv.dvr.ui.browse.RecordingCardView
import com.android.tv.ui.AppLayerTvView
import com.android.tv.util.TvSettings
import com.android.tv.util.TvTrackInfoUtils
import com.android.tv.util.Utils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Steuer-Overlay der DVR-Wiedergabe (PlaybackSupportFragment) mit Zeile ähnlicher Aufnahmen.
 * DvrDataManager kommt aus TvSingletons statt per Injection. Entwickleroptionen/BuildType
 * entfallen: die Surface ist immer sicher.
 */
class DvrPlaybackOverlayFragment : PlaybackSupportFragment() {
    // TODO: Audiofokus sowie Sperren/Altersfreigaben behandeln.

    // Nur für die Aufnahme aus dem Intent; sonst nicht verwenden.
    private var program: RecordedProgram? = null
    private lateinit var dvrPlayer: DvrPlayer
    private lateinit var mediaSessionHelper: DvrPlaybackMediaSessionHelper
    private lateinit var playbackControlHelper: DvrPlaybackControlHelper
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var relatedRecordingsRowAdapter: SortedArrayAdapter<BaseProgram>
    private lateinit var relatedRecordingCardPresenter: DvrPlaybackCardPresenter
    private lateinit var audioManagerHelper: AudioManagerHelper
    private lateinit var tvView: AppLayerTvView
    private lateinit var blockScreenView: View
    private lateinit var relatedRecordingsRow: ListRow
    private var verticalPaddingBase = 0
    private var paddingWithoutRelatedRow = 0
    private var paddingWithoutSecondaryRow = 0
    private var windowWidth = 0
    private var windowHeight = 0
    private var appliedAspectRatio = 0f
    private var windowAspectRatio = 0f
    private var pinChecked = false
    private var started = false
    private val onSubtitleTrackSelectedListener = DvrPlayer.OnTrackSelectedListener { selectedTrackId ->
        playbackControlHelper.onSubtitleTrackStateChanged(selectedTrackId != null)
        rowsAdapter.notifyArrayItemRangeChanged(0, 1)
    }

    private lateinit var dvrDataManager: DvrDataManager

    override fun onAttach(context: Context) {
        if (DEBUG) Log.d(TAG, "onAttach")
        dvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        val res = requireActivity().resources
        verticalPaddingBase = res.getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_base)
        paddingWithoutRelatedRow = res.getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_no_related_row)
        paddingWithoutSecondaryRow = res.getDimensionPixelOffset(R.dimen.dvr_playback_overlay_padding_top_no_secondary_row)
        if (!dvrDataManager.isRecordedProgramLoadFinished) {
            dvrDataManager.addRecordedProgramLoadFinishedListener(object : DvrDataManager.OnRecordedProgramLoadFinishedListener {
                override fun onRecordedProgramLoadFinished() {
                    dvrDataManager.removeRecordedProgramLoadFinishedListener(this)
                    val activity = activity ?: return // Absicherung: Fragment bereits gelöst
                    if (handleIntent(activity.intent, true)) {
                        setUpRows()
                        preparePlayback(activity.intent)
                    }
                }
            })
        } else if (!handleIntent(requireActivity().intent, true)) {
            return
        }
        val size = Point()
        @Suppress("DEPRECATION") // Display.getSize wie im Original
        (requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
            .getSize(size)
        windowWidth = size.x
        windowHeight = size.y
        windowAspectRatio = windowWidth.toFloat() / windowHeight
        appliedAspectRatio = windowAspectRatio
        backgroundType = BG_LIGHT
        // setControlsOverlayAutoHideEnabled ersetzt das veraltete setFadingEnabled
        setControlsOverlayAutoHideEnabled(true)
    }

    override fun onStart() {
        super.onStart()
        started = true
        updateVerticalPosition()
    }

    // onActivityCreated bleibt: Die Views hinter dem <fragment>-Tag (block_screen) existieren erst
    // nach dem Inflaten des ganzen Activity-Layouts.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = requireActivity()
        tvView = activity.findViewById(R.id.dvr_tv_view)
        // Release-Build ohne Entwickler-Features: immer sichere Surface
        tvView.setUseSecureSurface(true)
        blockScreenView = activity.findViewById(R.id.block_screen)
        dvrPlayer = DvrPlayer(tvView, activity)
        mediaSessionHelper = DvrPlaybackMediaSessionHelper(activity, MEDIA_SESSION_TAG, dvrPlayer, this)
        playbackControlHelper = DvrPlaybackControlHelper(activity, this)
        relatedRecordingsRow = createRelatedRecordingsRow()
        dvrPlayer.setOnTracksAvailabilityChangedListener { hasClosedCaption, hasMultiAudio ->
            playbackControlHelper.updateSecondaryRow(hasClosedCaption, hasMultiAudio)
            if (hasClosedCaption) {
                dvrPlayer.setOnTrackSelectedListener(TvTrackInfo.TYPE_SUBTITLE, onSubtitleTrackSelectedListener)
                selectBestMatchedTrack(TvTrackInfo.TYPE_SUBTITLE)
            } else {
                dvrPlayer.setOnTrackSelectedListener(TvTrackInfo.TYPE_SUBTITLE, null)
            }
            if (hasMultiAudio) selectBestMatchedTrack(TvTrackInfo.TYPE_AUDIO)
            updateVerticalPosition()
            playbackControlHelper.host?.notifyPlaybackRowChanged()
        }
        dvrPlayer.setOnAspectRatioChangedListener { videoAspectRatio -> updateAspectRatio(videoAspectRatio) }
        pinChecked = activity.intent.getBooleanExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED, false)
        dvrPlayer.setOnContentBlockedListener { contentRating -> onContentBlocked(contentRating) }
        setOnItemViewClickedListener(BaseOnItemViewClickedListener<Any> { itemViewHolder, _, _, _ ->
            if (itemViewHolder.view is RecordingCardView) {
                setControlsOverlayAutoHideEnabled(false)
                val programId = (itemViewHolder.view.tag as RecordedProgram).id
                if (DEBUG) Log.d(TAG, "Play Related Recording:$programId")
                val intent = Intent(requireContext(), DvrPlaybackActivity::class.java)
                    .putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, programId)
                requireContext().startActivity(intent)
            }
        })
        audioManagerHelper = AudioManagerHelper(activity, dvrPlayer.view)
        if (program != null) {
            setUpRows()
            preparePlayback(activity.intent)
        }
    }

    private fun onContentBlocked(contentRating: TvContentRating) {
        if (pinChecked) {
            tvView.unblockContentCompat(contentRating)
            return
        }
        blockScreenView.visibility = View.VISIBLE
        requireActivity().mediaController.transportControls.pause()
        val playbackActivity = requireActivity() as DvrPlaybackActivity
        playbackActivity.setOnPinCheckListener { checked, _, _ ->
            playbackActivity.setOnPinCheckListener(null)
            if (checked) {
                pinChecked = true
                tvView.unblockContentCompat(contentRating)
                blockScreenView.visibility = View.GONE
                playbackActivity.mediaController.transportControls.play()
            }
        }
        PinDialogFragment.create(PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_DVR, contentRating.flattenToString())
            .show(playbackActivity.supportFragmentManager, PinDialogFragment.DIALOG_TAG)
    }

    override fun onPause() {
        if (DEBUG) Log.d(TAG, "onPause")
        super.onPause()
        if (mediaSessionHelper.playbackState == PlaybackState.STATE_FAST_FORWARDING ||
            mediaSessionHelper.playbackState == PlaybackState.STATE_REWINDING
        ) {
            requireActivity().mediaController.transportControls.pause()
        }
        // requestVisibleBehind() entfällt (seit Android 8 wirkungslos, wie in MainActivity)
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        // Bugfix: Wird die Activity schon in onCreate beendet (Aufnahme fehlt), läuft
        // onActivityCreated nie; dann gibt es nichts freizugeben.
        if (::playbackControlHelper.isInitialized) {
            playbackControlHelper.unregisterCallback()
            mediaSessionHelper.release()
            audioManagerHelper.abandonAudioFocus()
            relatedRecordingCardPresenter.unbindAllViewHolders()
            dvrPlayer.release()
        }
        super.onDestroy()
    }

    /** Neues Intent an das Fragment weiterreichen. */
    fun onNewIntent(intent: Intent) {
        if (dvrDataManager.isRecordedProgramLoadFinished && handleIntent(intent, false)) {
            preparePlayback(intent)
        }
    }

    /** Bei geänderter Fenstergröße aufrufen, damit Größe/Position der TvView angepasst werden. */
    fun onWindowSizeChanged(windowWidth: Int, windowHeight: Int) {
        this.windowWidth = windowWidth
        this.windowHeight = windowHeight
        windowAspectRatio = windowWidth.toFloat() / windowHeight
        updateAspectRatio(appliedAspectRatio)
    }

    /** Nächste Folge derselben Serie nach [program] oder null. */
    fun getNextEpisode(program: RecordedProgram): RecordedProgram? {
        val position = relatedRecordingsRowAdapter.findInsertPosition(program)
        return if (position == relatedRecordingsRowAdapter.size()) {
            null
        } else {
            relatedRecordingsRowAdapter.get(position) as RecordedProgram
        }
    }

    /** Spuren des Typs ([TvTrackInfo.TYPE_SUBTITLE] oder [TvTrackInfo.TYPE_AUDIO]), sonst null. */
    fun getTracks(trackType: Int): ArrayList<TvTrackInfo>? = when (trackType) {
        TvTrackInfo.TYPE_AUDIO -> dvrPlayer.audioTracks
        TvTrackInfo.TYPE_SUBTITLE -> dvrPlayer.subtitleTracks
        else -> null
    }

    /** ID der gewählten Spur des Typs. */
    fun getSelectedTrackId(trackType: Int): String? = dvrPlayer.getSelectedTrackId(trackType)

    /** Gespeicherte Spur-Einstellung des Typs oder null. */
    internal fun getTrackSetting(trackType: Int): TvTrackInfo? =
        TvSettings.getDvrPlaybackTrackSettings(requireContext(), trackType)

    /** Ton- oder Untertitelspur wählen; null schaltet sie ab. */
    internal fun selectTrack(trackType: Int, selectedTrack: TvTrackInfo?) {
        if (dvrPlayer.isPlaybackPrepared()) dvrPlayer.selectTrack(trackType, selectedTrack)
    }

    private fun handleIntent(intent: Intent, finishActivity: Boolean): Boolean {
        program = getProgramFromIntent(intent)
        if (program == null) {
            Toast.makeText(requireActivity(), getString(R.string.dvr_program_not_found), Toast.LENGTH_SHORT).show()
            if (finishActivity) requireActivity().finish()
            return false
        }
        return true
    }

    private fun selectBestMatchedTrack(trackType: Int) {
        val selectedTrack = getTrackSetting(trackType)
        if (selectedTrack != null) {
            val bestMatchedTrack = TvTrackInfoUtils.getBestTrackInfo(
                getTracks(trackType),
                selectedTrack.id,
                selectedTrack.language,
                if (trackType == TvTrackInfo.TYPE_AUDIO) selectedTrack.audioChannelCount else 0,
            )
            if (bestMatchedTrack != null &&
                (trackType == TvTrackInfo.TYPE_AUDIO || Utils.isEqualLanguage(bestMatchedTrack.language, selectedTrack.language))
            ) {
                selectTrack(trackType, bestMatchedTrack)
                return
            }
        }
        if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            // Ohne passende Sprache Untertitel abschalten
            selectTrack(TvTrackInfo.TYPE_SUBTITLE, null)
        }
    }

    private fun updateAspectRatio(aspectRatio: Float) {
        var videoAspectRatio = aspectRatio
        if (videoAspectRatio <= 0) {
            // Videogröße unbekannt: Seitenverhältnis des Fensters verwenden
            videoAspectRatio = windowAspectRatio
        }
        if (abs(appliedAspectRatio - videoAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON) return
        val parent = tvView.parent as ViewGroup
        if (abs(windowAspectRatio - videoAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON) {
            parent.setPadding(0, 0, 0, 0)
        } else if (videoAspectRatio < windowAspectRatio) {
            val newPadding = (windowWidth - (windowHeight * videoAspectRatio).roundToInt()) / 2
            parent.setPadding(newPadding, 0, newPadding, 0)
        } else {
            val newPadding = (windowHeight - (windowWidth / videoAspectRatio).roundToInt()) / 2
            parent.setPadding(0, newPadding, 0, newPadding)
        }
        appliedAspectRatio = videoAspectRatio
    }

    private fun preparePlayback(intent: Intent) {
        mediaSessionHelper.setupPlayback(program, getSeekTimeFromIntent(intent))
        playbackControlHelper.updateSecondaryRow(false, false)
        audioManagerHelper.requestAudioFocus()
        requireActivity().mediaController.transportControls.prepare()
        updateRelatedRecordingsRow()
    }

    private fun updateRelatedRecordingsRow() {
        val wasEmpty = relatedRecordingsRowAdapter.size() == 0
        relatedRecordingsRowAdapter.clear()
        val program = program ?: return
        val programId = program.id
        val seriesId = program.seriesId
        val seriesRecording = seriesId?.let { dvrDataManager.getSeriesRecording(it) }
        if (seriesRecording != null) {
            if (DEBUG) Log.d(TAG, "Update related recordings with:$seriesId")
            for (relatedProgram in dvrDataManager.getRecordedPrograms(seriesRecording.id)) {
                if (programId != relatedProgram.id) relatedRecordingsRowAdapter.add(relatedProgram)
            }
        }
        if (relatedRecordingsRowAdapter.size() == 0) {
            rowsAdapter.remove(relatedRecordingsRow)
        } else if (wasEmpty) {
            rowsAdapter.add(relatedRecordingsRow)
        }
        updateVerticalPosition()
        rowsAdapter.notifyArrayItemRangeChanged(1, 1)
    }

    private fun setUpRows() {
        playbackControlHelper.createControlsRow()
        playbackControlHelper.host = PlaybackSupportFragmentGlueHost(this)
        rowsAdapter = adapter as ArrayObjectAdapter
        val selector = rowsAdapter.presenterSelector as ClassPresenterSelector
        selector.addClassPresenter(ListRow::class.java, DvrListRowPresenter(requireContext()))
        rowsAdapter.presenterSelector = selector
        if (started) {
            // Vor dem Aufbau der Zeilen gestartet: Position hier nachholen
            updateVerticalPosition()
        }
    }

    private fun createRelatedRecordingsRow(): ListRow {
        relatedRecordingCardPresenter = DvrPlaybackCardPresenter(requireActivity())
        relatedRecordingsRowAdapter = RelatedRecordingsAdapter(relatedRecordingCardPresenter)
        val header = HeaderItem(0, requireActivity().getString(R.string.dvr_playback_related_recordings))
        return ListRow(header, relatedRecordingsRowAdapter)
    }

    private fun getProgramFromIntent(intent: Intent): RecordedProgram? {
        val programId = intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, -1)
        return dvrDataManager.getRecordedProgram(programId)
    }

    private fun getSeekTimeFromIntent(intent: Intent): Long =
        intent.getLongExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME, TvInputManager.TIME_SHIFT_INVALID_TIME)

    private fun updateVerticalPosition() {
        // Absicherung: onStart kann vor onActivityCreated-Ende bzw. ohne Helper laufen
        if (!::playbackControlHelper.isInitialized) return
        val hasSecondaryRow = playbackControlHelper.hasSecondaryRow() ?: return
        var verticalPadding = verticalPaddingBase
        if (relatedRecordingsRowAdapter.size() == 0) verticalPadding += paddingWithoutRelatedRow
        if (!hasSecondaryRow) verticalPadding += paddingWithoutSecondaryRow
        val fragment = childFragmentManager.findFragmentById(R.id.playback_controls_dock)
        fragment?.view?.translationY = verticalPadding.toFloat()
    }

    fun onPlaybackResume() {
        playbackControlHelper.onPlaybackResume()
    }

    fun getProgramStartTimeMs(): Long {
        val program = program
        return if (program != null && program.isPartial) program.startTimeUtcMillis else INVALID_TIME
    }

    fun updateProgress() {
        playbackControlHelper.updateProgress()
    }

    private class RelatedRecordingsAdapter(presenter: DvrPlaybackCardPresenter) :
        SortedArrayAdapter<BaseProgram>(SinglePresenterSelector(presenter), BaseProgram.EPISODE_COMPARATOR) {
        override fun getId(item: BaseProgram): Long = item.id
    }

    companion object {
        private const val TAG = "DvrPlaybackOverlayFrag"
        private const val DEBUG = false
        private const val MEDIA_SESSION_TAG = "com.android.tv.dvr.mediasession"
        private const val DISPLAY_ASPECT_RATIO_EPSILON = 0.01f
        private const val INVALID_TIME = -1L
    }
}
