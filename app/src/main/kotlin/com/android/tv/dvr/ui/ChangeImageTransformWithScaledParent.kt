package com.android.tv.dvr.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.transition.ChangeImageTransform
import android.transition.TransitionValues
import android.util.AttributeSet
import android.widget.ImageView
import com.android.tv.R

/**
 * Workaround für b/32405620: berücksichtigt die Skalierung des Elternelements beim Shared-Element-
 * Übergang zwischen RecordingCardView und DetailsActivity.
 */
class ChangeImageTransformWithScaledParent(context: Context, attrs: AttributeSet?) : ChangeImageTransform(context, attrs) {

    override fun captureStartValues(transitionValues: TransitionValues) {
        super.captureStartValues(transitionValues)
        applyParentScale(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        super.captureEndValues(transitionValues)
        applyParentScale(transitionValues)
    }

    private fun applyParentScale(transitionValues: TransitionValues) {
        val view = transitionValues.view
        val matrix = transitionValues.values[PROPNAME_MATRIX] as? Matrix ?: return
        if (view.id != R.id.details_overview_image || view !is ImageView) return
        val drawable = view.drawable
        if (view.scaleType == ImageView.ScaleType.CENTER_INSIDE && drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap.width < view.width && bitmap.height < view.height) {
                val scale = view.context.resources.getFraction(R.fraction.lb_focus_zoom_factor_medium, 1, 1)
                matrix.postScale(scale, scale, (view.width / 2).toFloat(), (view.height / 2).toFloat())
            }
        }
    }

    private companion object {
        const val PROPNAME_MATRIX = "android:changeImageTransform:matrix"
    }
}
