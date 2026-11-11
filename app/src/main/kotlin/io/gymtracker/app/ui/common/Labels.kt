package io.gymtracker.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import io.gymtracker.app.R
import io.gymtracker.domain.model.Equipment
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.MuscleGroup
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.muscle_chest
    MuscleGroup.BACK -> R.string.muscle_back
    MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
    MuscleGroup.BICEPS -> R.string.muscle_biceps
    MuscleGroup.TRICEPS -> R.string.muscle_triceps
    MuscleGroup.QUADS -> R.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
    MuscleGroup.GLUTES -> R.string.muscle_glutes
    MuscleGroup.CALVES -> R.string.muscle_calves
    MuscleGroup.CORE -> R.string.muscle_core
    MuscleGroup.FULL_BODY -> R.string.muscle_full_body
    MuscleGroup.CARDIO -> R.string.muscle_cardio
}

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.BAND -> R.string.equipment_band
    Equipment.OTHER -> R.string.equipment_other
}

@StringRes
fun LoadType.labelRes(): Int = when (this) {
    LoadType.WEIGHTED -> R.string.load_weighted
    LoadType.BODYWEIGHT -> R.string.load_bodyweight
    LoadType.TIMED -> R.string.load_timed
    LoadType.DISTANCE -> R.string.load_distance
}

object Format {
    /** 22500 g -> "22.5", 100000 g -> "100". */
    fun kg(grams: Int): String {
        val kg = grams / 1000.0
        return if (kg == kg.toLong().toDouble()) kg.toLong().toString() else String.format(Locale.US, "%.2f", kg).trimEnd('0')
    }

    /** "22,5" o "22.5" -> 22500 g; null si no es un número. */
    fun parseKg(text: String): Int? = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 && it < 10_000 }?.let { (it * 1000).toInt() }

    fun mmss(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun duration(seconds: Int): String {
        val d = Duration.ofSeconds(seconds.toLong())
        return if (d.toHours() > 0) "${d.toHours()} h ${d.toMinutesPart()} min" else "${d.toMinutes()} min"
    }

    fun day(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)).replaceFirstChar { it.titlecase(locale) }

    fun time(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    /** Idioma de la app (sigue la configuración de Compose, no la de la JVM). */
    @Composable
    fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    @Composable
    fun day(date: LocalDate): String = day(date, appLocale())

    @Composable
    fun time(epochMs: Long): String = time(epochMs, appLocale())
}
