package com.example.cuidaleapp.utils

object PhoneUtils {
    fun isValidChileanPhone(phone: String): Boolean {
        if (phone.isEmpty()) return true
        val clean = phone.replace(Regex("[^0-9]"), "")
        return clean.length == 9 && clean.startsWith("9")
    }
    fun formatChileanPhone(phone: String): String {
        val clean = phone.replace(Regex("[^0-9]"), "")
        return if (clean.length >= 9) {
            clean.takeLast(9)
        } else {
            clean
        }
    }

    fun formatForFirebase(phone: String): String {
        val clean = phone.replace(Regex("[^0-9]"), "")
        val last9 = if (clean.length >= 9) clean.takeLast(9) else clean
        return "+56$last9"
    }
}
