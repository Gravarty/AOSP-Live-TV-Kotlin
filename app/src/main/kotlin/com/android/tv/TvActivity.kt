package com.android.tv

import android.app.Activity
import android.content.Intent
import com.android.tv.util.Utils

/** Launcher-Eintrag (kann ausgeblendet werden); startet MainActivity. */
class TvActivity : Activity() {
    override fun onStart() {
        super.onStart()
        startActivity(Intent(this, MainActivity::class.java).putExtra(Utils.EXTRA_KEY_FROM_LAUNCHER, true))
        finish()
    }
}
