package com.example.cuidaleapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Abre el checkout de Mercado Pago con Chrome Custom Tabs.
 * Solo acepta URLs https de Mercado Pago (defensa ante URLs inyectadas).
 */
object CheckoutLauncher {

    private val MP_HOSTS = listOf(
        "mercadopago.cl",
        "mercadopago.com",
        "mercadopago.com.ar",
        "mercadopago.com.br",
        "mlstatic.com"
    )

    /**
     * Abre el checkout de Mercado Pago con Chrome Custom Tabs.
     * Devuelve true si se logró abrir y false si la URL no es válida o falló.
     */
    fun abrir(context: Context, url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme != "https" || MP_HOSTS.none { uri.host?.endsWith(it) == true }) return false

        return try {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
            true
        } catch (_: Exception) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
        }
    }
}
