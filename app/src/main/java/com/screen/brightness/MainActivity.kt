package com.screen.brightness

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var previewButton: ImageView

    private val bgColors = intArrayOf(
        0x80333333.toInt(), 0x80000000.toInt(), 0x801565C0.toInt(),
        0x80C62828.toInt(), 0x802E7D32.toInt(), 0x80FF8F00.toInt()
    )

    private val fgColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(),
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF000000.toInt()
    )

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        } else {
            updateStatus(getString(R.string.status_notification_denied))
        }
    }

    private val svgPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        importSvg(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)
        previewButton = findViewById(R.id.preview_button)

        actionButton.setOnClickListener {
            checkPermissionsAndStart()
        }

        setupSettings()
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
                if (!isServiceRunning()) {
                    startFloatingService()
                }
                updateStatus(getString(R.string.status_running))
                actionButton.text = getString(R.string.btn_stop)
                actionButton.setOnClickListener {
                    stopService(Intent(this, FloatingButtonService::class.java))
                    Toast.makeText(this, getString(R.string.toast_stopped), Toast.LENGTH_SHORT)
                        .show()
                    updateStatus(getString(R.string.status_stopped))
                    actionButton.text = getString(R.string.btn_start)
                    actionButton.setOnClickListener {
                        checkPermissionsAndStart()
                    }
                }
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingButtonService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        startForegroundService(intent)
    }

    private fun sendRefreshIntent() {
        if (isServiceRunning()) {
            val intent = Intent(this, FloatingButtonService::class.java).apply {
                action = FloatingButtonService.ACTION_REFRESH
            }
            startService(intent)
        }
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    // --- SVG 导入 ---

    private fun importSvg(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val svgContent = inputStream.bufferedReader().readText()
            inputStream.close()

            if (!IconHelper.isValidSvg(svgContent)) {
                Toast.makeText(this, getString(R.string.toast_svg_error), Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = getFileName(uri) ?: "custom.svg"
            AppPrefs.setCustomSvg(this, svgContent, fileName)
            AppPrefs.setIconType(this, "custom")

            Toast.makeText(this, getString(R.string.toast_svg_imported), Toast.LENGTH_SHORT).show()
            setupIconRow()
            updatePreview()
            sendRefreshIntent()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_svg_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return null
    }

    // --- 设置 UI ---

    private fun setupSettings() {
        updatePreview()
        setupColorRow(findViewById(R.id.bg_color_row), bgColors, AppPrefs.getBgColor(this)) { color ->
            AppPrefs.setBgColor(this, color)
            updatePreview()
            sendRefreshIntent()
        }
        setupColorRow(findViewById(R.id.fg_color_row), fgColors, AppPrefs.getFgColor(this)) { color ->
            AppPrefs.setFgColor(this, color)
            updatePreview()
            sendRefreshIntent()
        }
        setupIconRow()
        setupAlphaSeekBar()
        setupSizeSeekBar()
    }

    private fun updatePreview() {
        val bgColor = AppPrefs.getBgColor(this)
        val fgColor = AppPrefs.getFgColor(this)
        val alpha = AppPrefs.getAlpha(this)
        val sizeDp = AppPrefs.getSize(this)
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        val iconPadding = (sizePx * 0.22f).toInt()

        previewButton.setImageDrawable(IconHelper.loadIconDrawable(this))
        previewButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
        previewButton.alpha = alpha
        previewButton.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        previewButton.layoutParams = previewButton.layoutParams.apply {
            width = sizePx
            height = sizePx
        }

        if (IconHelper.isCustomIcon(this)) {
            previewButton.colorFilter = null
        } else {
            previewButton.setColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun setupColorRow(
        container: LinearLayout,
        colors: IntArray,
        selectedColor: Int,
        onSelect: (Int) -> Unit
    ) {
        container.removeAllViews()
        val circleSize = (36 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        for (color in colors) {
            val view = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == selectedColor) {
                        setStroke(
                            (3 * resources.displayMetrics.density).toInt(),
                            ContextCompat.getColor(this@MainActivity, R.color.accent)
                        )
                    }
                }
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                    setMargins(margin, 0, margin, 0)
                }
                setOnClickListener {
                    onSelect(color)
                    setupColorRow(container, colors, color, onSelect)
                }
            }
            container.addView(view)
        }
    }

    private fun setupIconRow() {
        val container = findViewById<LinearLayout>(R.id.icon_row)
        container.removeAllViews()
        val selectedType = AppPrefs.getIconType(this)
        val iconSize = (36 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        val padding = (6 * resources.displayMetrics.density).toInt()

        for (type in IconHelper.PRESET_TYPES) {
            val iv = ImageView(this).apply {
                setImageDrawable(IconHelper.getPresetDrawable(this@MainActivity, type))
                setColorFilter(Color.DKGRAY, PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    setMargins(margin, 0, margin, 0)
                }
                if (type == selectedType) {
                    setBackgroundColor(Color.parseColor("#30000000"))
                }
                setOnClickListener {
                    AppPrefs.setIconType(this@MainActivity, type)
                    setupIconRow()
                    updatePreview()
                    sendRefreshIntent()
                }
            }
            container.addView(iv)
        }

        // 自定义 SVG 已导入时显示
        if (selectedType == "custom") {
            val customIv = ImageView(this).apply {
                setImageDrawable(IconHelper.loadIconDrawable(this@MainActivity))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    setMargins(margin, 0, margin, 0)
                }
                setBackgroundColor(Color.parseColor("#30000000"))
            }
            container.addView(customIv)
        }

        // 导入按钮
        val importBtn = TextView(this).apply {
            text = getString(R.string.settings_import_svg)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setOnClickListener {
                svgPickerLauncher.launch("*/*")
            }
        }
        container.addView(importBtn)
    }

    private fun setupAlphaSeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.alpha_seekbar)
        val currentAlpha = AppPrefs.getAlpha(this)
        seekBar.progress = ((currentAlpha - 0.2f) * 100).toInt()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val alpha = 0.2f + progress / 100f
                AppPrefs.setAlpha(this@MainActivity, alpha)
                updatePreview()
                sendRefreshIntent()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSizeSeekBar() {
        val seekBar = findViewById<SeekBar>(R.id.size_seekbar)
        val currentSize = AppPrefs.getSize(this)
        seekBar.progress = currentSize - 40

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val sizeDp = 40 + progress
                AppPrefs.setSize(this@MainActivity, sizeDp)
                updatePreview()
                sendRefreshIntent()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
