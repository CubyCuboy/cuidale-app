package com.example.cuidaleapp.utils

/**
 * Utilidades puras de dinero y horas (testeables en JVM).
 * Dinero siempre en [Long] (CLP entero); horas en [Double].
 */
object MontoUtils {

    const val MONTO_MIN_RECARGA = 1_000L
    const val MONTO_MAX_RECARGA = 1_000_000L
    const val MONTO_MIN_RETIRO = 1_000L
    // Límites de retiro (espejo del backend en functions/pagos.js).
    const val MONTO_MAX_RETIRO_POR_CARGO = 200_000L
    const val RETIROS_MAX_POR_DIA = 5
    const val TARIFA_MAX = 10_000_000L
    const val HORAS_MAX = 1_000.0

    /** Formatea CLP como $20.000 / -$20.000 (es-CL). */
    fun formatClp(monto: Long): String {
        val signo = if (monto < 0) "-" else ""
        val abs = kotlin.math.abs(monto).toString()
        val miles = abs.reversed().chunked(3).joinToString(".").reversed()
        return "$signo$$miles"
    }

    /**
     * Parsea un monto CLP entero: acepta "20000", "20.000" o "20,000"
     * (separador de miles punto o coma). Rechaza decimales y formatos mixtos.
     */
    fun parseMonto(input: String): Long? {
        val limpio = input.trim().replace("$", "").replace(" ", "")
        if (limpio.isEmpty()) return null
        if (!limpio.matches(Regex("""[0-9.,]+"""))) return null
        // Mezclar punto y coma es ambiguo (posible decimal): se rechaza.
        if (limpio.contains('.') && limpio.contains(',')) return null
        val separador = when {
            limpio.contains('.') -> '.'
            limpio.contains(',') -> ','
            else -> null
        }
        val partes = if (separador != null) limpio.split(separador) else listOf(limpio)
        // La parte de miles (excepto la primera) debe tener exactamente 3 dígitos.
        if ((partes.size > 1) && partes.drop(1).any { it.length != 3 }) return null
        if (partes.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        return partes.joinToString("").toLongOrNull()
    }

    /** Parsea "1,5" o "1.5" como horas (> 0). */
    fun parseHoras(input: String): Double? {
        val limpio = input.trim().replace(',', '.').replace(" ", "")
        val v = limpio.toDoubleOrNull() ?: return null
        return if (v > 0.0) v else null
    }

    /** Recarga válida: entre 1.000 y 1.000.000 CLP. */
    fun validarMontoRecarga(monto: Long): Boolean =
        monto in MONTO_MIN_RECARGA..MONTO_MAX_RECARGA

    /** Tarifa horaria válida: > 0 y <= tope. */
    fun validarTarifaHora(tarifa: Long): Boolean =
        tarifa > 0L && tarifa <= TARIFA_MAX

    /** Horas válidas: > 0, <= tope y máximo 1 decimal. */
    fun validarHoras(horas: Double): Boolean =
        horas > 0.0 && horas <= HORAS_MAX && horas * 10.0 == Math.floor(horas * 10.0)

    /** monto = tarifa × horas (redondeado al CLP más cercano). */
    fun calcularMonto(tarifa: Long, horas: Double): Long =
        Math.round(tarifa * horas)

    /** Formatea horas: "9" para enteras, "8,98" con coma decimal. */
    fun formatHoras(horas: Double): String =
        if (horas == Math.floor(horas)) horas.toInt().toString() else horas.toString().replace('.', ',')

    private val HORARIO_REGEX =
        Regex("""de\s*(\d{1,2}):(\d{2})\s*a\s*(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)

    /**
     * Calcula las horas de un turno desde "De HH:mm a HH:mm".
     * Soporta cruce de medianoche (ej. 20:00 a 04:59 → 8,98 h).
     */
    fun horasDesdeHorario(horarioStr: String): Double? {
        val m = HORARIO_REGEX.find(horarioStr) ?: return null
        val hInicio = m.groupValues[1].toInt()
        val minInicio = m.groupValues[2].toInt()
        val hFin = m.groupValues[3].toInt()
        val minFin = m.groupValues[4].toInt()
        var minutos = (hFin * 60 + minFin) - (hInicio * 60 + minInicio)
        if (minutos <= 0) minutos += 24 * 60
        val horas = minutos / 60.0
        // Redondeo a 1 decimal para cumplir la validación (máx. 1 decimal).
        return Math.round(horas * 10) / 10.0
    }
}
