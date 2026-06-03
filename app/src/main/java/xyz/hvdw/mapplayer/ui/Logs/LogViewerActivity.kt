package xyz.hvdw.mapplayer.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import xyz.hvdw.mapplayer.R
import java.io.File

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val txtLog = findViewById<TextView>(R.id.txtLog)
        val btnDelete = findViewById<Button>(R.id.btnDeleteLog)
        val btnShare = findViewById<Button>(R.id.btnShareLog)

        val logFile = File(getExternalFilesDir(null), "_MapPlayer_Log/error.log")

        if (logFile.exists()) {
            txtLog.text = logFile.readText()
        } else {
            txtLog.text = getString(R.string.logviewer_no_log)
        }

        btnDelete.setOnClickListener {
            if (logFile.exists()) {
                logFile.delete()
                txtLog.text = getString(R.string.logviewer_deleted)
            }
        }

        btnShare.setOnClickListener {
            if (logFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    logFile
                )

                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, "Share error.log"))
            }
        }
    }
}
