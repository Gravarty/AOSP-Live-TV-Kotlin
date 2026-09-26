package com.android.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.DetailsOverviewRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.SparseArrayObjectAdapter
import androidx.leanback.widget.VerticalGridView
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Channel
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.dvr.ui.browse.ActionPresenterSelector
import com.android.tv.dvr.ui.browse.DetailsContent
import com.android.tv.dvr.ui.browse.DetailsContentPresenter
import com.android.tv.dvr.ui.browse.DetailsViewBackgroundHelper
import com.android.tv.util.images.ImageLoader

/**
 * Details einer (zukünftigen) Sendung mit Aufnahme-Aktionen.
 * Bugfix: Ohne DVR (Nicht-System-App) stürzte das Original ab (DvrManager/ScheduleManager null) –
 * jetzt werden die DVR-Teile dann einfach übersprungen.
 */
class ProgramDetailsFragment : DetailsSupportFragment(),
    DvrDataManager.ScheduledRecordingListener, DvrScheduleManager.OnConflictStateChangeListener {

    private lateinit var backgroundHelper: DetailsViewBackgroundHelper
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var detailsOverview: DetailsOverviewRow
    private lateinit var program: ProgramImpl
    private lateinit var inputId: String
    private var scheduledRecording: ScheduledRecording? = null
    private var dvrManager: DvrManager? = null
    private var dvrDataManager: DvrDataManager? = null
    private var dvrScheduleManager: DvrScheduleManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!onLoadDetails(requireArguments())) requireActivity().finish()
    }

    override fun onDestroy() {
        dvrDataManager?.removeScheduledRecordingListener(this)
        dvrScheduleManager?.removeOnConflictStateChangeListener(this)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        requireActivity().findViewById<VerticalGridView>(R.id.container_list)?.apply {
            itemAlignmentOffset = 0
            windowAlignmentOffset = resources.getDimensionPixelSize(R.dimen.lb_details_rows_align_top)
        }
    }

    private fun setupAdapter() {
        val rowPresenter = DetailsOverviewRowPresenter(DetailsContentPresenter(requireActivity())).apply {
            backgroundColor = resources.getColor(R.color.common_tv_background, null)
            setSharedElementEnterTransition(requireActivity(), DetailsActivity.SHARED_ELEMENT_NAME)
            onActionClickedListener = onCreateOnActionClickedListener()
        }
        rowsAdapter = ArrayObjectAdapter(onCreatePresenterSelector(rowPresenter))
        adapter = rowsAdapter
    }

    private fun setDetailsOverviewRow(detailsContent: DetailsContent) {
        detailsOverview = DetailsOverviewRow(detailsContent)
        detailsOverview.actionsAdapter = onCreateActionsAdapter()
        rowsAdapter.add(detailsOverview)
        onLoadLogoAndBackgroundImages(detailsContent)
    }

    private fun onCreatePresenterSelector(rowPresenter: DetailsOverviewRowPresenter): PresenterSelector =
        ClassPresenterSelector().apply { addClassPresenter(DetailsOverviewRow::class.java, rowPresenter) }

    private fun updateActions() {
        detailsOverview.actionsAdapter = onCreateActionsAdapter()
    }

    private fun onLoadDetails(args: Bundle): Boolean {
        val program = BundleCompat.getParcelable(args, DetailsActivity.PROGRAM, ProgramImpl::class.java)
        val channelId = args.getLong(DetailsActivity.CHANNEL_ID)
        val inputId = args.getString(DetailsActivity.INPUT_ID)
        if (program == null || channelId == Channel.INVALID_ID || inputId.isNullOrEmpty()) return false
        this.program = program
        this.inputId = inputId
        val singletons = TvSingletons.getSingletons(requireContext())
        dvrManager = singletons.getDvrManager()
        if (dvrManager != null) {
            dvrDataManager = singletons.getDvrDataManager()
            dvrScheduleManager = singletons.getDvrScheduleManager()
            scheduledRecording = dvrDataManager?.getScheduledRecordingForProgramId(program.id)
        }
        backgroundHelper = DetailsViewBackgroundHelper(requireActivity())
        setupAdapter()
        setDetailsOverviewRow(DetailsContent.createFromProgram(requireContext(), program))
        dvrDataManager?.addScheduledRecordingListener(this)
        dvrScheduleManager?.addOnConflictStateChangeListener(this)
        return true
    }

    private fun getScheduleIconId(): Int =
        if (dvrManager?.isConflicting(scheduledRecording) == true) R.drawable.ic_warning_white_32dp else R.drawable.ic_schedule_32dp

    private fun onCreateActionsAdapter(): SparseArrayObjectAdapter {
        val adapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        val res = resources
        val manager = dvrManager
        if (scheduledRecording != null) {
            adapter.set(ACTION_VIEW_SCHEDULE, Action(ACTION_VIEW_SCHEDULE.toLong(),
                res.getString(R.string.dvr_detail_view_schedule), null, res.getDrawable(getScheduleIconId(), null)))
            adapter.set(ACTION_CANCEL, Action(ACTION_CANCEL.toLong(),
                res.getString(R.string.dvr_detail_cancel_recording), null, res.getDrawable(R.drawable.ic_dvr_cancel_32dp, null)))
        } else if (manager != null && manager.isProgramRecordable(program)) {
            adapter.set(ACTION_SCHEDULE_RECORDING, Action(ACTION_SCHEDULE_RECORDING.toLong(),
                res.getString(R.string.dvr_detail_schedule_recording), null, res.getDrawable(R.drawable.ic_schedule_32dp, null)))
        }
        return adapter
    }

    private fun onCreateOnActionClickedListener() = OnActionClickedListener { action ->
        when (action.id.toInt()) {
            ACTION_VIEW_SCHEDULE -> DvrUiHelper.startSchedulesActivity(requireContext(), scheduledRecording)
            ACTION_CANCEL -> scheduledRecording?.let { dvrManager?.removeScheduledRecording(it) }
            ACTION_SCHEDULE_RECORDING ->
                // DvrFlags.startEarlyEndLateEnabled() ist im AOSP-Build false
                DvrUiHelper.checkStorageStatusAndShowErrorMessage(requireActivity(), inputId) {
                    DvrUiHelper.requestRecordingFutureProgram(requireActivity(), program, false)
                }
        }
    }

    /** Logo und Hintergrund laden; gleiche URI nur einmal laden. */
    private fun onLoadLogoAndBackgroundImages(detailsContent: DetailsContent) {
        var logoDrawable: Drawable? = null
        var backgroundDrawable: Drawable? = null
        if (detailsContent.logoImageUri.isNullOrEmpty()) {
            logoDrawable = resources.getDrawable(R.drawable.dvr_default_poster, null)
            detailsOverview.imageDrawable = logoDrawable
        }
        if (detailsContent.backgroundImageUri.isNullOrEmpty()) {
            backgroundDrawable = resources.getDrawable(R.drawable.dvr_default_poster, null)
            backgroundHelper.setBackground(backgroundDrawable)
        }
        if (logoDrawable != null && backgroundDrawable != null) return
        val context = requireContext()
        if (logoDrawable == null && backgroundDrawable == null &&
            detailsContent.logoImageUri == detailsContent.backgroundImageUri
        ) {
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri,
                callback = MyImageLoaderCallback(this, LOAD_LOGO_IMAGE or LOAD_BACKGROUND_IMAGE, context))
            return
        }
        if (logoDrawable == null) {
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri,
                resources.getDimensionPixelSize(R.dimen.dvr_details_poster_width),
                resources.getDimensionPixelSize(R.dimen.dvr_details_poster_height),
                MyImageLoaderCallback(this, LOAD_LOGO_IMAGE, context))
        }
        if (backgroundDrawable == null) {
            ImageLoader.loadBitmap(context, detailsContent.backgroundImageUri,
                callback = MyImageLoaderCallback(this, LOAD_BACKGROUND_IMAGE, context))
        }
    }

    override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
        scheduledRecordings.firstOrNull { it.programId == program.id }?.let {
            scheduledRecording = it
            updateActions()
        }
    }

    override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
        val current = scheduledRecording ?: return
        if (scheduledRecordings.any { it.id == current.id }) {
            scheduledRecording = null
            updateActions()
        }
    }

    override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
        val current = scheduledRecording ?: return
        scheduledRecordings.firstOrNull { it.id == current.id }?.let {
            scheduledRecording = it
            updateActions()
        }
    }

    override fun onConflictStateChange(conflict: Boolean, vararg scheduledRecordings: ScheduledRecording) =
        onScheduledRecordingStatusChanged(*scheduledRecordings)

    private class MyImageLoaderCallback(fragment: ProgramDetailsFragment, private val loadType: Int, private val context: Context) :
        ImageLoader.ImageLoaderCallback<ProgramDetailsFragment>(fragment) {
        override fun onBitmapLoaded(referent: ProgramDetailsFragment, bitmap: Bitmap?) {
            var type = loadType
            val res = context.resources
            val drawable: Drawable
            if (bitmap == null) {
                drawable = res.getDrawable(R.drawable.dvr_default_poster, null)
                if (type and LOAD_BACKGROUND_IMAGE != 0 && !referent.isDetached) {
                    type = type and LOAD_BACKGROUND_IMAGE.inv()
                    referent.backgroundHelper.setBackgroundColor(res.getColor(R.color.dvr_detail_default_background, null))
                    referent.backgroundHelper.setScrim(res.getColor(R.color.dvr_detail_default_background_scrim, null))
                }
            } else {
                drawable = BitmapDrawable(res, bitmap)
            }
            if (!referent.isDetached) {
                if (type and LOAD_LOGO_IMAGE != 0) referent.detailsOverview.imageDrawable = drawable
                if (type and LOAD_BACKGROUND_IMAGE != 0) referent.backgroundHelper.setBackground(drawable)
            }
        }
    }

    companion object {
        private const val LOAD_LOGO_IMAGE = 1
        private const val LOAD_BACKGROUND_IMAGE = 2
        private const val ACTION_VIEW_SCHEDULE = 1
        private const val ACTION_CANCEL = 2
        private const val ACTION_SCHEDULE_RECORDING = 3
    }
}
