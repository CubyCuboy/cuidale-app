package com.example.cuidaleapp.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun formatDate(time: Long?): String {
        if (time == null) return "Seleccionar"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(time))
    }
}
