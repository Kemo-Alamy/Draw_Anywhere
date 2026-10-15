package com.example.drawanywhere

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private val requestOverlayPermission = 1001

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // forward to service via broadcast
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_ADD_IMAGE
                putExtra(OverlayService.EXTRA_IMAGE_URI, it.toString())
            }
            startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Minimal UI: request overlay permission and start service
        setTheme(R.style.Theme_DrawAnywhere)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, requestOverlayPermission)
        }

        // Start overlay service once permission is granted (user may navigate back)
        val startBtn = Button(this).apply { text = "Start Overlay" }
        setContentView(startBtn)
        startBtn.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, requestOverlayPermission)
            }
        }

        // handle implicit pick request from service
        val pickIntent = Intent(this, OverlayService::class.java)
        pickIntent.action = OverlayService.ACTION_REQUEST_IMAGE_PICK
        // Service will trigger image pick via activity, but here keep simple.
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOverlayPermission) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
            }
        }
    }
}
