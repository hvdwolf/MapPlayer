package xyz.hvdw.mapplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.LibraryScanner
import xyz.hvdw.mapplayer.data.MusicRepository

class SettingsActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtStatusSubtitle: TextView
    private lateinit var txtProgress: TextView
    private lateinit var progressScanning: ProgressBar
    private lateinit var btnRescan: Button
    private lateinit var btnOpenLog: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }

        txtStatus = findViewById(R.id.txtStatus)
        txtStatusSubtitle = findViewById(R.id.txtStatusSubtitle)
        txtProgress = findViewById(R.id.txtProgress)
        progressScanning = findViewById(R.id.progressScanning)
        btnRescan = findViewById(R.id.btnRescan)
        btnOpenLog = findViewById<Button>(R.id.btnOpenLog)

        updateStatus()

        btnRescan.setOnClickListener {
            showScanningUI()

            LibraryScanner.scanLibrary(
                this,
                listener = object : LibraryScanner.ScanProgressListener {
                    override fun onProgress(current: Int, total: Int) {
                        runOnUiThread {
                            txtProgress.visibility = View.VISIBLE
                            txtProgress.text = getString(
                                R.string.settings_scanning_progress,
                                current,
                                total
                            )
                        }
                    }
                }
            ) {
                runOnUiThread {
                    // Reload the freshly written library.json
                    MusicRepository.loadLibraryFromJson(this)

                    // Notify FolderBrowserActivity to refresh its UI
                    LocalBroadcastManager.getInstance(this).sendBroadcast(
                        Intent("ACTION_LIBRARY_UPDATED")
                    )

                    hideScanningUI()
                    txtStatus.text = getString(R.string.settings_scan_complete)
                    txtProgress.visibility = View.GONE

                    //val btnOpenLog = findViewById<Button>(R.id.btnOpenLog)
                    if (LibraryScanner.errorLogWritten) {
                        btnOpenLog.visibility = View.VISIBLE
                        btnOpenLog.isEnabled = true

                        btnOpenLog.setOnClickListener {
                            startActivity(Intent(this, LogViewerActivity::class.java))
                        }
                    } else {
                        btnOpenLog.visibility = View.GONE
                    }
                }
            }
        }

        btnOpenLog.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            val logFile = File(getExternalFilesDir(null), "_MapPlayer_Log/error.log")
            intent.setDataAndType(Uri.fromFile(logFile), "text/plain")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }

    }

    private fun updateStatus() {
        txtStatus.text = if (MusicRepository.isReady()) {
            getString(R.string.settings_library_loaded)
        } else {
            getString(R.string.settings_library_not_loaded)
        }
    }

    private fun showScanningUI() {
        txtStatus.text = getString(R.string.settings_scanning)
        txtStatusSubtitle.visibility = View.VISIBLE
        progressScanning.visibility = View.VISIBLE
        btnRescan.isEnabled = false
    }

    private fun hideScanningUI() {
        txtStatusSubtitle.visibility = View.GONE
        progressScanning.visibility = View.GONE
        btnRescan.isEnabled = true
    }
}
