package com.android.tv.dvr.ui.browse

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.leanback.widget.BaseCardView
import com.android.tv.R
import com.android.tv.ui.ViewUtils
import com.android.tv.util.images.ImageLoader

/**
 * Karte für eine [com.android.tv.dvr.data.ScheduledRecording], ein
 * [com.android.tv.dvr.data.RecordedProgram] oder eine [com.android.tv.dvr.data.SeriesRecording].
 */
class RecordingCardView(
    context: Context,
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val expandTitleWhenFocused: Boolean,
) : BaseCardView(context) {

    constructor(context: Context) : this(context, false)

    constructor(context: Context, expandTitleWhenFocused: Boolean) : this(
        context,
        context.resources.getDimensionPixelSize(R.dimen.dvr_library_card_image_layout_width),
        context.resources.getDimensionPixelSize(R.dimen.dvr_library_card_image_layout_height),
        expandTitleWhenFocused,
    )

    /** Bild der Karte. */
    val imageView: ImageView
    private var imageUri: String? = null
    private val contentIconView: ImageView
    private val majorContentView: TextView
    private val minorContentView: TextView
    private val progressBar: ProgressBar
    private val defaultImage: Drawable
    private val titleArea: FrameLayout
    private val foldedTitleView: TextView
    private val expandedTitleView: TextView
    private val expandTitleAnimator: ValueAnimator
    private val foldedTitleHeight: Int
    private val expandedTitleHeight: Int
    private var expanded = false
    private var detailBackgroundImageUri: String? = null
    private var titleViewLayout: Layout? = null

    init {
        // TODO(dvr): ins Layout-XML verschieben (aus dem Original).
        cardType = CARD_TYPE_INFO_UNDER_WITH_EXTRA
        infoVisibility = CARD_REGION_VISIBLE_ALWAYS
        isFocusable = true
        isFocusableInTouchMode = true
        defaultImage = resources.getDrawable(R.drawable.dvr_default_poster, null)

        LayoutInflater.from(getContext()).inflate(R.layout.dvr_recording_card_view, this)
        imageView = findViewById(R.id.image)
        progressBar = findViewById(R.id.recording_progress)
        contentIconView = findViewById(R.id.content_icon)
        majorContentView = findViewById(R.id.content_major)
        minorContentView = findViewById(R.id.content_minor)
        titleArea = findViewById(R.id.title_area)
        foldedTitleView = findViewById(R.id.title_one_line)
        expandedTitleView = findViewById(R.id.title_two_lines)
        foldedTitleHeight = resources.getDimensionPixelSize(R.dimen.dvr_library_card_folded_title_height)
        expandedTitleHeight = resources.getDimensionPixelSize(R.dimen.dvr_library_card_expanded_title_height)
        expandTitleAnimator = ValueAnimator.ofFloat(0.0f, 1.0f).setDuration(ANIMATION_DURATION)
        expandTitleAnimator.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Float
            expandedTitleView.alpha = value
            foldedTitleView.alpha = 1.0f - value
            ViewUtils.setLayoutHeight(
                titleArea, (foldedTitleHeight + (expandedTitleHeight - foldedTitleHeight) * value).toInt())
        }
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                titleViewLayout = foldedTitleView.layout
            }
        })
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        // Hintergrundbild der Detailansicht vorladen, damit es beim Übergang nicht erst geladen wird.
        if (gainFocus) {
            val uri = detailBackgroundImageUri
            if (!TextUtils.isEmpty(uri)) {
                ImageLoader.loadBitmap<Any>(context, uri, Int.MAX_VALUE, Int.MAX_VALUE, null)
            }
        }
        if (expandTitleWhenFocused) {
            expandTitle(gainFocus, true)
        }
    }

    /**
     * Klappt den Titelbereich auf (zwei Zeilen) bzw. zu (eine Zeile).
     *
     * @param expand true zum Aufklappen, false zum Zuklappen.
     * @param withAnimation true für eine Animation.
     */
    fun expandTitle(expand: Boolean, withAnimation: Boolean) {
        val layout = titleViewLayout
        if (expand != expanded && layout != null && layout.getEllipsisCount(0) > 0) {
            if (withAnimation) {
                if (expand) expandTitleAnimator.start() else expandTitleAnimator.reverse()
            } else if (expand) {
                foldedTitleView.alpha = 0.0f
                expandedTitleView.alpha = 1.0f
                ViewUtils.setLayoutHeight(titleArea, expandedTitleHeight)
            } else {
                foldedTitleView.alpha = 1.0f
                expandedTitleView.alpha = 0.0f
                ViewUtils.setLayoutHeight(titleArea, foldedTitleHeight)
            }
            expanded = expand
        }
    }

    internal fun setTitle(title: CharSequence?) {
        foldedTitleView.text = title
        expandedTitleView.text = title
    }

    internal fun setContent(majorContent: CharSequence?, minorContent: CharSequence?) {
        contentIconView.visibility = View.GONE
        if (!TextUtils.isEmpty(majorContent)) {
            majorContentView.text = majorContent
            majorContentView.visibility = View.VISIBLE
        } else {
            majorContentView.visibility = View.GONE
        }
        if (!TextUtils.isEmpty(minorContent)) {
            minorContentView.text = minorContent
            minorContentView.visibility = View.VISIBLE
        } else {
            minorContentView.visibility = View.GONE
        }
    }

    internal fun setRecordingFailedContent(context: Context) {
        contentIconView.visibility = View.VISIBLE
        contentIconView.setImageResource(R.drawable.ic_error_outline_pink_24dp)
        majorContentView.text = context.getString(R.string.dvr_recording_failed_no_period)
        majorContentView.visibility = View.VISIBLE
        majorContentView.setTextColor(resources.getColor(R.color.dvr_recording_failed_text_color, null))
    }

    internal fun setRecordingConflictContent(context: Context) {
        contentIconView.visibility = View.VISIBLE
        contentIconView.setImageResource(R.drawable.ic_warning_yellow_24dp)
        majorContentView.text = context.getString(R.string.dvr_recording_conflict)
        majorContentView.visibility = View.VISIBLE
        majorContentView.setTextColor(resources.getColor(R.color.dvr_recording_conflict_text_color, null))
    }

    /** Setzt den Fortschrittsbalken; bei null wird er ausgeblendet. */
    internal fun setProgressBar(progress: Int?) {
        if (progress == null) {
            progressBar.visibility = View.GONE
        } else {
            progressBar.progress = progress
            progressBar.visibility = View.VISIBLE
        }
    }

    /** Setzt die Farbe des Fortschrittsbalkens. */
    internal fun setProgressBarColor(color: Int) {
        progressBar.progressDrawable.setTint(color)
    }

    /**
     * Setzt die Poster-URI der Karte.
     *
     * @param isChannelLogo true, wenn das Bild das Kanallogo ist.
     */
    internal fun setImageUri(uri: String?, isChannelLogo: Boolean) {
        imageView.scaleType = if (isChannelLogo) ImageView.ScaleType.CENTER_INSIDE else ImageView.ScaleType.CENTER_CROP
        imageUri = uri
        if (uri.isNullOrEmpty()) {
            imageView.setImageDrawable(defaultImage)
        } else {
            ImageLoader.loadBitmap(context, uri, imageWidth, imageHeight, RecordingCardImageLoaderCallback(this, uri))
        }
    }

    /** Setzt das Poster-Drawable der Karte. */
    fun setImage(image: Drawable?) {
        if (image != null) imageView.setImageDrawable(image)
    }

    /** Hintergrundbild-URI, die beim Öffnen der Detailansicht als Hintergrund dient. */
    fun setDetailBackgroundImageUri(uri: String?) {
        detailBackgroundImageUri = uri
    }

    private class RecordingCardImageLoaderCallback(referent: RecordingCardView, private val uri: String) :
        ImageLoader.ImageLoaderCallback<RecordingCardView>(referent) {
        override fun onBitmapLoaded(referent: RecordingCardView, bitmap: Bitmap?) {
            if (bitmap == null || uri != referent.imageUri) {
                referent.imageView.setImageDrawable(referent.defaultImage)
            } else {
                referent.imageView.setImageDrawable(BitmapDrawable(referent.resources, bitmap))
            }
        }
    }

    fun reset() {
        foldedTitleView.text = null
        expandedTitleView.text = null
        setContent(null, null)
        imageView.setImageDrawable(defaultImage)
    }

    companion object {
        // Muss FocusHighlightHelper.BrowseItemFocusHighlight.DURATION_MS entsprechen.
        private const val ANIMATION_DURATION = 150L
    }
}
