package com.example.cuidaleapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Recibe el retorno de la autorización OAuth de Mercado Pago
 * (deep link cuidaleapp://oauthmp?estado=ok|error|cancelado). El canje del
 * código ya lo hizo la Cloud Function `retornoOauthMp` en el backend; aquí solo
 * se reenvía el resultado a la pantalla de cuentas.
 */
class MpOauthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val estado = intent.data?.getQueryParameter("estado") ?: ""

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        } else {
            startActivity(
                Intent(this, MenuActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("TARGET_SCREEN", "CUENTAS")
                    putExtra("OAUTH_MP_ESTADO", estado)
                    putExtra("SKIP_SPLASH", true)
                }
            )
        }
        finish()
    }
}
