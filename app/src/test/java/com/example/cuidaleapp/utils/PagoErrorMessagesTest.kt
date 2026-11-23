package com.example.cuidaleapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagoErrorMessagesTest {

    @Test
    fun `saldo insuficiente se detecta y muestra mensaje claro`() {
        val codigo = "failed-precondition"
        val server = "Saldo insuficiente en tu billetera. Recarga para completar el pago."
        assertTrue(PagoErrorMessages.esSaldoInsuficiente(codigo, server))
        assertEquals(server, PagoErrorMessages.mensaje(codigo, server))
    }

    @Test
    fun `ya pagado se detecta`() {
        val codigo = "failed-precondition"
        val server = "Este servicio ya fue pagado."
        assertTrue(PagoErrorMessages.esYaPagado(codigo, server))
        assertEquals(server, PagoErrorMessages.mensaje(codigo, server))
    }

    @Test
    fun `already-exists mantiene el mensaje del servidor`() {
        val server = "Ya existe un proceso de cobro activo para este servicio."
        assertTrue(PagoErrorMessages.esCobroActivo("already-exists"))
        assertEquals(server, PagoErrorMessages.mensaje("already-exists", server))
    }

    @Test
    fun `unauthenticated muestra mensaje de sesion`() {
        val msg = PagoErrorMessages.mensaje("unauthenticated", null)
        assertTrue(msg.contains("sesión"))
    }

    @Test
    fun `errores internos ocultan el detalle crudo`() {
        val msg = PagoErrorMessages.mensaje("internal", "detalle interno sensible")
        assertFalse(msg.contains("detalle interno sensible"))
        assertTrue(msg.contains("inesperado"))
    }

    @Test
    fun `codigo desconocido usa mensaje generico`() {
        val msg = PagoErrorMessages.mensaje("otro", null)
        assertTrue(msg.contains("inesperado"))
    }

    @Test
    fun `estados de conflicto`() {
        assertTrue(PagoErrorMessages.esConflicto("en_conflicto"))
        assertTrue(PagoErrorMessages.esConflicto("en_disputa"))
        assertFalse(PagoErrorMessages.esConflicto("pendiente"))
    }
}
