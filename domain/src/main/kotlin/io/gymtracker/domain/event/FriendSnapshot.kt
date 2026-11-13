package io.gymtracker.domain.event

/**
 * Lo mínimo que un amigo necesita para su tablero: el perfil más reciente y el último
 * resumen de puntaje de cada semana (como mucho [maxWeeks] semanas). Se envía siempre
 * completo, así cada sincronización es idempotente y no hace falta recordar qué se envió.
 */
object FriendSnapshot {
    const val DEFAULT_MAX_WEEKS = 52

    fun select(ownEvents: List<SignedEvent>, maxWeeks: Int = DEFAULT_MAX_WEEKS): List<SignedEvent> {
        val shared = ownEvents.filter { it.body.visibility == Visibility.FRIENDS }
        val profile = shared.filter { it.payload is EventPayload.ProfileUpdated }.maxByOrNull { it.seq }
        val summaries = shared
            .filter { it.payload is EventPayload.ScorePublished }
            .groupBy { (it.payload as EventPayload.ScorePublished).summary.weekStart }
            .map { (week, events) -> week to events.maxBy { it.seq } }
            .sortedByDescending { it.first }
            .take(maxWeeks)
            .map { it.second }
        return (listOfNotNull(profile) + summaries).sortedBy { it.seq }
    }
}
