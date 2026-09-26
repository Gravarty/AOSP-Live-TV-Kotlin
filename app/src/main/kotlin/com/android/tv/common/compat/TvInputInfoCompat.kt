package com.android.tv.common.compat

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputService
import android.util.Log
import org.xmlpull.v1.XmlPullParser

/** 1:1-Port: liest <extra>-Einträge aus der tv-input-XML eines Inputs. */
class TvInputInfoCompat(private val context: Context, val tvInputInfo: TvInputInfo) {

    val type: Int get() = tvInputInfo.type

    val isAudioOnly: Boolean by lazy { extras[ATTRIBUTE_NAME_AUDIO_ONLY].toBoolean() }

    val extras: Map<String, String>
        get() {
            val si = tvInputInfo.serviceInfo
            try {
                val parser = si.loadXmlMetaData(context.packageManager, TvInputService.SERVICE_META_DATA)
                    ?: return emptyMap()
                var type: Int
                do {
                    type = parser.next()
                } while (type != XmlPullParser.END_DOCUMENT && type != XmlPullParser.START_TAG)

                if (TV_INPUT_XML_START_TAG_NAME != parser.name) {
                    Log.w(TAG, "Meta-data does not start with $TV_INPUT_XML_START_TAG_NAME tag for ${si.name}")
                    return emptyMap()
                }
                val extras = HashMap<String, String>()
                while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                    if (type == XmlPullParser.END_TAG && TV_INPUT_XML_START_TAG_NAME == parser.name) {
                        return extras
                    }
                    if (type == XmlPullParser.START_TAG && TV_INPUT_EXTRA_XML_START_TAG_NAME == parser.name) {
                        val name = parser.getAttributeValue(ATTRIBUTE_NAMESPACE_ANDROID, ATTRIBUTE_NAME)
                        val value = parser.getAttributeValue(ATTRIBUTE_NAMESPACE_ANDROID, ATTRIBUTE_VALUE)
                        if (name != null && value != null) extras[name] = value
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get extras of ${tvInputInfo.id}", e)
            }
            return emptyMap()
        }

    companion object {
        private const val TAG = "TvInputInfoCompat"
        private const val ATTRIBUTE_NAMESPACE_ANDROID = "http://schemas.android.com/apk/res/android"
        private const val TV_INPUT_XML_START_TAG_NAME = "tv-input"
        private const val TV_INPUT_EXTRA_XML_START_TAG_NAME = "extra"
        private const val ATTRIBUTE_NAME = "name"
        private const val ATTRIBUTE_VALUE = "value"
        private const val ATTRIBUTE_NAME_AUDIO_ONLY = "com.android.tv.common.compat.tvinputinfocompat.audioOnly"
    }
}
