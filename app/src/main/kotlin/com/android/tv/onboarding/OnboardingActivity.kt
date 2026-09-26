package com.android.tv.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import com.android.tv.R
import com.android.tv.SetupPassthroughActivity
import com.android.tv.Starter
import com.android.tv.TvSingletons
import com.android.tv.common.ui.setup.SetupActivity
import com.android.tv.common.ui.setup.SetupMultiPaneFragment
import com.android.tv.common.util.PermissionUtils
import com.android.tv.data.ChannelDataManager
import com.android.tv.util.OnboardingUtils
import com.android.tv.util.SetupUtils

/** Ersteinrichtung: Willkommen → Kanalquellen; fragt READ_TV_LISTINGS an. */
class OnboardingActivity : SetupActivity() {

    private lateinit var channelDataManager: ChannelDataManager
    private lateinit var setupUtils: SetupUtils

    private val channelListener = object : ChannelDataManager.Listener {
        override fun onLoadFinished() {
            channelDataManager.removeListener(this)
            setupUtils.markNewChannelsBrowsableIfEnabled()
        }
        override fun onChannelListUpdated() {}
        override fun onChannelBrowsableChanged() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        val singletons = TvSingletons.getSingletons(this)
        channelDataManager = singletons.getChannelDataManager()
        setupUtils = singletons.getSetupUtils()
        super.onCreate(savedInstanceState)
        if (hasListingsPermission()) {
            if (channelDataManager.isDbLoadFinished) setupUtils.markNewChannelsBrowsableIfEnabled()
            else channelDataManager.addListener(channelListener)
        } else {
            requestPermissions(arrayOf(PermissionUtils.PERMISSION_READ_TV_LISTINGS), PERMISSIONS_REQUEST_READ_TV_LISTINGS)
        }
    }

    private fun hasListingsPermission() = PermissionUtils.hasAccessAllEpg(this) || PermissionUtils.hasReadTvListings(this)

    override fun onDestroy() {
        if (::channelDataManager.isInitialized) channelDataManager.removeListener(channelListener)
        super.onDestroy()
    }

    override fun onCreateInitialFragment(): Fragment? {
        if (!hasListingsPermission()) return null // erst nach der Berechtigung
        return if (OnboardingUtils.isFirstRunWithCurrentVersion(this)) WelcomeFragment() else SetupSourcesFragment()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST_READ_TV_LISTINGS) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Neu starten, damit alles mit Berechtigung geladen wird
            finish()
            startActivity(buildIntent(this, intentAfterCompletion()))
        } else {
            Toast.makeText(this, R.string.msg_read_tv_listing_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun intentAfterCompletion(): Intent? = IntentCompat.getParcelableExtra(intent, KEY_INTENT_AFTER_COMPLETION, Intent::class.java)

    private fun finishActivity() {
        intentAfterCompletion()?.let { startActivity(it) }
        finish()
    }

    private fun showMerchantCollection() {
        val onlineStoreIntent = OnboardingUtils.createOnlineStoreIntent()
        if (onlineStoreIntent != null) {
            executeActionWithDelay(Runnable { startActivity(onlineStoreIntent) }, SHOW_RIPPLE_DURATION_MS)
        } else {
            Log.w(TAG, "Unable to show merchant collection, more channels url is not valid.")
        }
    }

    override fun executeAction(category: String, actionId: Int, params: Bundle?): Boolean {
        when (category) {
            WelcomeFragment.ACTION_CATEGORY -> if (actionId == WelcomeFragment.ACTION_NEXT) {
                OnboardingUtils.setFirstRunWithCurrentVersionCompleted(this)
                showFragment(SetupSourcesFragment(), false)
                return true
            }
            SetupSourcesFragment.ACTION_CATEGORY -> when (actionId) {
                SetupSourcesFragment.ACTION_ONLINE_STORE -> {
                    showMerchantCollection()
                    return true
                }
                SetupSourcesFragment.ACTION_SETUP_INPUT -> {
                    val inputId = params?.getString(SetupSourcesFragment.ACTION_PARAM_KEY_INPUT_ID)
                    val input = TvSingletons.getSingletons(this).getTvInputManagerHelper().getTvInputInfo(inputId)
                    // Bugfix: unbekannter Input führte im Original zu NPE
                    val intent = input?.let { setupUtils.createSetupIntent(this, it) }
                    if (input == null || intent == null) {
                        Toast.makeText(this, R.string.msg_no_setup_activity, Toast.LENGTH_SHORT).show()
                        return true
                    }
                    // Nach der Einrichtung Kanäle freischalten
                    intent.component = ComponentName(this, SetupPassthroughActivity::class.java)
                    try {
                        SetupUtils.grantEpgPermission(this, input.serviceInfo.packageName)
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQUEST_CODE_START_SETUP_ACTIVITY)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, getString(R.string.msg_unable_to_start_setup_activity, input.loadLabel(this)),
                            Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                SetupMultiPaneFragment.ACTION_DONE -> {
                    if (channelDataManager.channelCount == 0) finish() else finishActivity()
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val KEY_INTENT_AFTER_COMPLETION = "key_intent_after_completion"
        private const val PERMISSIONS_REQUEST_READ_TV_LISTINGS = 1
        private const val SHOW_RIPPLE_DURATION_MS = 266
        private const val REQUEST_CODE_START_SETUP_ACTIVITY = 1

        @JvmStatic
        fun buildIntent(context: Context, intentAfterCompletion: Intent?): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(KEY_INTENT_AFTER_COMPLETION, intentAfterCompletion)
    }
}
