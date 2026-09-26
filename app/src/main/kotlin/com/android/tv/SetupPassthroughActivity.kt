package com.android.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.tv.TvInputInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.android.tv.common.CommonConstants
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.actions.InputSetupActionUtils
import com.android.tv.util.SetupUtils

/**
 * Leitet die Einrichtung eines Inputs weiter und schaltet danach dessen Kanäle frei.
 * Nur Aufrufe aus dieser App werden weitergeleitet. Entfällt: EPG-Abruf während des
 * Suchlaufs und Cloud-EPG (nur eingebauter Tuner).
 */
class SetupPassthroughActivity : Activity() {
    private var tvInputInfo: TvInputInfo? = null
    private var activityAfterCompletion: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        val intent = intent
        val inputId = intent.getStringExtra(InputSetupActionUtils.EXTRA_INPUT_ID)
        val singletons = TvSingletons.getSingletons(this)
        tvInputInfo = singletons.getTvInputManagerHelper().getTvInputInfo(inputId)
        activityAfterCompletion = InputSetupActionUtils.getExtraActivityAfter(intent)
        if (savedInstanceState != null) return
        SoftPreconditions.checkArgument(InputSetupActionUtils.hasInputSetupAction(intent), TAG,
            "Unsupported action %s", intent.action)
        val input = tvInputInfo
        if (input == null) {
            Log.w(TAG, "There is no input with the ID $inputId.")
            finish()
            return
        }
        val extras = intent.extras
        if (extras == null) {
            Log.w(TAG, "There is no extra info in the intent")
            finish()
            return
        }
        val setupIntent = InputSetupActionUtils.getExtraSetupIntent(intent)
        if (setupIntent == null) {
            Log.w(TAG, "The input (${input.id}) doesn't have setup.")
            finish()
            return
        }
        InputSetupActionUtils.removeSetupIntent(extras)
        setupIntent.putExtras(extras)
        try {
            // Sicherheit: nur Aufrufe aus der eigenen App weiterleiten
            if (Build.VERSION.SDK_INT >= 34) {
                val callingPackage = launchedFromPackage
                if (callingPackage != CommonConstants.BASE_PACKAGE) {
                    Log.w(TAG, "Calling package $callingPackage is not trusted. Not forwarding intent.")
                    finish()
                    return
                }
            }
            val callingActivity = callingActivity
            if (callingActivity == null || callingActivity.packageName != CommonConstants.BASE_PACKAGE) {
                Log.w(TAG, "Calling activity ${callingActivity?.packageName ?: "null"} is not trusted. Not forwarding intent.")
                finish()
                return
            }
            SetupUtils.grantEpgPermission(this, input.serviceInfo.packageName)
            @Suppress("DEPRECATION")
            startActivityForResult(setupIntent, REQUEST_START_SETUP_ACTIVITY)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Can't find activity: ${setupIntent.component}")
            finish()
        }
    }

    @Deprecated("Wie im Original")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val setupComplete = requestCode == REQUEST_START_SETUP_ACTIVITY && resultCode == RESULT_OK
        val input = tvInputInfo
        if (!setupComplete || input == null) {
            if (setupComplete) Log.w(TAG, "There is no input with ID ${intent.getStringExtra(InputSetupActionUtils.EXTRA_INPUT_ID)}.")
            setResult(resultCode, data)
            finish()
            return
        }
        TvSingletons.getSingletons(this).getSetupUtils().onTvInputSetupFinished(input.id) {
            activityAfterCompletion?.let {
                try {
                    startActivity(it)
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Activity launch failed", e)
                }
            }
            setResult(resultCode, data)
            finish()
        }
    }

    companion object {
        private const val TAG = "SetupPassthroughAct"
        private const val REQUEST_START_SETUP_ACTIVITY = 200
    }
}
