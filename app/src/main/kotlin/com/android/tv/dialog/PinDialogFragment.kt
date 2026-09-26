package com.android.tv.dialog

import android.app.ActivityManager
import android.app.Dialog
import android.content.DialogInterface
import android.media.tv.TvContentRating
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.android.tv.R
import com.android.tv.common.SoftPreconditions
import com.android.tv.dialog.picker.TvPinPicker
import com.android.tv.util.TvSettings

/**
 * PIN-Dialog: Entsperren (Kanal/Sendung/Aufnahme), PIN abfragen oder neu setzen.
 * 5 Fehlversuche sperren die Eingabe für 1 Minute.
 * Bugfix: Beim Entsperren einer Aufnahme mit Altersfreigabe zeigte das Original ohne
 * Rating-Systeme "null" als Namen – jetzt der allgemeine Text "Sendung entsperren".
 */
class PinDialogFragment : SafeDismissDialogFragment() {

    fun interface OnPinCheckedListener {
        fun onPinChecked(checked: Boolean, type: Int, rating: String?)
    }

    var type = 0
        private set
    private var requestType = 0
    private var pinChecked = false
    private var dismissSilently = false
    private lateinit var wrongPinView: TextView
    private lateinit var enterPinView: View
    private lateinit var titleView: TextView
    private lateinit var tvPinPicker: TvPinPicker
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireActivity()) }
    private var prevPin: String? = null
    private var pin: String? = null
    private var ratingString: String? = null
    private var wrongPinCount = 0
    private var disablePinUntil = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestType = requireArguments().getInt(ARGS_TYPE, PIN_DIALOG_TYPE_ENTER_PIN)
        type = requestType
        ratingString = requireArguments().getString(ARGS_RATING)
        setStyle(DialogFragment.STYLE_NO_TITLE, 0)
        disablePinUntil = TvSettings.getDisablePinUntil(requireActivity())
        // Monkey-Tests nicht an der PIN hängen lassen
        if (ActivityManager.isUserAMonkey() && Math.random() < 0.5) exit(true)
        pinChecked = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            window?.attributes?.windowAnimations = R.style.pin_dialog_animation
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.pin_dialog_width), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.pin_dialog, container, false)
        wrongPinView = v.findViewById(R.id.wrong_pin)
        enterPinView = v.findViewById(R.id.enter_pin)
        titleView = enterPinView.findViewById(R.id.title)
        tvPinPicker = v.findViewById(R.id.tv_pin_picker)
        tvPinPicker.setOnClickListener {
            val input = tvPinPicker.pin
            if (!input.isNullOrEmpty()) done(input)
        }
        if (getPin().isEmpty()) type = PIN_DIALOG_TYPE_NEW_PIN
        when (type) {
            PIN_DIALOG_TYPE_UNLOCK_CHANNEL -> titleView.setText(R.string.pin_enter_unlock_channel)
            PIN_DIALOG_TYPE_UNLOCK_PROGRAM -> titleView.setText(R.string.pin_enter_unlock_program)
            PIN_DIALOG_TYPE_UNLOCK_DVR -> {
                val rating = TvContentRating.unflattenFromString(ratingString)
                titleView.text = if (rating == TvContentRating.UNRATED) getString(R.string.pin_enter_unlock_dvr_unrated)
                else getString(R.string.pin_enter_unlock_program)
            }
            PIN_DIALOG_TYPE_ENTER_PIN -> titleView.setText(R.string.pin_enter_pin)
            PIN_DIALOG_TYPE_NEW_PIN -> if (getPin().isEmpty()) {
                titleView.setText(R.string.pin_enter_create_pin)
            } else {
                titleView.setText(R.string.pin_enter_old_pin)
                type = PIN_DIALOG_TYPE_OLD_PIN
            }
        }
        if (type != PIN_DIALOG_TYPE_NEW_PIN) updateWrongPin()
        tvPinPicker.requestFocus()
        return v
    }

    /** Zeigt nach zu vielen Fehlversuchen den Countdown statt der Eingabe. */
    private fun updateWrongPin() {
        if (activity == null) {
            handler.removeCallbacksAndMessages(null)
            return
        }
        val remainingSeconds = ((disablePinUntil - System.currentTimeMillis()) / 1000).toInt()
        if (remainingSeconds < 1) {
            wrongPinView.visibility = View.INVISIBLE
            enterPinView.visibility = View.VISIBLE
            wrongPinCount = 0
        } else {
            enterPinView.visibility = View.INVISIBLE
            wrongPinView.visibility = View.VISIBLE
            wrongPinView.text = resources.getQuantityString(R.plurals.pin_enter_countdown, remainingSeconds, remainingSeconds)
            handler.postDelayed(::updateWrongPin, 1000)
        }
    }

    private fun exit(pinChecked: Boolean) {
        this.pinChecked = pinChecked
        dismiss()
    }

    /** Schließen, ohne den Listener aufzurufen. */
    fun dismissSilently() {
        dismissSilently = true
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val listener = activity as? OnPinCheckedListener
        SoftPreconditions.checkState(listener != null, TAG, "activity is not OnPinCheckedListener")
        if (!dismissSilently) listener?.onPinChecked(pinChecked, requestType, ratingString)
        dismissSilently = false
    }

    private fun handleWrongPin() {
        if (++wrongPinCount >= MAX_WRONG_PIN_COUNT) {
            disablePinUntil = System.currentTimeMillis() + DISABLE_PIN_DURATION_MILLIS
            TvSettings.setDisablePinUntil(requireActivity(), disablePinUntil)
            updateWrongPin()
        } else {
            showToast(R.string.pin_toast_wrong)
        }
    }

    private fun showToast(resId: Int) = Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show()

    private fun done(input: String) {
        when (type) {
            PIN_DIALOG_TYPE_UNLOCK_CHANNEL, PIN_DIALOG_TYPE_UNLOCK_PROGRAM, PIN_DIALOG_TYPE_UNLOCK_DVR, PIN_DIALOG_TYPE_ENTER_PIN -> {
                // Ohne gesetzte PIN gilt jede Eingabe
                if (getPin().isEmpty() || input == getPin()) {
                    exit(true)
                } else {
                    tvPinPicker.resetPin()
                    handleWrongPin()
                }
            }
            PIN_DIALOG_TYPE_NEW_PIN -> {
                tvPinPicker.resetPin()
                val prev = prevPin
                when {
                    prev == null -> {
                        prevPin = input
                        titleView.setText(R.string.pin_enter_again)
                    }
                    input == prev -> {
                        setPin(input)
                        exit(true)
                    }
                    else -> {
                        titleView.setText(if (getPin().isEmpty()) R.string.pin_enter_create_pin else R.string.pin_enter_new_pin)
                        prevPin = null
                        showToast(R.string.pin_toast_not_match)
                    }
                }
            }
            PIN_DIALOG_TYPE_OLD_PIN -> {
                // Beim Ändern: erst alte PIN prüfen
                tvPinPicker.resetPin()
                if (input == getPin()) {
                    type = PIN_DIALOG_TYPE_NEW_PIN
                    titleView.setText(R.string.pin_enter_new_pin)
                } else {
                    handleWrongPin()
                }
            }
        }
    }

    private fun setPin(pin: String) {
        this.pin = pin
        sharedPreferences.edit().putString(TvSettings.PREF_PIN, pin).apply()
    }

    private fun getPin(): String = pin ?: sharedPreferences.getString(TvSettings.PREF_PIN, "").orEmpty().also { pin = it }

    companion object {
        private const val TAG = "PinDialogFragment"
        const val PIN_DIALOG_TYPE_UNLOCK_CHANNEL = 0
        const val PIN_DIALOG_TYPE_UNLOCK_PROGRAM = 1
        const val PIN_DIALOG_TYPE_ENTER_PIN = 2
        const val PIN_DIALOG_TYPE_NEW_PIN = 3
        private const val PIN_DIALOG_TYPE_OLD_PIN = 4
        const val PIN_DIALOG_TYPE_UNLOCK_DVR = 5
        private const val MAX_WRONG_PIN_COUNT = 5
        private const val DISABLE_PIN_DURATION_MILLIS = 60 * 1000L
        private const val ARGS_TYPE = "args_type"
        private const val ARGS_RATING = "args_rating"
        val DIALOG_TAG: String = PinDialogFragment::class.java.name

        @JvmStatic
        @JvmOverloads
        fun create(type: Int, rating: String? = null) = PinDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(ARGS_TYPE, type)
                putString(ARGS_RATING, rating)
            }
        }
    }
}
