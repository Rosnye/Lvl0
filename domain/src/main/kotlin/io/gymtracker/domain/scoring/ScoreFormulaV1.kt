package io.gymtracker.domain.scoring

/**
 * Constantes de la fórmula de puntaje v1. Ver docs/PUNTAJE_V1.md.
 * Cambiar cualquier valor exige una nueva versión de fórmula: los amigos comparan
 * puntajes calculados con la misma versión.
 */
object ScoreFormulaV1 {
    const val VERSION: Int = 1

    /** Series de trabajo mínimas para que una sesión cuente. */
    const val MIN_WORKING_SETS: Int = 3

    /** Puntos base por la primera sesión válida del día. */
    const val BASE_POINTS: Int = 100

    /** Bono máximo por volumen superior al propio promedio. */
    const val VOLUME_BONUS_MAX: Int = 50
    /** Ventana (días) del promedio propio de volumen. */
    const val VOLUME_WINDOW_DAYS: Long = 28
    /** Sesiones previas en la ventana necesarias para tener línea base. */
    const val VOLUME_MIN_HISTORY: Int = 3

    /** Puntos por cada récord personal (1RM estimado). */
    const val PR_POINTS: Int = 30
    /** Récords que puntúan por sesión como máximo. */
    const val PR_MAX_PER_SESSION: Int = 2
    /** Sesiones previas con el ejercicio antes de que un récord puntúe. */
    const val PR_MIN_PRIOR_SESSIONS: Int = 3
    /** Repeticiones máximas para estimar 1RM con Epley. */
    const val PR_MAX_REPS: Int = 12

    /** Incremento del multiplicador por cada semana de racha. */
    const val STREAK_STEP: Double = 0.10
    /** Semanas de racha que cuentan para el multiplicador (máx. 1.5x). */
    const val STREAK_MAX_WEEKS: Int = 5

    /** Sesiones por semana si el usuario no fijó meta de frecuencia. */
    const val DEFAULT_WEEKLY_TARGET: Int = 3
    const val MIN_WEEKLY_TARGET: Int = 2
    const val MAX_WEEKLY_TARGET: Int = 6

    /** Bono al alcanzar la meta de frecuencia semanal. */
    const val WEEKLY_GOAL_BONUS: Int = 100
    /** Bono único al cumplir una meta de fuerza. */
    const val STRENGTH_GOAL_BONUS: Int = 200

    /** nivel = floor(sqrt(total / LEVEL_DIVISOR)) + 1 */
    const val LEVEL_DIVISOR: Int = 500
}
