package io.gymtracker.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
sealed interface GoalSpec {
    /** Entrenar N veces por semana (semana ISO, lunes a domingo). */
    @Serializable
    @SerialName("weekly_frequency")
    data class WeeklyFrequency(val sessionsPerWeek: Int) : GoalSpec

    /** Levantar al menos targetWeightGrams por minReps repeticiones en un ejercicio. */
    @Serializable
    @SerialName("strength")
    data class Strength(
        val exerciseId: ExerciseId,
        val targetWeightGrams: Int,
        val minReps: Int = 1,
    ) : GoalSpec
}

@Serializable
data class Goal(
    val id: GoalId,
    val spec: GoalSpec,
    /** Fecha local de creación (yyyy-MM-dd). Solo cuentan sesiones desde esta fecha. */
    val createdLocalDate: String,
    val deadlineLocalDate: String? = null,
) {
    val createdDate: LocalDate get() = LocalDate.parse(createdLocalDate)
}
