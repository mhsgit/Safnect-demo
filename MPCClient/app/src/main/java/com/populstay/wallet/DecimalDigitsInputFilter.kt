package com.populstay.wallet


import android.text.InputFilter
import android.text.Spanned

class DecimalDigitsInputFilter(private val decimalDigits: Int) : InputFilter {
    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val currentText = dest.toString()
        val newText = currentText.substring(0, dstart) + source + currentText.substring(dend)

        return if (isValidDecimal(newText)) {
            null
        } else {
            ""
        }
    }

    private fun isValidDecimal(text: String): Boolean {
        val decimalSeparator = getDecimalSeparator()
        val parts = text.split(decimalSeparator)
        return when {
            parts.size > 2 -> false // 输入包含多个小数点
            parts.size == 2 -> parts[1].length <= decimalDigits // 包含小数部分
            else -> true // 只有整数部分
        }
    }

    private fun getDecimalSeparator(): String {
        return java.text.DecimalFormatSymbols().decimalSeparator.toString()
    }
}

