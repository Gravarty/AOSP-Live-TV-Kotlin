package com.android.tv

import android.app.Activity
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import com.android.tv.data.ChannelImpl
import com.android.tv.ui.SelectInputView
import com.android.tv.util.Utils

/** Eingangsauswahl außerhalb der App (Input-Taste). */
class SelectInputActivity : Activity() {
    private lateinit var selectInputView: SelectInputView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (applicationContext as TvApplication).setSelectInputActivity(this)
        setContentView(R.layout.activity_select_input)
        selectInputView = findViewById(R.id.scene_transition_common)
        selectInputView.setOnInputSelectedCallback(object : SelectInputView.OnInputSelectedCallback {
            override fun onTunerInputSelected() = startTvWithChannel(TvContract.Channels.CONTENT_URI)
            override fun onPassthroughInputSelected(input: TvInputInfo) =
                startTvWithChannel(TvContract.buildChannelUriForPassthroughInput(input.id))

            private fun startTvWithChannel(channelUri: Uri) {
                startActivity(Intent(Intent.ACTION_VIEW, channelUri, this@SelectInputActivity, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
        })
        // Zuletzt gesehenen Passthrough-Eingang vorwählen
        Utils.getLastWatchedChannelUri(this)?.let { Uri.parse(it) }?.let { uri ->
            if (TvContract.isChannelUriForPassthroughInput(uri)) selectInputView.setCurrentChannel(ChannelImpl.createPassthroughChannel(uri))
        }
    }

    override fun onResume() {
        super.onResume()
        selectInputView.onEnterAction(true)
    }

    override fun onPause() {
        selectInputView.onExitAction()
        super.onPause()
    }

    override fun onDestroy() {
        (applicationContext as TvApplication).setSelectInputActivity(null)
        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_I || keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            selectInputView.onKeyUp(keyCode, event)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
