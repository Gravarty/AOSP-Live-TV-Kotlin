package com.android.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Startet eine fremde Activity über einen Zwischenschritt, damit MainActivity bei fehlender App
 * eine Fehlermeldung (ERROR_MESSAGE) statt eines Absturzes bekommt.
 */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = IntentCompat.getParcelableExtra(getIntent(), EXTRA_INTENT, Intent::class.java)
        val requestResult = getIntent().getBooleanExtra(EXTRA_REQUEST_RESULT, false)
        try {
            if (requestResult) {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_START_ACTIVITY)
            } else {
                startActivity(intent)
                setResult(RESULT_OK)
                finish()
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Activity not found for $intent")
            intent?.putExtra(ERROR_MESSAGE, resources.getString(R.string.msg_missing_app))
            setResult(RESULT_CANCELED, intent)
            finish()
        }
    }

    @Deprecated("Wie im Original")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        setResult(resultCode, data)
        finish()
    }

    companion object {
        private const val TAG = "LauncherActivity"
        const val ERROR_MESSAGE = "com.android.tv.LauncherActivity.ErrorMessage"
        private const val REQUEST_CODE_DEFAULT = 0
        private const val REQUEST_START_ACTIVITY = 100
        private const val EXTRA_INTENT = "com.android.tv.LauncherActivity.INTENT"
        private const val EXTRA_REQUEST_RESULT = "com.android.tv.LauncherActivity.REQUEST_RESULT"

        /** Startet [intentToLaunch]; das Ergebnis kommt als REQUEST_CODE_DEFAULT zurück. */
        @JvmStatic
        fun startActivitySafe(baseActivity: Activity, intentToLaunch: Intent) {
            @Suppress("DEPRECATION")
            baseActivity.startActivityForResult(createIntent(baseActivity, intentToLaunch, false), REQUEST_CODE_DEFAULT)
        }

        private fun createIntent(context: Context, intentToLaunch: Intent, requestResult: Boolean) =
            Intent(context, LauncherActivity::class.java).apply {
                putExtra(EXTRA_INTENT, intentToLaunch)
                if (requestResult) putExtra(EXTRA_REQUEST_RESULT, true)
            }
    }
}
