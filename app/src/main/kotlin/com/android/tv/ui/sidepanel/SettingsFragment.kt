package com.android.tv.ui.sidepanel

import android.app.ApplicationErrorReport
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.tv.R
import com.android.tv.TvApplication
import com.android.tv.TvSingletons
import com.android.tv.features.TvFeatures
import com.android.tv.license.LicenseSideFragment
import com.android.tv.license.Licenses

/**
 * Einstellungen: Kanalliste anpassen, Kanalquellen, Feedback, Lizenzen, interaktive Apps, Version.
 * Entfällt: Kindersicherung (nur System-App) und Trickplay-Schalter (nur eingebauter Tuner).
 */
class SettingsFragment : SideFragment<Item>() {

    override fun getTitle(): String = getString(R.string.side_panel_title_settings)

    override fun getItemList(): List<Item> {
        val items = ArrayList<Item>()
        val activity = mainActivity
        val sideFragmentManager = activity.overlayManager.sideFragmentManager

        val customizeChannelListItem = object : SubMenuItem(
            getString(R.string.settings_channel_source_item_customize_channels),
            getString(R.string.settings_channel_source_item_customize_channels_description),
            sideFragmentManager,
        ) {
            override fun getFragment(): SideFragment<*> = CustomizeChannelListFragment()

            override fun onBind(view: View) {
                super.onBind(view)
                setEnabled(false)
            }

            override fun onUpdate() {
                super.onUpdate()
                setEnabled(channelDataManager.channelCount != 0)
            }
        }
        customizeChannelListItem.setEnabled(false)
        items.add(customizeChannelListItem)

        val hasNewInput = TvSingletons.getSingletons(requireContext()).getSetupUtils().hasNewInput(activity.tvInputManagerHelper)
        items.add(object : ActionItem(
            getString(R.string.settings_channel_source_item_setup),
            if (hasNewInput) getString(R.string.settings_channel_source_item_setup_new_inputs) else null,
        ) {
            override fun onSelected() {
                closeFragment()
                activity.overlayManager.showSetupFragment()
            }
        })

        items.add(object : ActionItem(getString(R.string.settings_send_feedback)) {
            override fun onSelected() {
                val intent = Intent().setComponent(ComponentName(FEEDBACK_PACKAGE, FEEDBACK_CLASS))
                val report = ApplicationErrorReport().apply {
                    packageName = requireContext().packageName
                    processName = packageName
                    time = System.currentTimeMillis()
                    type = ApplicationErrorReport.TYPE_NONE
                }
                intent.putExtra(Intent.EXTRA_BUG_REPORT, report)
                // Bugfix: ohne Google-Feedback-App stürzte das Original ab
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, 0)
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "Feedback app not available", e)
                    Toast.makeText(requireContext(), R.string.msg_missing_app, Toast.LENGTH_SHORT).show()
                }
            }
        })

        if (Licenses.hasLicenses(requireContext())) {
            items.add(object : SubMenuItem(getString(R.string.settings_menu_licenses), sideFragmentManager) {
                override fun getFragment(): SideFragment<*> = LicenseSideFragment()
            })
        }

        if (TvFeatures.hasTiaf()) {
            items.add(object : ActionItem(getString(R.string.interactive_app_settings)) {
                override fun onSelected() = sideFragmentManager.show(InteractiveAppSettingsFragment(), false)
            })
        }

        val version = SimpleActionItem(
            getString(R.string.settings_menu_version), (activity.applicationContext as TvApplication).versionName)
        version.setClickable(false)
        items.add(version)
        return items
    }

    override fun onResume() {
        super.onResume()
        if (channelDataManager.areAllChannelsHidden()) {
            Toast.makeText(activity, R.string.msg_all_channels_hidden, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "SettingsFragment"
        private const val FEEDBACK_PACKAGE = "com.google.android.feedback"
        private const val FEEDBACK_CLASS = "com.google.android.feedback.FeedbackActivity"
    }
}
