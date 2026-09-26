package com.android.tv.setup

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.tv.R
import com.android.tv.SetupPassthroughActivity
import com.android.tv.Starter
import com.android.tv.TvSingletons
import com.android.tv.common.CommonConstants
import com.android.tv.common.ui.setup.SetupActivity
import com.android.tv.common.ui.setup.SetupMultiPaneFragment
import com.android.tv.onboarding.SetupSourcesFragment
import com.android.tv.util.OnboardingUtils
import com.android.tv.util.SetupUtils

/** Kanalquellen-Einrichtung, aufgerufen aus den System-Einstellungen (LAUNCH_SYSTEM_SETUP). */
class SystemSetupActivity : SetupActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        if (intent.action != SYSTEM_SETUP) finish()
    }

    override fun onCreateInitialFragment(): Fragment = SetupSourcesFragment()

    private fun showMerchantCollection() {
        val onlineStoreIntent = OnboardingUtils.createOnlineStoreIntent()
        if (onlineStoreIntent != null) executeActionWithDelay(Runnable { startActivity(onlineStoreIntent) }, SHOW_RIPPLE_DURATION_MS)
        else Log.w(TAG, "Unable to show merchant collection, more channels url is not valid.")
    }

    override fun executeAction(category: String, actionId: Int, params: Bundle?): Boolean {
        if (category != SetupSourcesFragment.ACTION_CATEGORY) return false
        when (actionId) {
            SetupSourcesFragment.ACTION_ONLINE_STORE -> showMerchantCollection()
            SetupSourcesFragment.ACTION_SETUP_INPUT -> {
                val singletons = TvSingletons.getSingletons(this)
                val input = singletons.getTvInputManagerHelper()
                    .getTvInputInfo(params?.getString(SetupSourcesFragment.ACTION_PARAM_KEY_INPUT_ID))
                // Bugfix: unbekannter Input führte im Original zu NPE
                val intent = input?.let { singletons.getSetupUtils().createSetupIntent(this, it) }
                if (input == null || intent == null) {
                    Toast.makeText(this, R.string.msg_no_setup_activity, Toast.LENGTH_SHORT).show()
                    return true
                }
                intent.component = ComponentName(this, SetupPassthroughActivity::class.java)
                try {
                    SetupUtils.grantEpgPermission(this, input.serviceInfo.packageName)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_CODE_START_SETUP_ACTIVITY)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, getString(R.string.msg_unable_to_start_setup_activity, input.loadLabel(this)),
                        Toast.LENGTH_SHORT).show()
                }
            }
            SetupMultiPaneFragment.ACTION_DONE -> {
                setResult(RESULT_OK)
                finish()
            }
            else -> return false
        }
        return true
    }

    companion object {
        private const val TAG = "SystemSetupActivity"
        private const val SYSTEM_SETUP = CommonConstants.BASE_PACKAGE + ".action.LAUNCH_SYSTEM_SETUP"
        private const val SHOW_RIPPLE_DURATION_MS = 266
        private const val REQUEST_CODE_START_SETUP_ACTIVITY = 1
    }
}
