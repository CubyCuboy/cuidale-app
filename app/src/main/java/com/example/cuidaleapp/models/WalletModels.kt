package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties

/** Estados posibles de un cobro (reflejan los strings que escribe el backend). */
enum class EstadoCobro {
    PENDIENTE,
    REQUIERE_CONFIRMACION,
    PAGADO,
    RECHAZADO,
    EN_DISPUTA,
    EN_CONFLICTO,
    DESCONOCIDO;

    companion object {
        fun fromString(value: String?): EstadoCobro = when (value) {
            "pendiente" -> PENDIENTE
            "requiere_confirmacion" -> REQUIERE_CONFIRMACION
            "pagado" -> PAGADO
            "rechazado" -> RECHAZADO
            "en_disputa" -> EN_DISPUTA
            "en_conflicto" -> EN_CONFLICTO
            else -> DESCONOCIDO
        }
    }
}

/** Estados posibles de un turno. */
enum class EstadoTurno {
    PENDIENTE_CONFIRMACION_REP,
    PENDIENTE_CONFIRMACION_CUI,
    ACORDADO,
    PAGADO,
    RECHAZADO,
    EN_DISPUTA,
    EN_CONFLICTO,
    DESCONOCIDO;

    companion object {
        fun fromString(value: String?): EstadoTurno = when (value) {
            "pendiente_confirmacion_rep" -> PENDIENTE_CONFIRMACION_REP
            "pendiente_confirmacion_cui" -> PENDIENTE_CONFIRMACION_CUI
            "acordado" -> ACORDADO
            "pagado" -> PAGADO
            "rechazado" -> RECHAZADO
            "en_disputa" -> EN_DISPUTA
            "en_conflicto" -> EN_CONFLICTO
            else -> DESCONOCIDO
        }
    }
}

/** Estados del acuerdo (tarifa / horario) de un contrato. */
enum class EstadoAcuerdo {
    ACORDADO,
    EN_NEGOCIACION,
    EN_DISPUTA,
    DESCONOCIDO;

    companion object {
        fun fromString(value: String?): EstadoAcuerdo = when (value) {
            "acordado" -> ACORDADO
            "en_negociacion" -> EN_NEGOCIACION
            "en_disputa" -> EN_DISPUTA
            else -> DESCONOCIDO
        }
    }
}

/** Billetera de un usuario: { uid, saldo, movimientos, actualizadoAt }. Solo lectura. */
@Immutable
@IgnoreExtraProperties
data class Billetera(
    val uid: String = "",
    val saldo: Long = 0L,
    val movimientos: List<MovimientoBilletera> = emptyList(),
    val actualizadoAt: Timestamp? = null
)

/** Movimiento de billetera (embebido en el documento `billetera/{uid}`). Solo lectura. */
@Immutable
@IgnoreExtraProperties
data class MovimientoBilletera(
    val id: String = "",
    val tipo: String = "",
    val tipoMovimiento: String = "",
    val monto: Long = 0L,
    val saldoResultante: Long = 0L,
    val referencia: String = "",
    val descripcion: String = "",
    val timestamp: Timestamp? = null,
    val estado: String = "",
    val emisorUid: String = "",
    val receptorUid: String = ""
)

/**
 * Transferencia / recarga de billetera (colección `transferencias`).
 * El backend escribe estos documentos; la app solo lee.
 * Estados: pendiente | aprobada | rechazada | en revisión.
 */
@Immutable
@IgnoreExtraProperties
data class Transferencia(
    val id: String = "",
    val uid: String = "",
    val monto: Long = 0L,
    val estado: String = "",
    val preferenceId: String = "",
    val paymentId: String = "",
    val detalle: String = "",
    val createdAt: Timestamp? = null,
    val aprobadaAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/** Estados de un movimiento de billetera (el backend los escribe en recargas). */
enum class EstadoMovimiento {
    APROBADO,
    COMPLETADO,
    PENDIENTE,
    RECHAZADO,
    REVISION,
    DESCONOCIDO;

    companion object {
        fun fromString(value: String?): EstadoMovimiento = when (value?.lowercase()) {
            "aprobado", "aprobada", "completado", "completada", "pagado", "recibido" -> APROBADO
            "pendiente" -> PENDIENTE
            "rechazado", "rechazada", "cancelado", "cancelada" -> RECHAZADO
            "revision", "en_revision" -> REVISION
            else -> DESCONOCIDO
        }
    }
}

/**
 * Cobro de un turno. El backend lo escribe; la app solo lee.
 * El id del cobro puede ser el id del turno o `${solicitudId}_${fecha}`.
 */
@Immutable
@IgnoreExtraProperties
data class Cobro(
    val id: String = "",
    val solicitudId: String = "",
    val turnoId: String = "",
    val cuidadorUid: String = "",
    val cuidadorNombre: String = "",
    val representanteUid: String = "",
    val representanteNombre: String = "",
    val nombrePaciente: String = "",
    val montoAcordado: Long = 0L,
    val horasAcordadas: Double = 0.0,
    val tarifaHora: Long = 0L,
    val monto: Long = 0L,
    val horas: Double = 0.0,
    val comentario: String = "",
    val estado: String = "",
    val saldoSuficiente: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val pagadoAt: Timestamp? = null
) {
    val estadoCobro: EstadoCobro get() = EstadoCobro.fromString(estado)
}

/** Turno de un contrato: { fecha, horas, tarifa, monto, estado, confirmaciones, observaciones }. Solo lectura. */
@Immutable
@IgnoreExtraProperties
data class Turno(
    val id: String = "",
    val solicitudId: String = "",
    val fecha: String = "",
    val horasTrabajadas: Double = 0.0,
    val tarifaUsada: Long = 0L,
    val montoCalculado: Long = 0L,
    val estado: String = "",
    val confirmaciones: Map<String, Boolean> = emptyMap(),
    val propuestaTarifa: Long? = null,
    val propuestaPor: String = "",
    val observaciones: Map<String, String> = emptyMap(),
    val registroInformeId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val estadoTurno: EstadoTurno get() = EstadoTurno.fromString(estado)
}

/** Registro de turno (base del informe/PDF del representante). Solo lectura. */
@Immutable
@IgnoreExtraProperties
data class RegistroTurno(
    val id: String = "",
    val turnoId: String = "",
    val solicitudId: String = "",
    val fecha: String = "",
    val horas: Double = 0.0,
    val tarifaUsada: Long = 0L,
    val montoPagado: Long = 0L,
    val observaciones: Map<String, String> = emptyMap(),
    val creadoEn: Timestamp? = null,
    val vigenciaHasta: Timestamp? = null
)
