package com.android.tv.common.util

import android.content.Intent
import android.media.tv.TvInputInfo
import com.android.tv.common.actions.InputSetupActionUtils

object CommonUtils {
    // Gebündelte (System-)Inputs bekommen höchste Priorität in der UI.
    private val BUNDLED_PACKAGE_SET = setOf("com.android.tv")

    @JvmStatic
    fun isBundledInput(inputId: String): Boolean =
        BUNDLED_PACKAGE_SET.any { inputId.startsWith("$it/") }

    @JvmStatic
    fun isInBundledPackageSet(packageName: String?): Boolean = packageName in BUNDLED_PACKAGE_SET

    /** Verpackt ein Setup-Intent in LAUNCH_INPUT_SETUP (sofern nicht schon so). */
    @JvmStatic
    fun createSetupIntent(originalSetupIntent: Intent?, inputId: String): Intent? {
        if (originalSetupIntent == null) return null
        if (InputSetupActionUtils.hasInputSetupAction(originalSetupIntent)) return Intent(originalSetupIntent)
        return Intent(InputSetupActionUtils.INTENT_ACTION_INPUT_SETUP).apply {
            putExtra(InputSetupActionUtils.EXTRA_SETUP_INTENT, originalSetupIntent)
            putExtra(InputSetupActionUtils.EXTRA_INPUT_ID, inputId)
        }
    }

    @JvmStatic
    fun createSetupIntent(input: TvInputInfo): Intent? = createSetupIntent(input.createSetupIntent(), input.id)

    /** Löscht Datei bzw. Ordner rekursiv. */
    @JvmStatic
    fun deleteDirOrFile(fileOrDirectory: java.io.File): Boolean {
        if (fileOrDirectory.isDirectory) fileOrDirectory.listFiles()?.forEach { deleteDirOrFile(it) }
        return fileOrDirectory.delete()
    }

    // Bugfix: Original-ThreadLocal gab allen Threads dieselbe SimpleDateFormat-Instanz zurück (nicht threadsicher).
    private val ISO_8601 = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
    }

    /** Wandelt Millisekunden in einen ISO-8601-Text um. */
    @JvmStatic
    fun toIsoDateTimeString(timeMillis: Long): String = ISO_8601.get()!!.format(java.util.Date(timeMillis))
}
