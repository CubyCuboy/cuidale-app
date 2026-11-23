package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Recibe el retorno del checkout de Mercado Pago (deep link cuidaleapp://pago).
 *
 * NO acredita nada: solo valida el estado de la URL y lleva al usuario a la
 * Billetera, donde verá "procesando" hasta que el webhook acredite el saldo.
 * Por minimización de datos, se ignora el parámetro `recarga` de la URL.
 */
class PagoRetornoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val estadoBruto = intent.data?.getQueryParameter("estado")?.lowercase() ?: "pendiente"
        val estadoNormalizado = when (estadoBruto) {
            "aprobado" -> "aprobado"
            "rechazado" -> "rechazado"
            else -> "pendiente"
        }

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        } else {
            startActivity(
                Intent(this, MenuActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("TARGET_SCREEN", "BILLETERA")
                    putExtra("PAGO_ESTADO", estadoNormalizado)
                    putExtra("SKIP_SPLASH", true)
                }
            )
        }
        finish()
    }
}
