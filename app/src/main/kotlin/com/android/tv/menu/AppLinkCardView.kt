package com.android.tv.menu

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.api.Channel
import com.android.tv.util.TvInputManagerHelper
import com.android.tv.util.images.BitmapUtils
import com.android.tv.util.images.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-Link-Karte des aktuellen Kanals (Link-Text, App-Name/-Symbol, Poster bzw. App-Banner).
 * AsyncTasks → Coroutines. Bugfixes: vertauschte Prüfung "gleicher Input?" beim Nachladen von
 * Label/Banner (Ergebnis wurde verworfen statt angezeigt), NPE ohne ApplicationInfo.
 */
class AppLinkCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : BaseCardView<ChannelsRowItem>(context, attrs, defStyle) {

    private val cardImageWidth = resources.getDimensionPixelSize(R.dimen.card_image_layout_width)
    private val cardImageHeight = resources.getDimensionPixelSize(R.dimen.card_image_layout_height)
    private val iconWidth = resources.getDimensionPixelSize(R.dimen.app_link_card_icon_width)
    private val iconHeight = resources.getDimensionPixelSize(R.dimen.app_link_card_icon_height)
    private val iconPadding = resources.getDimensionPixelOffset(R.dimen.app_link_card_icon_padding)
    private val iconColorFilter = resources.getColor(R.color.app_link_card_icon_color_filter, null)
    private val defaultDrawable: Drawable? = resources.getDrawable(R.drawable.ic_recent_thumbnail_default, null)
    private val packageManager: PackageManager = context.packageManager
    private val mainActivity = context as MainActivity
    private val tvInputManagerHelper: TvInputManagerHelper = mainActivity.tvInputManagerHelper
    private lateinit var imageView: ImageView
    private lateinit var appInfoView: TextView
    private lateinit var metaViewHolder: View
    private var channel: Channel? = null
    var intent: Intent? = null
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()
        imageView = findViewById(R.id.image)
        appInfoView = findViewById(R.id.app_info)
        metaViewHolder = findViewById(R.id.app_link_text_holder)
    }

    override fun onBind(item: ChannelsRowItem, selected: Boolean) {
        val newChannel = item.channel ?: return
        val channelChanged = channel != newChannel
        val previousPosterArtUri = channel?.appLinkPosterArtUri
        val posterArtChanged = previousPosterArtUri == null || newChannel.appLinkPosterArtUri == null ||
            previousPosterArtUri != newChannel.appLinkPosterArtUri
        channel = newChannel
        val appInfo = tvInputManagerHelper.getTvInputAppInfo(newChannel.inputId)
        if (channelChanged) {
            intent = newChannel.getAppLinkIntent(context)
            when (newChannel.getAppLinkType(context)) {
                Channel.APP_LINK_TYPE_CHANNEL -> {
                    setText(newChannel.appLinkText)
                    appInfoView.visibility = VISIBLE
                    appInfoView.compoundDrawablePadding = iconPadding
                    appInfoView.setCompoundDrawablesRelative(null, null, null, null)
                    val appLabel = tvInputManagerHelper.getTvInputApplicationLabel(newChannel.inputId)
                    if (appLabel != null) appInfoView.text = appLabel
                    else loadAppLabel(newChannel.inputId, appInfo) { appInfoView.text = it }
                    if (!newChannel.appLinkIconUri.isNullOrEmpty()) {
                        newChannel.loadBitmap(context, Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON, iconWidth, iconHeight,
                            createChannelLogoCallback(this, newChannel, Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON))
                    } else if (appInfo != null && appInfo.icon != 0) {
                        val cachedIcon = tvInputManagerHelper.getTvInputApplicationIcon(newChannel.inputId)
                        if (cachedIcon != null) setAppIcon(cachedIcon) else loadAppIcon(newChannel.inputId, appInfo)
                    }
                }
                Channel.APP_LINK_TYPE_APP -> {
                    val appLabel = tvInputManagerHelper.getTvInputApplicationLabel(newChannel.inputId)
                    if (appLabel != null) {
                        setText(context.getString(R.string.channels_item_app_link_app_launcher, appLabel))
                    } else {
                        loadAppLabel(newChannel.inputId, appInfo) {
                            setText(context.getString(R.string.channels_item_app_link_app_launcher, it))
                        }
                    }
                    appInfoView.visibility = GONE
                }
                else -> appInfoView.visibility = GONE
            }
            if (newChannel.appLinkColor == 0) metaViewHolder.setBackgroundResource(R.color.channel_card_meta_background)
            else metaViewHolder.setBackgroundColor(newChannel.appLinkColor)
        }
        if (posterArtChanged) {
            imageView.setImageDrawable(defaultDrawable)
            imageView.foreground = null
            if (!newChannel.appLinkPosterArtUri.isNullOrEmpty()) {
                newChannel.loadBitmap(context, Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART, cardImageWidth, cardImageHeight,
                    createChannelLogoCallback(this, newChannel, Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART))
            } else {
                setCardImageWithBanner(appInfo)
            }
        }
        super.onBind(item, selected)
    }

    private fun isStillCurrent(inputId: String) = inputId == channel?.inputId && isAttachedToWindow

    private fun loadAppLabel(inputId: String, appInfo: ApplicationInfo?, onLoaded: (CharSequence) -> Unit) {
        mainActivity.lifecycleScope.launch {
            val label = withContext(Dispatchers.IO) { appInfo?.let { packageManager.getApplicationLabel(it) } }
            if (label != null) tvInputManagerHelper.setTvInputApplicationLabel(inputId, label)
            if (label != null && isStillCurrent(inputId)) onLoaded(label)
        }
    }

    private fun loadAppIcon(inputId: String, appInfo: ApplicationInfo) {
        mainActivity.lifecycleScope.launch {
            val icon = withContext(Dispatchers.IO) { packageManager.getApplicationIcon(appInfo) }
            tvInputManagerHelper.setTvInputApplicationIcon(inputId, icon)
            if (isStillCurrent(inputId)) setAppIcon(icon)
        }
    }

    private fun setAppIcon(icon: Drawable) {
        BitmapUtils.setColorFilterToDrawable(iconColorFilter, icon)
        icon.setBounds(0, 0, iconWidth, iconHeight)
        appInfoView.setCompoundDrawablesRelative(icon, null, null, null)
    }

    private fun updateChannelLogo(bitmap: Bitmap?, type: Int) {
        if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_ICON) {
            // Symbol ins Seitenverhältnis einpassen
            val drawable = bitmap?.let {
                BitmapDrawable(resources, it).apply {
                    if (it.width > it.height) setBounds(0, 0, iconWidth, iconWidth * it.height / it.width)
                    else setBounds(0, 0, iconHeight * it.width / it.height, iconHeight)
                }
            }
            BitmapUtils.setColorFilterToDrawable(iconColorFilter, drawable)
            appInfoView.setCompoundDrawablesRelative(drawable, null, null, null)
        } else if (type == Channel.LOAD_IMAGE_TYPE_APP_LINK_POSTER_ART) {
            val ch = channel ?: return
            if (bitmap == null) {
                setCardImageWithBanner(tvInputManagerHelper.getTvInputAppInfo(ch.inputId))
            } else {
                imageView.setImageBitmap(bitmap)
                imageView.foreground = context.getDrawable(R.drawable.card_image_gradient)
                if (ch.appLinkColor == 0) extractAndSetMetaViewBackgroundColor(bitmap)
            }
        }
    }

    /** Banner der Ziel-Activity, sonst deren Icon, sonst App-Banner. */
    private fun setCardImageWithBanner(appInfo: ApplicationInfo?) {
        val inputId = channel?.inputId ?: return
        val linkIntent = intent
        mainActivity.lifecycleScope.launch {
            val banner = withContext(Dispatchers.IO) {
                if (linkIntent == null) return@withContext null
                try {
                    packageManager.getActivityBanner(linkIntent) ?: packageManager.getActivityIcon(linkIntent)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            if (!isStillCurrent(inputId)) return@launch
            if (banner != null) setCardImageWithBannerInternal(banner) else setCardImageWithApplicationInfoBanner(appInfo)
        }
    }

    private fun setCardImageWithApplicationInfoBanner(appInfo: ApplicationInfo?) {
        val inputId = channel?.inputId ?: return
        val appBanner = tvInputManagerHelper.getTvInputApplicationBanner(inputId)
        if (appBanner != null) {
            setCardImageWithBannerInternal(appBanner)
            return
        }
        mainActivity.lifecycleScope.launch {
            val banner = withContext(Dispatchers.IO) {
                if (appInfo == null) return@withContext null
                var b: Drawable? = if (appInfo.banner != 0) packageManager.getApplicationBanner(appInfo) else null
                if (b == null && appInfo.icon != 0) b = packageManager.getApplicationIcon(appInfo)
                b
            }
            if (banner != null) tvInputManagerHelper.setTvInputApplicationBanner(inputId, banner)
            if (isStillCurrent(inputId)) setCardImageWithBannerInternal(banner)
        }
    }

    private fun setCardImageWithBannerInternal(banner: Drawable?) {
        if (banner == null) {
            imageView.setImageDrawable(defaultDrawable)
            imageView.setBackgroundResource(R.color.channel_card)
            return
        }
        // Banner in Kartengröße rendern (für die Farbermittlung)
        val bitmap = Bitmap.createBitmap(cardImageWidth, cardImageHeight, Bitmap.Config.ARGB_8888)
        banner.setBounds(0, 0, cardImageWidth, cardImageHeight)
        banner.draw(Canvas(bitmap))
        imageView.setImageDrawable(banner)
        imageView.foreground = context.getDrawable(R.drawable.card_image_gradient)
        if (channel?.appLinkColor == 0) extractAndSetMetaViewBackgroundColor(bitmap)
    }

    private fun extractAndSetMetaViewBackgroundColor(bitmap: Bitmap) {
        Palette.Builder(bitmap).generate { palette ->
            metaViewHolder.setBackgroundColor(
                palette?.getDarkVibrantColor(resources.getColor(R.color.channel_card_meta_background, null))
                    ?: resources.getColor(R.color.channel_card_meta_background, null))
        }
    }

    companion object {
        private fun createChannelLogoCallback(cardView: AppLinkCardView, channel: Channel, type: Int) =
            object : ImageLoader.ImageLoaderCallback<AppLinkCardView>(cardView) {
                override fun onBitmapLoaded(referent: AppLinkCardView, bitmap: Bitmap?) {
                    // Veraltet, wenn sich der Kanal inzwischen geändert hat
                    if (referent.channel?.hasSameReadOnlyInfo(channel) != true) return
                    referent.updateChannelLogo(bitmap, type)
                }
            }
    }
}
