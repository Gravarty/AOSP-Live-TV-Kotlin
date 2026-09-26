package com.android.tv.ui

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.widget.TextClock

/** Uhr mit Datum im Format der Gerätesprache ("hm MMMd" bzw. "Hm MMMd"). */
class LocaleAwareTextClock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : TextClock(context, attrs, defStyleAttr) {
    init {
        format12Hour = DateFormat.getBestDateTimePattern(textLocale, "hm MMMd")
        format24Hour = DateFormat.getBestDateTimePattern(textLocale, "Hm MMMd")
    }
}
