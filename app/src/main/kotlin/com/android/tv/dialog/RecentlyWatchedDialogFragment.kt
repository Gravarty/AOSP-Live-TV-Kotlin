package com.android.tv.dialog

import com.android.tv.data.WatchedPrograms
import android.app.AlertDialog
import android.app.Dialog
import android.database.Cursor
import android.media.tv.TvContract
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.android.tv.MainActivity
import com.android.tv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Liste der zuletzt gesehenen Sendungen (TvProvider). CursorLoader → Coroutine. */
class RecentlyWatchedDialogFragment : SafeDismissDialogFragment() {
    private var adapter: SimpleCursorAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val channelDataManager = (requireActivity() as MainActivity).channelDataManager
        val to = intArrayOf(
            R.id.watched_program_id, R.id.watched_program_channel_id,
            R.id.watched_program_watch_time, R.id.watched_program_title,
        )
        val adapter = SimpleCursorAdapter(requireActivity(), R.layout.list_item_watched_program, null, PROJECTION, to, 0)
        this.adapter = adapter
        adapter.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, columnIndex ->
            when (cursor.getColumnName(columnIndex)) {
                WatchedPrograms.COLUMN_CHANNEL_ID -> {
                    val channelId = cursor.getLong(columnIndex)
                    (view as TextView).text = channelId.toString()
                    val displayNumber = channelDataManager.getChannel(channelId)?.displayNumber ?: ""
                    (view.parent as View).findViewById<TextView>(R.id.watched_program_channel_display_number).text = displayNumber
                    true
                }
                WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS -> {
                    val time = cursor.getLong(columnIndex)
                    (view as TextView).text = DateUtils.getRelativeTimeSpanString(
                        time, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString()
                    true
                }
                else -> false
            }
        }
        loadWatchedPrograms()
        val listView = ListView(requireActivity()).apply { this.adapter = adapter }
        return AlertDialog.Builder(requireActivity()).setTitle(R.string.recently_watched).setView(listView).create()
    }

    private fun loadWatchedPrograms() {
        val resolver = requireActivity().contentResolver
        lifecycleScope.launch {
            val cursor: Cursor? = withContext(Dispatchers.IO) {
                try {
                    resolver.query(WatchedPrograms.CONTENT_URI, PROJECTION, null, null,
                        "${WatchedPrograms._ID} DESC")
                } catch (e: SecurityException) {
                    null // Sehverlauf nur mit Systemrecht lesbar
                }
            }
            adapter?.changeCursor(cursor) ?: cursor?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter?.changeCursor(null)
    }

    companion object {
        val DIALOG_TAG: String = RecentlyWatchedDialogFragment::class.java.simpleName
        private val PROJECTION = arrayOf(
            WatchedPrograms._ID,
            WatchedPrograms.COLUMN_CHANNEL_ID,
            WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS,
            WatchedPrograms.COLUMN_TITLE,
        )
    }
}
