package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp

/**
 * Destino de retiro: cuenta bancaria chilena o cuenta de Mercado Pago.
 * El documento NO necesita estar a nombre del usuario (no se valida titularidad).
 */
@Immutable
data class CuentaDestino(
    val id: String = "",
    val uid: String = "",
    val tipoDestino: String = "", // "banco" | "mercado_pago"
    val banco: String = "",
    val bancoNombre: String = "",
    val tipoCuenta: String = "",
    val numeroCuenta: String = "",
    val titular: String = "",
    val rut: String = "",
    val mpIdentificador: String = "",
    val creadaAt: Timestamp? = null
) {
    val mascara: String
        get() = if (tipoDestino == TIPO_BANCO) {
            "•••• ${numeroCuenta.takeLast(4)}"
        } else {
            mpIdentificador
        }

    companion object {
        const val TIPO_BANCO = "banco"
        const val TIPO_MP = "mercado_pago"

        /** Valida un RUT chileno (cuerpo + dígito verificador). */
        fun validarRut(rut: String): Boolean {
            val limpio = rut.replace(".", "").replace("-", "")
            val m = Regex("^(\\d{1,8})([0-9kK])$").find(limpio) ?: return false
            val cuerpo = m.groupValues[1]
            var suma = 0
            var multiplo = 2
            for (i in cuerpo.length - 1 downTo 0) {
                suma += cuerpo[i].digitToInt() * multiplo
                multiplo = if (multiplo == 7) 2 else multiplo + 1
            }
            val esperado = 11 - (suma % 11)
            val dv = when (esperado) {
                11 -> "0"
                10 -> "k"
                else -> esperado.toString()
            }
            return dv == m.groupValues[2].lowercase()
        }
    }
}

/** Catálogo de bancos chilenos (código MP → nombre). Espejo del backend. */
object BancosChile {
    val lista: List<Pair<String, String>> = listOf(
        "001" to "Banco de Chile",
        "002" to "Banco Internacional",
        "009" to "Banco Scotiabank",
        "014" to "Banco Itaú",
        "016" to "Banco Security",
        "028" to "Banco BICE",
        "031" to "Banco Santander",
        "037" to "Banco BCI",
        "049" to "Banco Estado",
        "051" to "Banco Falabella",
        "055" to "Banco Ripley",
        "057" to "Banco Consorcio",
        "065" to "Banco Desarrollo",
        "072" to "Banco Paris",
        "668" to "Coopeuch"
    )

    val mapa: Map<String, String> = lista.toMap()
}
