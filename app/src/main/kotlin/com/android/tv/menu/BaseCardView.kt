package com.android.tv.menu

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.android.tv.R
import kotlin.math.roundToInt

/** Basis der Menükarten: Vergrößern/Anheben bei Fokus, bei langen Texten Karte ausfahren. */
abstract class BaseCardView<T> @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle), ItemListRowView.CardView<T> {

    private var focusAnimator: ValueAnimator? = null
    private val focusAnimDuration = resources.getInteger(R.integer.menu_focus_anim_duration).toLong()
    private val focusTranslationZ =
        resources.getDimension(R.dimen.channel_card_elevation_focused) - resources.getDimension(R.dimen.card_elevation_normal)
    private val verticalCardMargin = 2f * (resources.getDimensionPixelOffset(R.dimen.menu_list_padding_top) +
        resources.getDimensionPixelOffset(R.dimen.menu_list_margin_top))
    private val cardCornerRadius = resources.getDimensionPixelSize(R.dimen.channel_card_round_radius).toFloat()
    private var focusAnimatedValue = 0f
    private var extendViewOnFocus = false
    private val extendedCardHeight = resources.getDimensionPixelSize(R.dimen.card_layout_height_extended).toFloat()
    private val textViewHeight = resources.getDimensionPixelSize(R.dimen.card_meta_layout_height).toFloat()
    private val extendedTextViewHeight = resources.getDimensionPixelOffset(R.dimen.card_meta_layout_height_extended).toFloat()
    private val cardImageWidth = resources.getDimensionPixelSize(R.dimen.card_image_layout_width)
    private val cardHeight = resources.getDimensionPixelSize(R.dimen.card_layout_height).toFloat()
    private var selected = false
    private var textResId = 0
    private var textString: String? = null
    private var textView: TextView? = null
    private var textViewFocused: TextView? = null

    init {
        clipToOutline = true
        elevation = resources.getDimension(R.dimen.card_elevation_normal)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, cardCornerRadius)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        textView = findViewById(R.id.card_text)
        textViewFocused = findViewById(R.id.card_text_focused)
    }

    override fun onBind(item: T, selected: Boolean) {
        setFocusAnimatedValue(if (selected) SCALE_FACTOR_1F else SCALE_FACTOR_0F)
    }

    override fun onRecycled() {}

    override fun onSelected() {
        selected = true
        if (isAttachedToWindow && visibility == VISIBLE) {
            startFocusAnimation(SCALE_FACTOR_1F)
        } else {
            cancelFocusAnimationIfAny()
            setFocusAnimatedValue(SCALE_FACTOR_1F)
        }
    }

    override fun onDeselected() {
        selected = false
        if (isAttachedToWindow && visibility == VISIBLE) {
            startFocusAnimation(SCALE_FACTOR_0F)
        } else {
            cancelFocusAnimationIfAny()
            setFocusAnimatedValue(SCALE_FACTOR_0F)
        }
    }

    override fun requestFocusWithAccessibility(): Boolean =
        requestFocus() && performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)

    fun setText(resId: Int) {
        if (textResId != resId) {
            textResId = resId
            textString = null
            textViewFocused?.setText(resId)
            textView?.setText(resId)
            onTextViewUpdated()
        }
    }

    fun setText(text: String?) {
        if (text != textString) {
            textString = text
            textResId = 0
            textViewFocused?.text = text
            textView?.text = text
            onTextViewUpdated()
        }
    }

    /** Mehrzeiliger Text: Karte bei Fokus ausfahren. */
    private fun onTextViewUpdated() {
        val focused = textViewFocused
        if (textView != null && focused != null) {
            focused.measure(
                MeasureSpec.makeMeasureSpec(cardImageWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            extendViewOnFocus = focused.lineCount > 1
            setTextViewFocusedAlpha(if (extendViewOnFocus) (if (selected) 1f else 0f) else 1f)
        }
        setFocusAnimatedValue(if (selected) SCALE_FACTOR_1F else SCALE_FACTOR_0F)
    }

    fun setTextViewEnabled(enabled: Boolean) {
        textViewFocused?.isEnabled = enabled
        textView?.isEnabled = enabled
    }

    protected open fun onFocusAnimationStart(selected: Boolean) {
        if (extendViewOnFocus) setTextViewFocusedAlpha(if (selected) 1f else 0f)
    }

    protected open fun onFocusAnimationEnd(selected: Boolean) {}

    protected open fun onSetFocusAnimatedValue(animatedValue: Float) {
        val cardViewHeight = if (extendViewOnFocus && isFocused) extendedCardHeight else cardHeight
        val scale = 1f + (verticalCardMargin / cardViewHeight) * animatedValue
        scaleX = scale
        scaleY = scale
        translationZ = focusTranslationZ * animatedValue
        val tv = textView
        if (tv != null && textViewFocused != null) {
            val params = tv.layoutParams
            val height = if (extendViewOnFocus) {
                (textViewHeight + (extendedTextViewHeight - textViewHeight) * animatedValue).roundToInt()
            } else {
                textViewHeight.toInt()
            }
            if (height != params.height) {
                params.height = height
                setTextViewLayoutParams(params)
            }
            if (extendViewOnFocus) setTextViewFocusedAlpha(animatedValue)
        }
    }

    private fun setFocusAnimatedValue(animatedValue: Float) {
        focusAnimatedValue = animatedValue
        onSetFocusAnimatedValue(animatedValue)
    }

    private fun startFocusAnimation(targetAnimatedValue: Float) {
        cancelFocusAnimationIfAny()
        val selected = targetAnimatedValue == SCALE_FACTOR_1F
        focusAnimator = ValueAnimator.ofFloat(focusAnimatedValue, targetAnimatedValue).apply {
            duration = focusAnimDuration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    setHasTransientState(true)
                    onFocusAnimationStart(selected)
                }

                override fun onAnimationEnd(animation: Animator) {
                    setHasTransientState(false)
                    onFocusAnimationEnd(selected)
                }
            })
            addUpdateListener { setFocusAnimatedValue(it.animatedValue as Float) }
            start()
        }
    }

    private fun cancelFocusAnimationIfAny() {
        focusAnimator?.cancel()
        focusAnimator = null
    }

    private fun setTextViewLayoutParams(params: ViewGroup.LayoutParams) {
        textViewFocused?.layoutParams = params
        textView?.layoutParams = params
    }

    private fun setTextViewFocusedAlpha(focusedAlpha: Float) {
        textViewFocused?.alpha = focusedAlpha
        textView?.alpha = 1f - focusedAlpha
    }

    companion object {
        private const val SCALE_FACTOR_0F = 0f
        private const val SCALE_FACTOR_1F = 1f
    }
}

/** Einfache Karte (Guide, Setup, DVR, Pfeile). */
class SimpleCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    BaseCardView<ChannelsRowItem>(context, attrs, defStyle)
