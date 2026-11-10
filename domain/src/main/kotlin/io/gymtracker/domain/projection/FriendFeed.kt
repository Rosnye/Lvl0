package io.gymtracker.domain.projection

import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.ScoreSummary

/** Lo que sabemos de un amigo: su perfil y el último resumen publicado por semana. */
data class FriendFeed(
    val authorId: AuthorId,
    val profile: Profile?,
    /** weekStart -> último resumen recibido para esa semana. */
    val weeks: Map<String, ScoreSummary>,
) {
    val latest: ScoreSummary? get() = weeks.values.maxByOrNull { it.weekStart }
    fun pointsForWeek(weekStart: String): Int = weeks[weekStart]?.weekPoints ?: 0
}

object FriendProjector {
    fun project(authorId: AuthorId, events: List<SignedEvent>): FriendFeed {
        var profile: Profile? = null
        val weeks = HashMap<String, ScoreSummary>()
        for (event in events.filter { it.authorId == authorId }.sortedBy { it.seq }) {
            when (val p = event.payload) {
                is EventPayload.ProfileUpdated -> profile = p.profile
                is EventPayload.ScorePublished -> weeks[p.summary.weekStart] = p.summary
                else -> Unit
            }
        }
        return FriendFeed(authorId, profile, weeks)
    }
}
