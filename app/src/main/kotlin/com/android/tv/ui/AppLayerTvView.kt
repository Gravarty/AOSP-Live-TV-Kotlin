package com.android.tv.ui

import android.content.Context
import android.media.tv.TvContentRating
import android.util.Log
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View
import android.media.tv.TvView
import com.android.tv.common.compat.TvViewCompat

/** TvView, die immer Fokus meldet und ihre SurfaceView standardmäßig sicher schaltet. */
class AppLayerTvView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : TvViewCompat(context, attrs, defStyleAttr) {

    private var useSecureSurface = true

    fun setUseSecureSurface(secure: Boolean) { useSecureSurface = secure }

    override fun hasWindowFocus(): Boolean = true

    override fun onViewAdded(child: View) {
        // Siehe b/29118070
        if (child is SurfaceView) child.setSecure(useSecureSurface)
        super.onViewAdded(child)
    }

    /**
     * Abweichung: TvView.unblockContent() ist @SystemApi und fehlt im öffentlichen SDK, daher per
     * Reflection. Braucht MODIFY_PARENTAL_CONTROLS (nur System-App).
     * Bugfix: Ohne dieses Recht stürzte das Original mit SecurityException ab; jetzt nur Log.
     */
    fun unblockContentCompat(rating: TvContentRating) {
        try {
            TvView::class.java.getMethod("unblockContent", TvContentRating::class.java).invoke(this, rating)
        } catch (e: Exception) {
            Log.w("AppLayerTvView", "unblockContent nicht möglich", e)
        }
    }
}
