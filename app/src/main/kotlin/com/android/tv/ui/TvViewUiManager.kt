package com.android.tv.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Property
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import com.android.tv.R
import com.android.tv.TvOptionsManager
import com.android.tv.data.DisplayMode
import com.android.tv.features.TvFeatures
import com.android.tv.util.TvSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Port von TvViewUiManager: Bildformat (Normal/Voll/Zoom), verkleinerte TvView mit Animation,
 * Hintergrundfarbe und Ein-/Ausblenden. Fenstergröße über WindowMetrics statt Display.getSize().
 */
class TvViewUiManager(
    private val context: Context,
    private val tvView: TunableTvView,
    private val contentView: FrameLayout,
    private val tvOptionsManager: TvOptionsManager,
) {
    private val resources = context.resources
    private val tvViewShrunkenStartMargin = resources.getDimensionPixelOffset(R.dimen.shrunken_tvview_margin_start)
    private val tvViewShrunkenEndMargin = resources.getDimensionPixelOffset(R.dimen.shrunken_tvview_margin_end) +
        resources.getDimensionPixelSize(R.dimen.side_panel_width)
    private var windowWidth: Int
    private var windowHeight: Int
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val linearOutSlowIn = AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in)
    private val fastOutLinearIn = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in)

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_SET_LAYOUT_PARAMS) {
            val layoutParams = msg.obj as FrameLayout.LayoutParams
            tvView.tvViewLayoutParams = layoutParams
            tvView.layoutParams = tvViewFrame
            // Nicht im Bild-in-Bild: feste Surface-Größe (verhindert Neuaufbau beim Zoomen)
            if (!TvFeatures.isPictureInPictureEnabled(context) || !(context as Activity).isInPictureInPictureMode) {
                tvView.setFixedSurfaceSize(layoutParams.width, layoutParams.height)
            }
        }
        true
    }

    private var displayMode = 0
    private var tvViewStartMarginBeforeShrunken = 0
    private var tvViewEndMarginBeforeShrunken = 0
    private var displayModeBeforeShrunken = 0
    var isUnderShrunkenTvView = false
        private set
    private var tvViewStartMargin = 0
    private var tvViewEndMargin = 0
    private var tvViewAnimator: ObjectAnimator? = null
    private var tvViewLayoutParams: FrameLayout.LayoutParams? = null
    // Rahmen der TunableTvView; die innere TvView liegt darin.
    private var tvViewFrame: FrameLayout.LayoutParams
    private var lastAnimatedTvViewFrame: FrameLayout.LayoutParams? = null
    private var oldTvViewFrame: FrameLayout.LayoutParams? = null
    private var backgroundAnimator: ObjectAnimator? = null
    private var backgroundColor = 0
    private var appliedDisplayedMode = DisplayMode.MODE_NOT_DEFINED
    private var appliedTvViewStartMargin = 0
    private var appliedTvViewEndMargin = 0
    private var appliedVideoDisplayAspectRatio = 0f

    init {
        val bounds = (context as Activity).windowManager.currentWindowMetrics.bounds
        windowWidth = bounds.width()
        windowHeight = bounds.height()
        tvViewFrame = createMarginLayoutParams(0, 0, 0, 0)
    }

    fun onConfigurationChanged(windowWidth: Int, windowHeight: Int) {
        if (windowWidth > 0 && windowHeight > 0 && (this.windowWidth != windowWidth || this.windowHeight != windowHeight)) {
            this.windowWidth = windowWidth
            this.windowHeight = windowHeight
            applyDisplayMode(tvView.videoDisplayAspectRatio, false, true)
        }
    }

    /** Animatoren vorab anlegen (Lazy-Init nach dem ersten Tunen). */
    fun initAnimatorIfNeeded() {
        initTvAnimatorIfNeeded()
        initBackgroundAnimatorIfNeeded()
    }

    fun startShrunkenTvView() {
        isUnderShrunkenTvView = true
        tvView.setIsUnderShrunken(true)
        tvViewStartMarginBeforeShrunken = tvViewStartMargin
        tvViewEndMarginBeforeShrunken = tvViewEndMargin
        setTvViewMargin(tvViewShrunkenStartMargin, tvViewShrunkenEndMargin)
        displayModeBeforeShrunken = setDisplayMode(DisplayMode.MODE_NORMAL, false, true)
    }

    fun endShrunkenTvView() {
        isUnderShrunkenTvView = false
        tvView.setIsUnderShrunken(false)
        setTvViewMargin(tvViewStartMarginBeforeShrunken, tvViewEndMarginBeforeShrunken)
        setDisplayMode(displayModeBeforeShrunken, false, true)
    }

    /** Voll/Zoom nur, wenn das Video ein anderes Seitenverhältnis als der Bildschirm hat. */
    fun isDisplayModeAvailable(displayMode: Int): Boolean {
        if (displayMode == DisplayMode.MODE_NORMAL) return true
        val viewWidth = contentView.width
        val viewHeight = contentView.height
        val videoDisplayAspectRatio = tvView.videoDisplayAspectRatio
        if (viewWidth <= 0 || viewHeight <= 0 || videoDisplayAspectRatio <= 0f) {
            Log.w(TAG, "Video size is currently unavailable")
            return false
        }
        return abs(viewWidth / viewHeight.toFloat() - videoDisplayAspectRatio) >= DISPLAY_MODE_EPSILON
    }

    fun getDisplayMode(): Int = if (isDisplayModeAvailable(displayMode)) displayMode else DisplayMode.MODE_NORMAL

    /** Setzt das Bildformat und liefert das vorherige. */
    fun setDisplayMode(displayMode: Int, storeInPreference: Boolean, animate: Boolean): Int {
        val prev = this.displayMode
        this.displayMode = displayMode
        if (storeInPreference) sharedPreferences.edit().putInt(TvSettings.PREF_DISPLAY_MODE, displayMode).apply()
        applyDisplayMode(tvView.videoDisplayAspectRatio, animate, false)
        return prev
    }

    fun restoreDisplayMode(animate: Boolean) =
        setDisplayMode(sharedPreferences.getInt(TvSettings.PREF_DISPLAY_MODE, DisplayMode.MODE_NORMAL), false, animate)

    fun updateTvAspectRatio() {
        applyDisplayMode(tvView.videoDisplayAspectRatio, false, false)
        if (tvView.isVideoAvailable && tvView.isFadedOut) {
            tvView.fadeIn(resources.getInteger(R.integer.tvview_fade_in_duration), fastOutLinearIn, null)
        }
    }

    fun fadeInTvView() {
        if (tvView.isFadedOut) tvView.fadeIn(resources.getInteger(R.integer.tvview_fade_in_duration), fastOutLinearIn, null)
    }

    fun fadeOutTvView(postAction: Runnable?) {
        if (!tvView.isFadedOut) tvView.fadeOut(resources.getInteger(R.integer.tvview_fade_out_duration), linearOutSlowIn, postAction)
    }

    private fun setTvViewMargin(start: Int, end: Int) {
        tvViewStartMargin = start
        tvViewEndMargin = end
    }

    private fun isTvViewFullScreen() = tvViewStartMargin == 0 && tvViewEndMargin == 0

    private fun setBackgroundColor(color: Int, targetLayoutParams: FrameLayout.LayoutParams, animate: Boolean) {
        if (animate) {
            val animator = initBackgroundAnimatorIfNeeded()
            if (animator.isStarted) animator.cancel()
            val decorViewWidth = contentView.width
            val decorViewHeight = contentView.height
            val hasPillarBox = tvView.width != decorViewWidth || tvView.height != decorViewHeight
            val willHavePillarBox =
                (targetLayoutParams.width != ViewGroup.LayoutParams.MATCH_PARENT && targetLayoutParams.width != decorViewWidth) ||
                    (targetLayoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT && targetLayoutParams.height != decorViewHeight)
            if (!isTvViewFullScreen() && !hasPillarBox) {
                // Verkleinert ohne Balken: sofort setzen
                contentView.setBackgroundColor(color)
            } else if (!isTvViewFullScreen() || willHavePillarBox) {
                animator.setIntValues(backgroundColor, color)
                animator.setEvaluator(ArgbEvaluator())
                animator.interpolator = fastOutLinearIn
                animator.start()
            }
            // Sonst (Vollbild ohne Balken) keine Farbe sichtbar
        } else {
            contentView.setBackgroundColor(color)
        }
        backgroundColor = color
    }

    private fun setTvViewPosition(layoutParams: FrameLayout.LayoutParams, tvViewFrame: FrameLayout.LayoutParams, animate: Boolean) {
        val oldFrame = this.tvViewFrame
        tvViewLayoutParams = layoutParams
        this.tvViewFrame = tvViewFrame
        if (animate) {
            val animator = initTvAnimatorIfNeeded()
            oldTvViewFrame = if (animator.isStarted) {
                animator.cancel()
                FrameLayout.LayoutParams(lastAnimatedTvViewFrame!!)
            } else {
                FrameLayout.LayoutParams(oldFrame)
            }
            animator.setObjectValues(tvView.tvViewLayoutParams, layoutParams)
            animator.setEvaluator(object : TypeEvaluator<FrameLayout.LayoutParams> {
                private var lp: FrameLayout.LayoutParams? = null
                override fun evaluate(
                    fraction: Float, startValue: FrameLayout.LayoutParams, endValue: FrameLayout.LayoutParams,
                ): FrameLayout.LayoutParams {
                    val out = lp ?: FrameLayout.LayoutParams(0, 0).also { it.gravity = startValue.gravity; lp = it }
                    interpolateMargins(out, startValue, endValue, fraction)
                    return out
                }
            })
            animator.interpolator = if (isTvViewFullScreen()) fastOutLinearIn else linearOutSlowIn
            animator.start()
        } else {
            // Nach Ende der laufenden Animation wird die Position ohnehin gesetzt
            if (tvViewAnimator?.isStarted == true) return
            if (isTvViewFullScreen()) {
                // Verzögert setzen, damit die Surface nicht mehrfach neu aufgebaut wird
                handler.removeMessages(MSG_SET_LAYOUT_PARAMS)
                handler.obtainMessage(MSG_SET_LAYOUT_PARAMS, layoutParams).sendToTarget()
            } else {
                tvView.tvViewLayoutParams = layoutParams
                tvView.layoutParams = this.tvViewFrame
            }
        }
    }

    private fun initTvAnimatorIfNeeded(): ObjectAnimator {
        tvViewAnimator?.let { return it }
        return ObjectAnimator().also { animator ->
            tvViewAnimator = animator
            animator.target = tvView.tvView
            animator.setProperty(Property.of(FrameLayout::class.java, ViewGroup.LayoutParams::class.java, "layoutParams"))
            animator.duration = resources.getInteger(R.integer.tvview_anim_duration).toLong()
            animator.addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: Animator) { canceled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (canceled) {
                        canceled = false
                        return
                    }
                    // Endposition ohne Animation erneut setzen (feste Surface-Größe)
                    handler.post { setTvViewPosition(tvViewLayoutParams!!, tvViewFrame, false) }
                }
            })
            animator.addUpdateListener { anim ->
                val frame = tvView.layoutParams as FrameLayout.LayoutParams
                lastAnimatedTvViewFrame = frame
                interpolateMargins(frame, oldTvViewFrame!!, tvViewFrame, anim.animatedFraction)
                tvView.layoutParams = frame
            }
        }
    }

    private fun initBackgroundAnimatorIfNeeded(): ObjectAnimator {
        backgroundAnimator?.let { return it }
        return ObjectAnimator().also { animator ->
            backgroundAnimator = animator
            animator.target = contentView
            animator.setPropertyName("backgroundColor")
            animator.duration = resources.getInteger(R.integer.tvactivity_background_anim_duration).toLong()
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    handler.post { contentView.setBackgroundColor(backgroundColor) }
                }
            })
        }
    }

    /** Berechnet Größe/Position der TvView für Bildformat, Seitenverhältnis und Ränder. */
    private fun applyDisplayMode(ratio: Float, animate: Boolean, forceUpdate: Boolean) {
        var videoDisplayAspectRatio = ratio
        if (videoDisplayAspectRatio <= 0f) videoDisplayAspectRatio = windowWidth.toFloat() / windowHeight
        if (appliedDisplayedMode == displayMode && appliedTvViewStartMargin == tvViewStartMargin &&
            appliedTvViewEndMargin == tvViewEndMargin &&
            abs(appliedVideoDisplayAspectRatio - videoDisplayAspectRatio) < DISPLAY_ASPECT_RATIO_EPSILON
        ) {
            if (!forceUpdate) return
        } else {
            appliedDisplayedMode = displayMode
            appliedTvViewStartMargin = tvViewStartMargin
            appliedTvViewEndMargin = tvViewEndMargin
            appliedVideoDisplayAspectRatio = videoDisplayAspectRatio
        }
        val availableAreaWidth = windowWidth - tvViewStartMargin - tvViewEndMargin
        val availableAreaHeight = availableAreaWidth * windowHeight / windowWidth
        var mode = displayMode
        var availableAreaRatio = 0f
        if (availableAreaWidth <= 0 || availableAreaHeight <= 0) {
            mode = DisplayMode.MODE_FULL
            Log.w(TAG, "Wrong size of the available area: width=$availableAreaWidth, height=$availableAreaHeight")
        } else {
            availableAreaRatio = availableAreaWidth.toFloat() / availableAreaHeight
        }
        val layoutParams = FrameLayout.LayoutParams(0, 0, (tvView.tvViewLayoutParams as FrameLayout.LayoutParams).gravity)
        when (mode) {
            DisplayMode.MODE_ZOOM -> if (videoDisplayAspectRatio < availableAreaRatio) {
                layoutParams.width = availableAreaWidth
                layoutParams.height = (availableAreaWidth / videoDisplayAspectRatio).roundToInt()
            } else {
                layoutParams.width = (availableAreaHeight * videoDisplayAspectRatio).roundToInt()
                layoutParams.height = availableAreaHeight
            }
            DisplayMode.MODE_NORMAL -> if (videoDisplayAspectRatio < availableAreaRatio) {
                layoutParams.width = (availableAreaHeight * videoDisplayAspectRatio).roundToInt()
                layoutParams.height = availableAreaHeight
            } else {
                layoutParams.width = availableAreaWidth
                layoutParams.height = (availableAreaWidth / videoDisplayAspectRatio).roundToInt()
            }
            else -> {
                layoutParams.width = availableAreaWidth
                layoutParams.height = availableAreaHeight
            }
        }
        // Rahmen bleibt gleich, nur die innere TvView wird zentriert
        layoutParams.marginStart = (availableAreaWidth - layoutParams.width) / 2
        val tvViewFrameTop = (windowHeight - availableAreaHeight) / 2
        val frame = createMarginLayoutParams(tvViewStartMargin, tvViewEndMargin, tvViewFrameTop, tvViewFrameTop)
        setTvViewPosition(layoutParams, frame, animate)
        setBackgroundColor(
            resources.getColor(
                if (isTvViewFullScreen()) R.color.tvactivity_background else R.color.tvactivity_background_on_shrunken_tvview,
                null),
            layoutParams, animate)
        tvOptionsManager.onDisplayModeChanged(mode)
    }

    private fun createMarginLayoutParams(start: Int, end: Int, top: Int, bottom: Int) =
        FrameLayout.LayoutParams(0, 0).apply {
            marginStart = start
            marginEnd = end
            topMargin = top
            bottomMargin = bottom
            width = windowWidth - start - end
            height = windowHeight - top - bottom
        }

    companion object {
        private const val TAG = "TvViewManager"
        private const val DISPLAY_MODE_EPSILON = 0.001f
        private const val DISPLAY_ASPECT_RATIO_EPSILON = 0.01f
        private const val MSG_SET_LAYOUT_PARAMS = 1000

        private fun interpolate(start: Int, end: Int, fraction: Float) = (start + (end - start) * fraction).toInt()

        private fun interpolateMargins(out: MarginLayoutParams, s: MarginLayoutParams, e: MarginLayoutParams, fraction: Float) {
            out.topMargin = interpolate(s.topMargin, e.topMargin, fraction)
            out.bottomMargin = interpolate(s.bottomMargin, e.bottomMargin, fraction)
            out.marginStart = interpolate(s.marginStart, e.marginStart, fraction)
            out.marginEnd = interpolate(s.marginEnd, e.marginEnd, fraction)
            out.width = interpolate(s.width, e.width, fraction)
            out.height = interpolate(s.height, e.height, fraction)
        }
    }
}
