package com.android.tv.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.tv.TvInputInfo
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.tv.R

/** Sperr-/Hinweisbildschirm über der TvView (Symbol, Text, Hintergrundbild, Einblendungen). */
class BlockScreenView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private lateinit var containerView: View
    private lateinit var imageContainer: View
    private lateinit var normalLockIconView: ImageView
    private lateinit var shrunkenLockIconView: ImageView
    private lateinit var space: View
    private lateinit var blockingInfoTextView: TextView
    private lateinit var backgroundImageView: ImageView
    private lateinit var emptyInputStatusBlockView: EmptyInputStatusBlockView
    private val spacingNormal = resources.getDimensionPixelOffset(R.dimen.tvview_block_vertical_spacing)
    private val spacingShrunken = resources.getDimensionPixelOffset(R.dimen.shrunken_tvview_block_vertical_spacing)

    /** Blendet den ganzen Sperrbildschirm aus. */
    private var fadeOut: Animator? = null
    /** Blenden Symbol und Text ein/aus. */
    private var infoFadeIn: Animator? = null
    private var infoFadeOut: Animator? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        containerView = findViewById(R.id.block_screen_container)
        imageContainer = findViewById(R.id.image_container)
        normalLockIconView = findViewById(R.id.block_screen_icon)
        shrunkenLockIconView = findViewById(R.id.block_screen_shrunken_icon)
        space = findViewById(R.id.space)
        blockingInfoTextView = findViewById(R.id.block_screen_text)
        backgroundImageView = findViewById(R.id.background_image)
        emptyInputStatusBlockView = findViewById(R.id.empty_input_status_block_view)

        fadeOut = AnimatorInflater.loadAnimator(context, R.animator.tvview_block_screen_fade_out).apply {
            setTarget(this@BlockScreenView)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    setBackgroundImage(null)
                    alpha = 1.0f
                }
            })
        }
        infoFadeIn = AnimatorInflater.loadAnimator(context, R.animator.tvview_block_screen_fade_in).apply {
            setTarget(containerView)
        }
        infoFadeOut = AnimatorInflater.loadAnimator(context, R.animator.tvview_block_screen_fade_out).apply {
            setTarget(containerView)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { containerView.visibility = GONE }
            })
        }
    }

    fun setIconImage(resId: Int) {
        normalLockIconView.setImageResource(resId)
        updateSpaceVisibility()
    }

    fun setIconScaleType(scaleType: ImageView.ScaleType) {
        normalLockIconView.scaleType = scaleType
        updateSpaceVisibility()
    }

    fun setIconVisibility(visible: Boolean) {
        imageContainer.visibility = if (visible) VISIBLE else GONE
        updateSpaceVisibility()
    }

    fun setInfoText(resId: Int) {
        blockingInfoTextView.setText(resId)
        updateSpaceVisibility()
    }

    fun setInfoText(text: String) {
        blockingInfoTextView.text = text
        updateSpaceVisibility()
    }

    fun setBackgroundImage(backgroundImage: Drawable?) {
        backgroundImageView.visibility = if (backgroundImage == null) GONE else VISIBLE
        backgroundImageView.setImageDrawable(backgroundImage)
    }

    /** Abstand nur, wenn Symbol und Text sichtbar sind. */
    private fun updateSpaceVisibility() {
        space.visibility = if (isImageViewVisible() && isTextViewVisible(blockingInfoTextView)) VISIBLE else GONE
    }

    private fun isImageViewVisible(): Boolean =
        imageContainer.visibility == VISIBLE &&
            (isImageViewVisible(normalLockIconView) || isImageViewVisible(shrunkenLockIconView))

    fun setSpacing(blockScreenType: Int) {
        space.layoutParams.height =
            if (blockScreenType == TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW) spacingShrunken else spacingNormal
        requestLayout()
    }

    fun setInfoTextOnClickListener(onClickListener: OnClickListener?) =
        blockingInfoTextView.setOnClickListener(onClickListener)

    fun onBlockStatusChanged(blockScreenType: Int, withAnimation: Boolean) {
        when (blockScreenType) {
            TunableTvView.BLOCK_SCREEN_TYPE_NO_UI ->
                if (!withAnimation) containerView.visibility = GONE
                else if (containerView.visibility == VISIBLE) infoFadeOut?.start()
            TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW -> showContainer(shrunken = true, withAnimation)
            TunableTvView.BLOCK_SCREEN_TYPE_NORMAL -> showContainer(shrunken = false, withAnimation)
        }
        updateSpaceVisibility()
    }

    private fun showContainer(shrunken: Boolean, withAnimation: Boolean) {
        normalLockIconView.visibility = if (shrunken) GONE else VISIBLE
        shrunkenLockIconView.visibility = if (shrunken) VISIBLE else GONE
        if (!withAnimation) {
            containerView.visibility = VISIBLE
            containerView.alpha = 1.0f
        } else if (containerView.visibility == GONE) {
            containerView.visibility = VISIBLE
            infoFadeIn?.start()
        }
    }

    fun addInfoFadeInAnimationListener(listener: Animator.AnimatorListener) { infoFadeIn?.addListener(listener) }

    fun fadeOut() {
        val anim = fadeOut ?: return
        if (visibility == VISIBLE && !anim.isStarted) anim.start()
    }

    fun endAnimations() {
        listOf(fadeOut, infoFadeIn, infoFadeOut).forEach { if (it != null && it.isRunning) it.end() }
    }

    fun setInfoTextClickable(clickable: Boolean) { blockingInfoTextView.isClickable = clickable }

    fun setEmptyInputStatusInputInfo(inputInfo: TvInputInfo?) =
        emptyInputStatusBlockView.setIconAndLabelByInputInfo(inputInfo)

    fun setEmptyInputStatusBlockVisibility(visible: Boolean) {
        emptyInputStatusBlockView.visibility = if (visible) VISIBLE else GONE
    }

    companion object {
        private fun isImageViewVisible(imageView: ImageView) = imageView.visibility != GONE && imageView.drawable != null
        private fun isTextViewVisible(textView: TextView) = textView.visibility != GONE && !textView.text.isNullOrEmpty()
    }
}
