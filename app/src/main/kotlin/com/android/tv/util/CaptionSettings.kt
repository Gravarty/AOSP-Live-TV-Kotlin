package com.android.tv.util

import android.content.Context
import android.os.LocaleList
import android.view.accessibility.CaptioningManager

/** Untertitel-Einstellung der App: System (Standard), aus oder an mit Sprache/Spur. */
class CaptionSettings(context: Context) {
    private val captioningManager = context.getSystemService(CaptioningManager::class.java)

    var enableOption = OPTION_SYSTEM
    private var languageInternal: String? = null
    var trackId: String? = null

    /** Bevorzugte Sprachen: Untertitel-Sprache des Systems, dann Systemsprachen. */
    val systemPreferenceLanguageList: List<String>
        get() {
            val list = ArrayList<String>()
            captioningManager.locale?.let { list.add(it.language) }
            val locales = LocaleList.getDefault()
            for (i in 0 until locales.size()) list.add(locales[i].language)
            return list
        }

    /** Sprache je nach Option (System: erste bevorzugte). */
    var language: String?
        get() = when (enableOption) {
            OPTION_SYSTEM -> systemPreferenceLanguageList.firstOrNull()
            OPTION_ON -> languageInternal
            else -> null
        }
        set(value) { languageInternal = value }

    val isSystemSettingEnabled: Boolean get() = captioningManager.isEnabled

    val isEnabled: Boolean
        get() = when (enableOption) {
            OPTION_SYSTEM -> isSystemSettingEnabled
            OPTION_ON -> true
            else -> false
        }

    companion object {
        const val OPTION_SYSTEM = 0
        const val OPTION_OFF = 1
        const val OPTION_ON = 2
    }
}
