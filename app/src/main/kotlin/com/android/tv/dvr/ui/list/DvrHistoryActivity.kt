package com.android.tv.dvr.ui.list

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.android.tv.R
import com.android.tv.Starter

/** Activity mit dem Aufnahmeverlauf. Activity → FragmentActivity. */
class DvrHistoryActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        // null übergeben, damit Fragments nicht automatisch neu erzeugt werden.
        super.onCreate(null)
        setContentView(R.layout.activity_dvr_history)
        supportFragmentManager.beginTransaction().add(R.id.fragment_container, DvrHistoryFragment()).commit()
    }
}
