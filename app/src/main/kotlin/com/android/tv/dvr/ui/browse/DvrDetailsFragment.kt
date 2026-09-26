package com.android.tv.dvr.ui.browse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvContentRating
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.leanback.app.DetailsSupportFragment
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
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.CommonUtils
import com.android.tv.dialog.PinDialogFragment
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.ui.DetailsActivity
import com.android.tv.util.ToastUtils
import com.android.tv.util.images.ImageLoader
import java.io.File

/** Basis der DVR-Detailansichten. Leanback DetailsFragment → DetailsSupportFragment (AndroidX). */
abstract class DvrDetailsFragment : DetailsSupportFragment() {
    protected lateinit var backgroundHelper: DetailsViewBackgroundHelper
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var detailsOverview: DetailsOverviewRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bugfix: ohne Argumente nicht abstürzen, sondern wie bei ungültigen Aufnahmen beenden.
        val args = arguments
        if (args == null || !onLoadRecordingDetails(args)) {
            requireActivity().finish()
            return
        }
        backgroundHelper = DetailsViewBackgroundHelper(requireActivity())
        setupAdapter()
        onCreateInternal()
    }

    override fun onStart() {
        super.onStart()
        // TODO: Workaround für b/30401180 entfernen (aus dem Original).
        // Offset manuell anpassen, siehe DetailsFragment.setVerticalGridViewLayout.
        // Bugfix: null-sicher (z. B. wenn onCreate früh beendet hat).
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

    /** Adapter der Zeilen der Detailansicht. */
    protected fun getRowsAdapter(): ArrayObjectAdapter = rowsAdapter

    /** Setzt die Übersichtszeile. */
    protected fun setDetailsOverviewRow(detailsContent: DetailsContent) {
        detailsOverview = DetailsOverviewRow(detailsContent)
        detailsOverview.actionsAdapter = onCreateActionsAdapter()
        rowsAdapter.add(detailsOverview)
        onLoadLogoAndBackgroundImages(detailsContent)
    }

    /** Erzeugt den PresenterSelector des Zeilen-Adapters. */
    protected open fun onCreatePresenterSelector(rowPresenter: DetailsOverviewRowPresenter): PresenterSelector =
        ClassPresenterSelector().apply { addClassPresenter(DetailsOverviewRow::class.java, rowPresenter) }

    /**
     * Unterklassen-spezifische Initialisierung. Da [onCreate] die Activity früh beenden kann,
     * gehört alles, was nach super.onCreate() passieren muss, hierher.
     */
    protected open fun onCreateInternal() {}

    /** Aktualisiert die Aktionen der Übersicht. */
    protected open fun updateActions() {
        detailsOverview.actionsAdapter = onCreateActionsAdapter()
    }

    /**
     * Lädt die Details anhand der Argumente.
     *
     * @return false, wenn keine gültige Aufnahme gefunden wurde (Activity wird dann beendet).
     */
    protected abstract fun onLoadRecordingDetails(args: Bundle): Boolean

    /** Erzeugt die Aktionen und ihren Adapter. */
    protected abstract fun onCreateActionsAdapter(): SparseArrayObjectAdapter

    /** Erzeugt den Listener für Klicks auf die Aktionen. */
    protected abstract fun onCreateOnActionClickedListener(): OnActionClickedListener

    /** Lädt Logo und Hintergrund der Detailansicht. */
    protected open fun onLoadLogoAndBackgroundImages(detailsContent: DetailsContent) {
        val context = requireContext()
        var logoDrawable: Drawable? = null
        var backgroundDrawable: Drawable? = null
        if (TextUtils.isEmpty(detailsContent.logoImageUri)) {
            logoDrawable = context.resources.getDrawable(R.drawable.dvr_default_poster, null)
            detailsOverview.imageDrawable = logoDrawable
        }
        if (TextUtils.isEmpty(detailsContent.backgroundImageUri)) {
            backgroundDrawable = context.resources.getDrawable(R.drawable.dvr_default_poster, null)
            backgroundHelper.setBackground(backgroundDrawable)
        }
        if (logoDrawable != null && backgroundDrawable != null) return
        if (logoDrawable == null && backgroundDrawable == null &&
            detailsContent.logoImageUri == detailsContent.backgroundImageUri
        ) {
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri,
                callback = MyImageLoaderCallback(this, LOAD_LOGO_IMAGE or LOAD_BACKGROUND_IMAGE, context))
            return
        }
        if (logoDrawable == null) {
            val imageWidth = resources.getDimensionPixelSize(R.dimen.dvr_details_poster_width)
            val imageHeight = resources.getDimensionPixelSize(R.dimen.dvr_details_poster_height)
            ImageLoader.loadBitmap(context, detailsContent.logoImageUri, imageWidth, imageHeight,
                MyImageLoaderCallback(this, LOAD_LOGO_IMAGE, context))
        }
        if (backgroundDrawable == null) {
            ImageLoader.loadBitmap(context, detailsContent.backgroundImageUri,
                callback = MyImageLoaderCallback(this, LOAD_BACKGROUND_IMAGE, context))
        }
    }

    protected fun startPlayback(recordedProgram: RecordedProgram, seekTimeMs: Long) {
        val context = requireContext()
        if (CommonUtils.isInBundledPackageSet(recordedProgram.packageName) &&
            !isDataUriAccessible(recordedProgram.dataUri)
        ) {
            // Das Aufräumen vergessener Speicher dauert; bis dahin keine Wiedergabe.
            ToastUtils.show(context, context.resources.getString(R.string.dvr_toast_recording_deleted), Toast.LENGTH_SHORT)
            return
        }
        val programId = recordedProgram.id
        val singletons = TvSingletons.getSingletons(context)
        // ParentalControlSettings entfällt → Kindersicherung über die öffentliche TvInputManager-API.
        if (!singletons.getTvInputManagerHelper().isParentalControlsEnabled()) {
            DvrUiHelper.startPlaybackActivity(context, programId, seekTimeMs, false)
            return
        }
        val channel = singletons.getChannelDataManager().getChannel(recordedProgram.channelId)
        if (channel != null && channel.isLocked) {
            checkPinToPlay(recordedProgram, seekTimeMs)
            return
        }
        if (isRatingBlocked(context, recordedProgram.contentRatings)) {
            checkPinToPlay(recordedProgram, seekTimeMs)
        } else {
            DvrUiHelper.startPlaybackActivity(context, programId, seekTimeMs, false)
        }
    }

    /** Ersatz für ParentalControlSettings.getBlockedRating(): öffentliche API TvInputManager.isRatingBlocked(). */
    private fun isRatingBlocked(context: Context, ratings: List<TvContentRating>): Boolean {
        val tvInputManager = context.getSystemService(TvInputManager::class.java) ?: return false
        return ratings.any {
            try {
                tvInputManager.isRatingBlocked(it)
            } catch (e: IllegalArgumentException) {
                false // Ungültige Freigabe
            }
        }
    }

    private fun isDataUriAccessible(dataUri: Uri?): Boolean {
        val path = dataUri?.path ?: return false
        try {
            if (File(path).exists()) return true
        } catch (e: SecurityException) {
            // Kein Zugriff
        }
        return false
    }

    private fun checkPinToPlay(recordedProgram: RecordedProgram, seekTimeMs: Long) {
        val activity = activity
        SoftPreconditions.checkState(activity is DetailsActivity, TAG, "Activity is not DetailsActivity")
        if (activity is DetailsActivity) {
            activity.setOnPinCheckListener { checked, type, _ ->
                activity.setOnPinCheckListener(null)
                if (checked && type == PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM) {
                    DvrUiHelper.startPlaybackActivity(activity, recordedProgram.id, seekTimeMs, true)
                }
            }
            PinDialogFragment.create(PinDialogFragment.PIN_DIALOG_TYPE_UNLOCK_PROGRAM)
                .show(activity.supportFragmentManager, PinDialogFragment.DIALOG_TAG)
        }
    }

    private class MyImageLoaderCallback(
        fragment: DvrDetailsFragment,
        private val loadType: Int,
        private val context: Context,
    ) : ImageLoader.ImageLoaderCallback<DvrDetailsFragment>(fragment) {
        override fun onBitmapLoaded(referent: DvrDetailsFragment, bitmap: Bitmap?) {
            val drawable: Drawable
            var loadType = loadType
            if (bitmap == null) {
                val res = context.resources
                drawable = res.getDrawable(R.drawable.dvr_default_poster, null)
                if (loadType and LOAD_BACKGROUND_IMAGE != 0 && !referent.isDetached) {
                    loadType = loadType and LOAD_BACKGROUND_IMAGE.inv()
                    referent.backgroundHelper.setBackgroundColor(res.getColor(R.color.dvr_detail_default_background, null))
                    referent.backgroundHelper.setScrim(res.getColor(R.color.dvr_detail_default_background_scrim, null))
                }
            } else {
                drawable = BitmapDrawable(context.resources, bitmap)
            }
            if (!referent.isDetached) {
                if (loadType and LOAD_LOGO_IMAGE != 0) referent.detailsOverview.imageDrawable = drawable
                if (loadType and LOAD_BACKGROUND_IMAGE != 0) referent.backgroundHelper.setBackground(drawable)
            }
        }
    }

    companion object {
        private const val TAG = "DvrDetailsFragment"
        private const val LOAD_LOGO_IMAGE = 1
        private const val LOAD_BACKGROUND_IMAGE = 2
    }
}
