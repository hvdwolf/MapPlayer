package xyz.hvdw.mapplayer.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.LibraryScanner
import xyz.hvdw.mapplayer.data.MusicRepository

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnRescan: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        txtStatus = findViewById(R.id.txtStatus)
        btnRescan = findViewById(R.id.btnRescan)

        updateStatus()

        btnRescan.setOnClickListener {
            txtStatus.text = "Scanning /storage/emulated/0/Music ..."
            btnRescan.isEnabled = false

            LibraryScanner.scanLibrary(this) {
                runOnUiThread {
                    txtStatus.text = "Scan complete"
                    btnRescan.isEnabled = true
                    MusicRepository.ensureLibraryLoaded(this)
                }
            }
        }
    }

    private fun updateStatus() {
        txtStatus.text = if (MusicRepository.isReady()) {
            "Library loaded"
        } else {
            "Library not loaded"
        }
    }
}
