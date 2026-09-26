package com.android.tv.menu

import android.content.Context
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.common.customization.CustomAction
import com.android.tv.common.customization.CustomizationManager
import com.android.tv.ui.TunableTvView

/** Erzeugt die Menüzeilen (inkl. OEM-Anpassungen). */
class MenuRowFactory(
    private val mainActivity: MainActivity,
    private val tvView: TunableTvView,
    private val tvOptionsRowAdapterFactory: TvOptionsRowAdapter.Factory,
) {
    private val customizationManager = CustomizationManager(mainActivity).also { it.initialize() }

    fun createMenuRow(menu: Menu, key: Class<*>): MenuRow? = when (key) {
        PlayControlsRow::class.java -> PlayControlsRow(mainActivity, tvView, menu, mainActivity.timeShiftManager)
        ChannelsRow::class.java -> ChannelsRow(mainActivity, menu, mainActivity.programDataManager)
        PartnerRow::class.java -> {
            val customActions = customizationManager.getCustomActions(CustomizationManager.ID_PARTNER_ROW)
            val title = customizationManager.partnerRowTitle
            if (customActions != null && !title.isNullOrEmpty()) PartnerRow(mainActivity, menu, title, customActions) else null
        }
        TvOptionsRow::class.java -> TvOptionsRow(
            mainActivity, menu, customizationManager.getCustomActions(CustomizationManager.ID_OPTIONS_ROW),
            tvOptionsRowAdapterFactory)
        else -> null
    }

    class TvOptionsRow internal constructor(
        context: Context, menu: Menu, customActions: List<CustomAction>?, factory: TvOptionsRowAdapter.Factory,
    ) : ItemListRow(context, menu, R.string.menu_title_options, R.dimen.action_card_height,
        factory.create(context, customActions)) {
        companion object {
            val ID: String = TvOptionsRow::class.java.name
        }
    }

    class PartnerRow internal constructor(context: Context, menu: Menu, title: String, customActions: List<CustomAction>) :
        ItemListRow(context, menu, title, R.dimen.action_card_height, PartnerOptionsRowAdapter(context, customActions))
}
