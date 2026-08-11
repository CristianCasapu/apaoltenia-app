package ro.apaoltenia.client

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Maparea id buton radio -> mod de tema. */
class ThemeModeTest {

    @Test fun light() = assertEquals(
        AppCompatDelegate.MODE_NIGHT_NO,
        SettingsActivity.themeModeFor(R.id.themeLight)
    )

    @Test fun dark() = assertEquals(
        AppCompatDelegate.MODE_NIGHT_YES,
        SettingsActivity.themeModeFor(R.id.themeDark)
    )

    @Test fun system_default() = assertEquals(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        SettingsActivity.themeModeFor(R.id.themeSystem)
    )

    @Test fun id_necunoscut_da_system() = assertEquals(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        SettingsActivity.themeModeFor(-1)
    )
}
