package com.screen.brightness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        } else {
            updateStatus(getString(R.string.status_notification_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)

        actionButton.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        when {
            !Settings.canDrawOverlays(this) -> {
                updateStatus(getString(R.string.status_overlay_needed))
                actionButton.text = getString(R.string.btn_grant_overlay)
                actionButton.setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            !Settings.System.canWrite(this) -> {
                updateStatus(getString(R.string.status_write_settings_needed))
                actionButton.text = getString(R.string.btn_grant_settings)
                actionButton.setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED -> {
                updateStatus(getString(R.string.status_notification_needed))
                actionButton.text = getString(R.string.btn_grant_notification)
                actionButton.setOnClickListener {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            else -> {
                startFloatingService()
                updateStatus(getString(R.string.status_running))
                actionButton.text = getString(R.string.btn_stop)
                actionButton.setOnClickListener {
                    stopService(Intent(this, FloatingButtonService::class.java))
                    Toast.makeText(this, getString(R.string.toast_stopped), Toast.LENGTH_SHORT).show()
                    updateStatus(getString(R.string.status_stopped))
                    actionButton.text = getString(R.string.btn_start)
                    actionButton.setOnClickListener {
                        checkPermissionsAndStart()
                    }
                }
            }
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(intent)
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }
}
