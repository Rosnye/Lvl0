package io.gymtracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Semanas ISO: empiezan el lunes. Una semana se identifica por la fecha de su lunes. */
object Weeks {
    fun startOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
