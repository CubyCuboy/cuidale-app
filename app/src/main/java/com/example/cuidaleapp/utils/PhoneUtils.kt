package com.example.cuidaleapp.utils

object PhoneUtils {
    /**
     * Sanitize and format phone number for Chile (+56).
     * If it starts with +56, it's kept.
     * If it starts with 56, + is added.
     * Otherwise, +569 is prefixed if it has 8 or 9 digits, or just +56 if it's longer.
     */
    fun formatChileanPhone(phone: String): String {
        val clean = phone.replace(Regex("[^0-9+]"), "")
        if (clean.isEmpty()) return ""
        
        if (clean.startsWith("+")) {
            return clean
        }
        
        if (clean.startsWith("56")) {
            return "+$clean"
        }
        
        // Typical Chilean mobile is 9 digits (9XXXXXXXX) or 8 digits (XXXXXXXX)
        // If the user entered something like 912345678, we add +56
        return if (clean.length >= 8 && clean.startsWith("9")) {
            "+56$clean"
        } else if (clean.length == 8) {
            "+569$clean"
        } else {
            "+56$clean"
        }
    }
}
