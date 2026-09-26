package com.android.tv.dvr.ui.browse

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.android.tv.R
import com.android.tv.ui.ViewUtils
import com.android.tv.util.Utils

/** Presenter für den Beschreibungsbereich der DVR-Detailansicht (mit „Mehr lesen“). */
class DetailsContentPresenter(private val activity: Activity) : Presenter() {
    private val fullTextAnimationDuration: Int =
        activity.resources.getInteger(R.integer.dvr_details_full_text_animation_duration)

    /** ViewHolder des [DetailsContentPresenter]. */
    class ViewHolder(view: View) : Presenter.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.dvr_details_description_title)
        val subtitle: TextView = view.findViewById(R.id.dvr_details_description_subtitle)
        internal val errorMessage: LinearLayout = view.findViewById(R.id.dvr_details_description_error_message)
        val body: TextView = view.findViewById(R.id.dvr_details_description_body)
        internal val descriptionContainer: LinearLayout = view.findViewById(R.id.dvr_details_description_container)
        internal val readMoreView: TextView
        internal val titleMargin: Int
        internal val underTitleBaselineMargin: Int
        internal val underSubtitleBaselineMargin: Int
        internal val titleLineSpacing: Int
        internal val bodyLineSpacing: Int
        internal val bodyMaxLines: Int
        internal val bodyMinLines: Int
        internal val titleFontMetricsInt: FontMetricsInt
        internal val subtitleFontMetricsInt: FontMetricsInt
        internal val bodyFontMetricsInt: FontMetricsInt
        internal val titleMaxLines: Int

        internal var activity: Activity? = null
        internal var fullTextAnimationDuration = 0
        private var fullTextMode = false
        private var isListeningToPreDraw = false

        private val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (subtitle.visibility == View.VISIBLE && subtitle.top > this@ViewHolder.view.height &&
                    title.lineCount > 1
                ) {
                    title.maxLines = title.lineCount - 1
                    return false
                }
                val bodyLines = body.lineCount
                var maxLines = if (fullTextMode) bodyLines
                else if (title.lineCount > 1) bodyMinLines else bodyMaxLines
                if (bodyLines > maxLines) {
                    readMoreView.visibility = View.VISIBLE
                    descriptionContainer.isFocusable = true
                    descriptionContainer.isClickable = true
                    descriptionContainer.setOnClickListener { v ->
                        fullTextMode = true
                        readMoreView.visibility = View.GONE
                        descriptionContainer.isFocusable = isAccessibilityEnabled(v.context)
                        descriptionContainer.isClickable = false
                        descriptionContainer.setOnClickListener(null)
                        val oldMaxLines = body.maxLines
                        body.maxLines = bodyLines
                        // 1 abziehen, um den Platz von „MEHR LESEN“ zu entfernen
                        showFullText((bodyLines - oldMaxLines - 1) * bodyLineSpacing)
                    }
                }
                if (readMoreView.visibility == View.VISIBLE && subtitle.visibility == View.VISIBLE) {
                    // Bei „MEHR LESEN“ und Untertitel passt eine Zeile weniger.
                    maxLines -= 1
                }
                return if (body.maxLines != maxLines) {
                    body.maxLines = maxLines
                    false
                } else {
                    removePreDrawListener()
                    true
                }
            }
        }

        init {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // Falls der Listener beim Detach entfernt wurde, Layout sicherstellen.
                    addPreDrawListener()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    removePreDrawListener()
                }
            })
            // Für Barrierefreiheit explizit fokussierbar setzen, da „MEHR LESEN“ den Zustand ändert.
            descriptionContainer.isFocusable = isAccessibilityEnabled(view.context)
            readMoreView = view.findViewById(R.id.dvr_details_description_read_more)

            val res = view.resources
            val titleAscent = res.getDimensionPixelSize(R.dimen.lb_details_description_title_baseline)
            // Ascent ist negativ
            titleMargin = titleAscent + getFontMetricsInt(title).ascent
            underTitleBaselineMargin =
                res.getDimensionPixelSize(R.dimen.lb_details_description_under_title_baseline_margin)
            underSubtitleBaselineMargin =
                res.getDimensionPixelSize(R.dimen.dvr_details_description_under_subtitle_baseline_margin)
            titleLineSpacing = res.getDimensionPixelSize(R.dimen.lb_details_description_title_line_spacing)
            bodyLineSpacing = res.getDimensionPixelSize(R.dimen.lb_details_description_body_line_spacing)
            bodyMaxLines = res.getInteger(R.integer.lb_details_description_body_max_lines)
            bodyMinLines = res.getInteger(R.integer.lb_details_description_body_min_lines)
            titleMaxLines = title.maxLines
            titleFontMetricsInt = getFontMetricsInt(title)
            subtitleFontMetricsInt = getFontMetricsInt(subtitle)
            bodyFontMetricsInt = getFontMetricsInt(body)
        }

        internal fun addPreDrawListener() {
            if (!isListeningToPreDraw) {
                isListeningToPreDraw = true
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            }
        }

        internal fun removePreDrawListener() {
            if (isListeningToPreDraw) {
                view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                isListeningToPreDraw = false
            }
        }

        private fun getFontMetricsInt(textView: TextView): FontMetricsInt {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = textView.textSize
            paint.typeface = textView.typeface
            return paint.fontMetricsInt
        }

        private fun showFullText(heightDiff: Int) {
            // Bugfix: Activity kann (theoretisch) noch nicht gesetzt sein → nichts animieren.
            val detailsFrame = activity?.findViewById<ViewGroup>(R.id.details_frame) ?: return
            val nowHeight = ViewUtils.getLayoutHeight(detailsFrame)
            val expandAnimator = ViewUtils.createHeightAnimator(detailsFrame, nowHeight, nowHeight + heightDiff)
            expandAnimator.duration = fullTextAnimationDuration.toLong()
            val shiftAnimator = ObjectAnimator.ofPropertyValuesHolder(
                detailsFrame,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -(heightDiff / 2).toFloat()))
            shiftAnimator.duration = fullTextAnimationDuration.toLong()
            val fullTextAnimator = AnimatorSet()
            fullTextAnimator.playTogether(expandAnimator, shiftAnimator)
            fullTextAnimator.start()
        }

        private fun isAccessibilityEnabled(context: Context): Boolean =
            (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isEnabled
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.dvr_details_description, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val vh = viewHolder as ViewHolder
        val detailsContent = item as DetailsContent

        vh.activity = activity
        vh.fullTextAnimationDuration = fullTextAnimationDuration

        var hasTitle = true
        if (TextUtils.isEmpty(detailsContent.title)) {
            vh.title.visibility = View.GONE
            hasTitle = false
        } else {
            vh.title.text = detailsContent.title
            vh.title.visibility = View.VISIBLE
            vh.title.setLineSpacing(
                vh.titleLineSpacing - vh.title.lineHeight + vh.title.lineSpacingExtra,
                vh.title.lineSpacingMultiplier)
            vh.title.maxLines = vh.titleMaxLines
        }
        setTopMargin(vh.title, vh.titleMargin)

        var hasSubtitle = true
        if (detailsContent.startTimeUtcMillis != DetailsContent.INVALID_TIME &&
            detailsContent.endTimeUtcMillis != DetailsContent.INVALID_TIME
        ) {
            vh.subtitle.text = Utils.getDurationString(
                viewHolder.view.context, detailsContent.startTimeUtcMillis, detailsContent.endTimeUtcMillis, false)
            vh.subtitle.visibility = View.VISIBLE
            if (hasTitle) {
                setTopMargin(vh.subtitle,
                    vh.underTitleBaselineMargin + vh.subtitleFontMetricsInt.ascent - vh.titleFontMetricsInt.descent)
            } else {
                setTopMargin(vh.subtitle, 0)
            }
        } else {
            vh.subtitle.visibility = View.GONE
            hasSubtitle = false
        }

        if (TextUtils.isEmpty(detailsContent.description)) {
            vh.body.visibility = View.GONE
        } else {
            if (detailsContent.shouldShowErrorMessage()) {
                vh.errorMessage.visibility = View.VISIBLE
            }
            vh.body.text = detailsContent.description
            vh.body.visibility = View.VISIBLE
            vh.body.setLineSpacing(
                vh.bodyLineSpacing - vh.body.lineHeight + vh.body.lineSpacingExtra,
                vh.body.lineSpacingMultiplier)
            when {
                hasSubtitle -> setTopMargin(vh.descriptionContainer,
                    vh.underSubtitleBaselineMargin + vh.bodyFontMetricsInt.ascent -
                        vh.subtitleFontMetricsInt.descent - vh.body.paddingTop)
                hasTitle -> setTopMargin(vh.descriptionContainer,
                    vh.underTitleBaselineMargin + vh.bodyFontMetricsInt.ascent -
                        vh.titleFontMetricsInt.descent - vh.body.paddingTop)
                else -> setTopMargin(vh.descriptionContainer, 0)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}

    private fun setTopMargin(view: View, topMargin: Int) {
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = topMargin
        view.layoutParams = lp
    }
}
