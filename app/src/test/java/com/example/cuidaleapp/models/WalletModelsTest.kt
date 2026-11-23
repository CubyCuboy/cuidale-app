package com.example.cuidaleapp.models

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletModelsTest {

    @Test
    fun `EstadoCobro mapea strings del backend`() {
        assertEquals(EstadoCobro.PENDIENTE, EstadoCobro.fromString("pendiente"))
        assertEquals(EstadoCobro.REQUIERE_CONFIRMACION, EstadoCobro.fromString("requiere_confirmacion"))
        assertEquals(EstadoCobro.PAGADO, EstadoCobro.fromString("pagado"))
        assertEquals(EstadoCobro.RECHAZADO, EstadoCobro.fromString("rechazado"))
        assertEquals(EstadoCobro.EN_DISPUTA, EstadoCobro.fromString("en_disputa"))
        assertEquals(EstadoCobro.EN_CONFLICTO, EstadoCobro.fromString("en_conflicto"))
        assertEquals(EstadoCobro.DESCONOCIDO, EstadoCobro.fromString(null))
    }

    @Test
    fun `EstadoTurno mapea strings del backend`() {
        assertEquals(EstadoTurno.PENDIENTE_CONFIRMACION_REP, EstadoTurno.fromString("pendiente_confirmacion_rep"))
        assertEquals(EstadoTurno.PENDIENTE_CONFIRMACION_CUI, EstadoTurno.fromString("pendiente_confirmacion_cui"))
        assertEquals(EstadoTurno.ACORDADO, EstadoTurno.fromString("acordado"))
        assertEquals(EstadoTurno.PAGADO, EstadoTurno.fromString("pagado"))
        assertEquals(EstadoTurno.EN_CONFLICTO, EstadoTurno.fromString("en_conflicto"))
        assertEquals(EstadoTurno.DESCONOCIDO, EstadoTurno.fromString("raro"))
    }

    @Test
    fun `EstadoAcuerdo mapea strings del backend`() {
        assertEquals(EstadoAcuerdo.ACORDADO, EstadoAcuerdo.fromString("acordado"))
        assertEquals(EstadoAcuerdo.EN_NEGOCIACION, EstadoAcuerdo.fromString("en_negociacion"))
        assertEquals(EstadoAcuerdo.EN_DISPUTA, EstadoAcuerdo.fromString("en_disputa"))
        assertEquals(EstadoAcuerdo.DESCONOCIDO, EstadoAcuerdo.fromString(""))
    }

    @Test
    fun `Cobro expone su estado tipado`() {
        val cobro = Cobro(estado = "pagado")
        assertEquals(EstadoCobro.PAGADO, cobro.estadoCobro)
    }

    @Test
    fun `Turno expone su estado tipado`() {
        val turno = Turno(estado = "pendiente_confirmacion_rep")
        assertEquals(EstadoTurno.PENDIENTE_CONFIRMACION_REP, turno.estadoTurno)
    }
}
