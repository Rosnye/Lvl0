package io.gymtracker.app.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Idiomas de la app. Inglés por defecto, independientemente del idioma del teléfono. */
enum class AppLanguage(val tag: String) { ENGLISH("en"), SPANISH("es") }

/**
 * Preferencia de idioma. Se lee de forma síncrona en attachBaseContext (antes de que Hilt
 * inyecte nada), por eso es un objeto con SharedPreferences y no una dependencia inyectada.
 */
object LanguageStore {
    private const val PREFS = "settings"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.ENGLISH
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, language.tag) }
    }

    /** Contexto con el idioma elegido; se usa en attachBaseContext de la app, actividad y servicios. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(get(base).tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Cambia el idioma y recrea la pantalla para aplicarlo. */
    fun change(context: Context, language: AppLanguage) {
        if (get(context) == language) return
        set(context, language)
        context.findActivity()?.recreate()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
