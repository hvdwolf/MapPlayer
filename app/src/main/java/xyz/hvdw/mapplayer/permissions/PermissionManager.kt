package xyz.hvdw.mapplayer.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    private const val REQUEST_CODE = 1234

    fun hasAllPermissions(activity: Activity): Boolean {
        val permissions = requiredPermissions()

        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(activity, perm) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: Activity) {
        val permissions = requiredPermissions()
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }
}
