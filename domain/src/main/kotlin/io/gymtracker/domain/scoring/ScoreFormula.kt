package io.gymtracker.domain.scoring

/**
 * Constantes de la fórmula de puntaje. Ver docs/PUNTAJE.md.
 *
 * v2 (2026-11-13): sin peso. El volumen es el total de repeticiones de trabajo y un récord
 * es superar tus repeticiones totales de un ejercicio en una sesión. El historial se recalcula
 * con v2 al actualizar la app; cada resumen compartido lleva su versión.
 */
object ScoreFormula {
    const val VERSION: Int = 2

    /** Series de trabajo mínimas para que una sesión cuente. */
    const val MIN_WORKING_SETS: Int = 3

    /** Puntos base por la primera sesión válida del día. */
    const val BASE_POINTS: Int = 100

    /** Bono máximo por volumen (repeticiones) superior al propio promedio. */
    const val VOLUME_BONUS_MAX: Int = 50
    /** Ventana (días) del promedio propio de volumen. */
    const val VOLUME_WINDOW_DAYS: Long = 28
    /** Sesiones previas en la ventana necesarias para tener línea base. */
    const val VOLUME_MIN_HISTORY: Int = 3

    /** Puntos por cada récord personal (más repeticiones totales en un ejercicio). */
    const val PR_POINTS: Int = 30
    /** Récords que puntúan por sesión como máximo. */
    const val PR_MAX_PER_SESSION: Int = 2
    /** Sesiones previas con el ejercicio antes de que un récord puntúe. */
    const val PR_MIN_PRIOR_SESSIONS: Int = 3

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
    /** Bono único al cumplir una meta de repeticiones (o de fuerza, en metas antiguas). */
    const val STRENGTH_GOAL_BONUS: Int = 200

    /** nivel = floor(sqrt(total / LEVEL_DIVISOR)) + 1 */
    const val LEVEL_DIVISOR: Int = 500
}
