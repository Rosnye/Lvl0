package io.gymtracker.domain.model

/**
 * Plan de series × valor de un ejercicio (repeticiones, segundos o metros según cómo se mide).
 * Las series de un ejercicio comparten el mismo valor: se rellena una vez, no serie por serie.
 * Se guarda como lista de [DraftSet] para conservar el formato de las sesiones.
 */
object SetPlan {
    const val MAX_SETS = 20
    const val MAX_VALUE = 9_999

    /** Valor común de las series (el de la primera). */
    fun valueOf(sets: List<DraftSet>, loadType: LoadType): Int {
        val first = sets.firstOrNull() ?: return 0
        return when (loadType) {
            LoadType.WEIGHTED, LoadType.BODYWEIGHT -> first.reps
            LoadType.TIMED -> first.durationSeconds
            LoadType.DISTANCE -> first.distanceMeters
        }
    }

    /** Ajusta el número de series y su valor conservando cuáles estaban hechas. Sin peso. */
    fun resize(sets: List<DraftSet>, count: Int, value: Int, loadType: LoadType): List<DraftSet> {
        val n = count.coerceIn(1, MAX_SETS)
        val v = value.coerceIn(0, MAX_VALUE)
        return List(n) { i ->
            val done = sets.getOrNull(i)?.done ?: false
            when (loadType) {
                LoadType.WEIGHTED, LoadType.BODYWEIGHT -> DraftSet(reps = v, done = done)
                LoadType.TIMED -> DraftSet(durationSeconds = v, done = done)
                LoadType.DISTANCE -> DraftSet(distanceMeters = v, done = done)
            }
        }
    }

    /** Plan inicial para un ejercicio nuevo: 3 × 10 reps, 3 × 30 s o 1 × 1000 m. */
    fun defaultFor(loadType: LoadType): List<DraftSet> = when (loadType) {
        LoadType.WEIGHTED, LoadType.BODYWEIGHT -> resize(emptyList(), 3, 10, loadType)
        LoadType.TIMED -> resize(emptyList(), 3, 30, loadType)
        LoadType.DISTANCE -> resize(emptyList(), 1, 1_000, loadType)
    }

    /** Reutiliza un plan anterior (de la última sesión o una rutina) como plan sin marcar. */
    fun fromPrevious(sets: List<DraftSet>, loadType: LoadType): List<DraftSet> {
        val working = sets.filter { !it.isWarmup }.ifEmpty { sets }
        if (working.isEmpty()) return defaultFor(loadType)
        return resize(emptyList(), working.size, valueOf(working, loadType), loadType)
    }
}
