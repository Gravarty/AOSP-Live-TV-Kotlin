package com.android.tv.license

import android.content.Context
import com.android.tv.R
import com.android.tv.ui.sidepanel.ActionItem
import com.android.tv.ui.sidepanel.SideFragment

/** Seitenleiste "Open-Source-Lizenzen". */
class LicenseSideFragment : SideFragment<LicenseSideFragment.LicenseActionItem>() {
    private var licenses: List<LicenseActionItem> = emptyList()

    inner class LicenseActionItem(private val license: License) : ActionItem(license.libraryName) {
        override fun onSelected() {
            mainActivity.overlayManager.showDialogFragment(LicenseDialogFragment.DIALOG_TAG,
                LicenseDialogFragment.newInstance(license), true)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        licenses = Licenses.getLicenses(context).map { LicenseActionItem(it) }
    }

    override fun getTitle(): String = resources.getString(R.string.settings_menu_licenses)
    override fun getItemList(): List<LicenseActionItem> = licenses
}
