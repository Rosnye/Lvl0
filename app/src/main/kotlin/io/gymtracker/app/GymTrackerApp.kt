package io.gymtracker.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import io.gymtracker.app.settings.LanguageStore

@HiltAndroidApp
class GymTrackerApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageStore.wrap(base))
    }
}
