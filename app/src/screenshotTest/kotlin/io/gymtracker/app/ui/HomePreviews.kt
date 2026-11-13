package io.gymtracker.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.gymtracker.app.ui.home.HomeContent
import io.gymtracker.app.ui.home.HomeUiState
import io.gymtracker.app.ui.theme.GymTrackerTheme

/**
 * Previews que el plugin de capturas renderiza en JVM, sin emulador.
 *   ./gradlew :app:updateDebugScreenshotTest   -> genera/actualiza las referencias PNG
 *   ./gradlew :app:validateDebugScreenshotTest -> compara contra las referencias (regresión visual)
 */
private val sample = HomeUiState(
    fingerprint = "9f3a-1c77-b2e0-4d58",
    totalPoints = 4310,
    weekPoints = 220,
    level = 3,
    streakWeeks = 3,
    sessionCount = 42,
)

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"

@Composable
private fun HomeShell(darkTheme: Boolean, state: HomeUiState) {
    GymTrackerTheme(darkTheme = darkTheme) {
        AppScaffold(selected = Destination.TRAIN, onSelect = {}) { padding ->
            Box(Modifier.padding(padding)) { HomeContent(state) }
        }
    }
}

@PreviewTest
@Preview(name = "Inicio claro", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun HomeLightPreview() = HomeShell(darkTheme = false, state = sample.copy(themeMode = io.gymtracker.app.settings.ThemeMode.LIGHT))

@PreviewTest
@Preview(name = "Inicio oscuro", device = PHONE, showSystemUi = true, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeDarkPreview() = HomeShell(darkTheme = true, state = sample)

@PreviewTest
@Preview(name = "Inicio sin datos", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun HomeEmptyPreview() = HomeShell(darkTheme = true, state = HomeUiState(fingerprint = "9f3a-1c77-b2e0-4d58"))
