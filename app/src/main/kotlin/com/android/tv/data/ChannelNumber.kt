package com.android.tv.data

import android.view.KeyEvent
import com.android.tv.common.util.StringUtils
import com.android.tv.data.api.Channel

/** 1:1-Port von com.android.tv.data.ChannelNumber. */
class ChannelNumber : Comparable<ChannelNumber> {
    @JvmField var majorNumber: String = ""
    @JvmField var hasDelimiter: Boolean = false
    @JvmField var minorNumber: String = ""

    fun reset() {
        majorNumber = ""
        hasDelimiter = false
        minorNumber = ""
    }

    override fun toString(): String =
        if (hasDelimiter) "$majorNumber${Channel.CHANNEL_NUMBER_DELIMITER}$minorNumber" else majorNumber

    override fun compareTo(other: ChannelNumber): Int {
        val major = majorNumber.toInt()
        val minor = if (hasDelimiter) minorNumber.toInt() else 0
        val otherMajor = other.majorNumber.toInt()
        val otherMinor = if (other.hasDelimiter) other.minorNumber.toInt() else 0
        return if (major == otherMajor) minor - otherMinor else major - otherMajor
    }

    override fun equals(other: Any?): Boolean =
        other is ChannelNumber &&
            majorNumber == other.majorNumber &&
            minorNumber == other.minorNumber &&
            hasDelimiter == other.hasDelimiter

    override fun hashCode(): Int = java.util.Objects.hash(majorNumber, hasDelimiter, minorNumber)

    companion object {
        private val CHANNEL_DELIMITER_KEYCODES = intArrayOf(
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_NUMPAD_DOT,
            KeyEvent.KEYCODE_SPACE,
        )

        @JvmStatic
        fun equivalent(lhs: String?, rhs: String?): Boolean {
            if (compare(lhs, rhs) == 0) return true
            // Treffer, wenn nur eine Seite ein Trennzeichen hat
            val l = parseChannelNumber(lhs)
            val r = parseChannelNumber(rhs)
            return l != null && r != null &&
                l.hasDelimiter != r.hasDelimiter &&
                l.majorNumber == r.majorNumber
        }

        @JvmStatic
        fun isChannelNumberDelimiterKey(keyCode: Int): Boolean =
            keyCode in CHANNEL_DELIMITER_KEYCODES

        @JvmStatic
        fun parseChannelNumber(number: String?): ChannelNumber? {
            if (number == null) return null
            val ret = ChannelNumber()
            val idx = number.indexOf(Channel.CHANNEL_NUMBER_DELIMITER)
            when {
                idx == 0 || idx == number.length - 1 -> return null
                idx < 0 -> {
                    ret.majorNumber = number
                    if (!isInteger(ret.majorNumber)) return null
                }
                else -> {
                    ret.hasDelimiter = true
                    ret.majorNumber = number.substring(0, idx)
                    ret.minorNumber = number.substring(idx + 1)
                    if (!isInteger(ret.majorNumber) || !isInteger(ret.minorNumber)) return null
                }
            }
            return ret
        }

        @JvmStatic
        fun compare(lhs: String?, rhs: String?): Int {
            val l = parseChannelNumber(lhs)
            val r = parseChannelNumber(rhs)
            // null zuerst
            return when {
                l == null && r == null -> StringUtils.compare(lhs, rhs)
                l == null -> -1
                r == null -> 1
                else -> l.compareTo(r)
            }
        }

        private fun isInteger(s: String): Boolean = s.toIntOrNull() != null
    }
}
