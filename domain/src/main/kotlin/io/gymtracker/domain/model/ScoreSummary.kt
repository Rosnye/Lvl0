package io.gymtracker.domain.model

import kotlinx.serialization.Serializable

/**
 * Resumen de puntaje que se publica a los amigos. Es lo ÚNICO que sale del teléfono
 * sobre el entrenamiento (decisión de producto: compartir solo el puntaje).
 * No contiene ejercicios, pesos, fechas de sesión ni notas.
 */
@Serializable
data class ScoreSummary(
    val formulaVersion: Int,
    /** Lunes de la semana resumida, yyyy-MM-dd. */
    val weekStart: String,
    val weekPoints: Int,
    val weekSessions: Int,
    val totalPoints: Int,
    val streakWeeks: Int,
    val level: Int,
)
