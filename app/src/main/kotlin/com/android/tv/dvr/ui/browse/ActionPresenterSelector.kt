package com.android.tv.dvr.ui.browse

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.leanback.widget.Action
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import com.android.tv.R

/** Wählt für Aktionen den ein- oder zweizeiligen Presenter (zweizeilig bei Label2 oder Icon). */
class ActionPresenterSelector : PresenterSelector() {
    private val oneLineActionPresenter: Presenter = OneLineActionPresenter()
    private val twoLineActionPresenter: Presenter = TwoLineActionPresenter()
    private val presenters = arrayOf(oneLineActionPresenter, twoLineActionPresenter)

    override fun getPresenter(item: Any?): Presenter {
        val action = item as Action
        return if (TextUtils.isEmpty(action.label2) && action.icon == null) {
            oneLineActionPresenter
        } else {
            twoLineActionPresenter
        }
    }

    override fun getPresenters(): Array<Presenter> = presenters

    internal class ActionViewHolder(view: View, val layoutDirection: Int) : Presenter.ViewHolder(view) {
        var action: Action? = null
        val button: Button = view.findViewById(R.id.lb_action_button)
    }

    // Im Original innere (nicht statische) Klassen; der äußere Bezug wird nicht benötigt.
    private class OneLineActionPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.lb_action_1_line, parent, false)
            return ActionViewHolder(v, parent.layoutDirection)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
            val action = item as Action
            val vh = viewHolder as ActionViewHolder
            vh.action = action
            vh.button.text = action.label1
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
            (viewHolder as ActionViewHolder).action = null
        }
    }

    private class TwoLineActionPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.lb_action_2_lines, parent, false)
            return ActionViewHolder(v, parent.layoutDirection)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
            val action = item as Action
            val vh = viewHolder as ActionViewHolder
            val icon = action.icon
            vh.action = action
            val res = vh.view.resources
            if (icon != null) {
                val startPadding = res.getDimensionPixelSize(R.dimen.lb_action_with_icon_padding_start)
                val endPadding = res.getDimensionPixelSize(R.dimen.lb_action_with_icon_padding_end)
                vh.view.setPaddingRelative(startPadding, 0, endPadding, 0)
            } else {
                val padding = res.getDimensionPixelSize(R.dimen.lb_action_padding_horizontal)
                vh.view.setPaddingRelative(padding, 0, padding, 0)
            }
            vh.button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)

            val line1 = action.label1
            val line2 = action.label2
            vh.button.text = when {
                TextUtils.isEmpty(line1) -> line2
                TextUtils.isEmpty(line2) -> line1
                else -> "$line1\n$line2"
            }
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
            val vh = viewHolder as ActionViewHolder
            vh.button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            vh.view.setPadding(0, 0, 0, 0)
            vh.action = null
        }
    }
}
