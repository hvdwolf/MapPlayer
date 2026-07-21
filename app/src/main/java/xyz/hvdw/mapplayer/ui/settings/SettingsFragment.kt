package xyz.hvdw.mapplayer.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import xyz.hvdw.mapplayer.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
    }
}
