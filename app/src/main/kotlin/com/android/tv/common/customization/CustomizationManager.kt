package com.android.tv.common.customization

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.android.tv.common.CommonConstants

/**
 * Menü-Anpassungen durch ein OEM-Paket mit der Berechtigung CUSTOMIZE_TV_APP
 * (zusätzliche Einträge in Optionen-/Partner-Zeile, Titel der Partner-Zeile).
 * Entfällt: Trickplay-Modus und Linux-DVB-Tuner (nur eingebauter Tuner).
 */
class CustomizationManager(private val context: Context) {
    private var initialized = false
    var partnerRowTitle: String? = null
        private set
    private val rowIdToCustomActionsMap = HashMap<String, MutableList<CustomAction>>()

    fun initialize() {
        if (initialized) return
        initialized = true
        if (!getCustomizationPackageName(context).isNullOrEmpty()) {
            buildCustomActions()
            buildPartnerRow()
        }
    }

    private fun buildCustomActions() {
        rowIdToCustomActionsMap.clear()
        val pm = context.packageManager
        for ((intentCategory, rowId) in INTENT_CATEGORY_TO_ROW_ID) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(intentCategory)
            val activities = pm.queryIntentActivities(
                query, PackageManager.GET_RECEIVERS or PackageManager.GET_RESOLVED_FILTER or PackageManager.GET_META_DATA)
            for (info in activities) {
                val packageName = info.activityInfo.packageName
                if (packageName != customizationPackage) {
                    Log.w(TAG, "A customization package $customizationPackage does not match with $packageName")
                    continue
                }
                val intent = Intent(Intent.ACTION_MAIN).addCategory(intentCategory)
                    .setClassName(customizationPackage!!, info.activityInfo.name)
                rowIdToCustomActionsMap.getOrPut(rowId) { ArrayList() }.add(
                    CustomAction(info.filter?.priority ?: 0, info.loadLabel(pm).toString(), info.loadIcon(pm), intent))
            }
        }
        rowIdToCustomActionsMap.values.forEach { it.sort() }
    }

    fun getCustomActions(rowId: String): List<CustomAction>? = rowIdToCustomActionsMap[rowId]

    private fun buildPartnerRow() {
        partnerRowTitle = null
        val pkg = customizationPackage ?: return
        val res = try {
            context.packageManager.getResourcesForApplication(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not get resources for package $pkg")
            return
        }
        val resId = res.getIdentifier(RES_ID_PARTNER_ROW_TITLE, RES_TYPE_STRING, pkg)
        if (resId != 0) partnerRowTitle = res.getString(resId)
    }

    companion object {
        private const val TAG = "CustomizationManager"
        private val CUSTOMIZE_PERMISSIONS = arrayOf(CommonConstants.BASE_PACKAGE + ".permission.CUSTOMIZE_TV_APP")
        private const val CATEGORY_TV_CUSTOMIZATION = CommonConstants.BASE_PACKAGE + ".category"
        const val ID_OPTIONS_ROW = "options_row"
        const val ID_PARTNER_ROW = "partner_row"
        private val INTENT_CATEGORY_TO_ROW_ID = mapOf(
            "$CATEGORY_TV_CUSTOMIZATION.OPTIONS_ROW" to ID_OPTIONS_ROW,
            "$CATEGORY_TV_CUSTOMIZATION.PARTNER_ROW" to ID_PARTNER_ROW,
        )
        private const val RES_ID_PARTNER_ROW_TITLE = "partner_row_title"
        private const val RES_TYPE_STRING = "string"
        private var customizationPackage: String? = null

        private fun getCustomizationPackageName(context: Context): String? {
            if (customizationPackage == null) {
                customizationPackage = getCustomizationPackageName(
                    context.packageManager.getPackagesHoldingPermissions(CUSTOMIZE_PERMISSIONS, 0))
            }
            return customizationPackage
        }

        /** Nicht-"com.android"-Paket bevorzugen, sonst das erste, sonst "". */
        internal fun getCustomizationPackageName(packageInfos: List<PackageInfo>): String {
            val names = packageInfos.map { it.packageName }
            return names.firstOrNull { !it.startsWith("com.android") } ?: names.firstOrNull() ?: ""
        }
    }
}
