package xyz.hvdw.mapplayer.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import xyz.hvdw.mapplayer.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        val prefs = requireContext().getSharedPreferences("mapplayer_settings", Context.MODE_PRIVATE)

        val galleryPref = findPreference<SwitchPreferenceCompat>("pref_gallery_view")
        galleryPref?.setOnPreferenceChangeListener { _, newValue ->
            prefs.edit().putBoolean("pref_gallery_view", newValue as Boolean).commit()
            true
        }

        val thumbSizePref = findPreference<ListPreference>("pref_thumbnail_size")
        thumbSizePref?.setOnPreferenceChangeListener { _, newValue ->
            prefs.edit().putInt("pref_thumbnail_size", (newValue as String).toInt()).commit()
            true
        }

    }
}
