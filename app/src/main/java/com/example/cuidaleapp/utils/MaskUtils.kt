package com.example.cuidaleapp.utils

object MaskUtils {

    fun email(email: String): String {
        if (email.isBlank()) return ""
        val parts = email.split("@")
        if (parts.size != 2) return email
        val local = parts[0]
        val domain = parts[1]
        if (local.length <= 2) return "$local@$domain"
        return "${local.first()}${"*".repeat(local.length - 2)}${local.last()}@$domain"
    }

    fun rut(rut: String): String {
        if (rut.isBlank()) return ""
        val clean = rut.replace(".", "").replace("-", "").replace(" ", "")
        if (clean.length < 3) return rut
        val body = clean.dropLast(1)
        val dv = clean.last()
        val maskedBody = "${body.first()}${"*".repeat((body.length - 2).coerceAtLeast(0))}${body.last()}"
        return "$maskedBody-$dv"
    }

    fun phone(phone: String): String {
        if (phone.isBlank()) return ""
        val clean = phone.replace(Regex("[^0-9]"), "")
        if (clean.length < 4) return phone
        val tail = clean.takeLast(3)
        val prefix = clean.take(2)
        return "+56 $prefix **** $tail"
    }
}
