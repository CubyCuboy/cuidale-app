package com.example.cuidaleapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MontoUtilsTest {

    // ---- Formato CLP ----

    @Test
    fun `formatClp formatea montos positivos`() {
        assertEquals("$20.000", MontoUtils.formatClp(20_000L))
        assertEquals("$1.000", MontoUtils.formatClp(1_000L))
        assertEquals("$1.000.000", MontoUtils.formatClp(1_000_000L))
        assertEquals("$0", MontoUtils.formatClp(0L))
    }

    @Test
    fun `formatClp formatea montos negativos`() {
        assertEquals("-$20.000", MontoUtils.formatClp(-20_000L))
    }

    // ---- Parseo ----

    @Test
    fun `parseMonto acepta puntos y simbolo peso`() {
        assertEquals(20_000L, MontoUtils.parseMonto("20.000"))
        assertEquals(20_000L, MontoUtils.parseMonto("$20.000"))
        assertEquals(40_000L, MontoUtils.parseMonto("40000"))
    }

    @Test
    fun `parseMonto acepta coma como separador de miles`() {
        assertEquals(20_000L, MontoUtils.parseMonto("20,000"))
        assertEquals(1_000_000L, MontoUtils.parseMonto("1,000,000"))
    }

    @Test
    fun `parseMonto rechaza decimales y formatos mixtos`() {
        assertNull(MontoUtils.parseMonto("20,5"))
        assertNull(MontoUtils.parseMonto("20.5"))
        assertNull(MontoUtils.parseMonto("1.234,56"))
        assertNull(MontoUtils.parseMonto("12.3.4"))
        assertNull(MontoUtils.parseMonto("abc"))
        assertNull(MontoUtils.parseMonto(""))
    }

    @Test
    fun `parseHoras acepta coma y punto decimal`() {
        assertEquals(1.5, MontoUtils.parseHoras("1,5")!!, 0.0001)
        assertEquals(1.5, MontoUtils.parseHoras("1.5")!!, 0.0001)
    }

    @Test
    fun `parseHoras rechaza cero y no numericos`() {
        assertNull(MontoUtils.parseHoras("0"))
        assertNull(MontoUtils.parseHoras("abc"))
    }

    // ---- Validaciones ----

    @Test
    fun `validarMontoRecarga respeta rango 1000 - 1000000`() {
        assertFalse(MontoUtils.validarMontoRecarga(999L))
        assertTrue(MontoUtils.validarMontoRecarga(1_000L))
        assertTrue(MontoUtils.validarMontoRecarga(1_000_000L))
        assertFalse(MontoUtils.validarMontoRecarga(1_000_001L))
    }

    @Test
    fun `validarTarifaHora exige positivo y tope`() {
        assertFalse(MontoUtils.validarTarifaHora(0L))
        assertTrue(MontoUtils.validarTarifaHora(500L))
        assertFalse(MontoUtils.validarTarifaHora(10_000_001L))
    }

    @Test
    fun `validarHoras exige positivo, tope y maximo un decimal`() {
        assertFalse(MontoUtils.validarHoras(0.0))
        assertTrue(MontoUtils.validarHoras(8.0))
        assertFalse(MontoUtils.validarHoras(8.55))
        assertFalse(MontoUtils.validarHoras(1001.0))
    }

    // ---- Cálculo ----

    @Test
    fun `calcularMonto multiplica tarifa por horas`() {
        assertEquals(72_000L, MontoUtils.calcularMonto(9_000L, 8.0))
        assertEquals(76_330L, MontoUtils.calcularMonto(8_500L, 8.98))
    }

    // ---- Horas desde horario ----

    @Test
    fun `horasDesdeHorario calcula tramo simple`() {
        assertEquals(9.0, MontoUtils.horasDesdeHorario("De 09:00 a 18:00")!!, 0.0001)
        assertEquals(4.0, MontoUtils.horasDesdeHorario("De 08:30 a 12:30")!!, 0.0001)
    }

    @Test
    fun `horasDesdeHorario soporta cruce de medianoche`() {
        assertEquals(8.98, MontoUtils.horasDesdeHorario("De 20:00 a 04:59")!!, 0.0001)
    }

    @Test
    fun `horasDesdeHorario devuelve null si el formato no coincide`() {
        assertNull(MontoUtils.horasDesdeHorario("horario inválido"))
        assertNull(MontoUtils.horasDesdeHorario(""))
    }
}
