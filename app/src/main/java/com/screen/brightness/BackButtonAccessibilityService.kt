package com.screen.brightness

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

class BackButtonAccessibilityService : AccessibilityService() {

    companion object {
        var instance: BackButtonAccessibilityService? = null
            private set
    }

    private var helper: FloatingButtonHelper? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (AppPrefs.isBackEnabled(this)) {
            createButton()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        helper?.destroy()
        instance = null
        super.onDestroy()
    }

    fun refreshButton() {
        if (!AppPrefs.isBackEnabled(this)) {
            helper?.destroy()
            helper = null
            return
        }
        if (helper?.isCreated() == true) {
            helper?.refresh(buildConfig())
        } else {
            createButton()
        }
    }

    private fun createButton() {
        helper?.destroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        helper = FloatingButtonHelper(
            context = this,
            windowManager = wm,
            config = buildConfig(),
            onClick = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onLongClick = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onPositionSaved = { posX, posY ->
                AppPrefs.setBackPosX(this, posX)
                AppPrefs.setBackPosY(this, posY)
            }
        )
        helper?.create()
    }

    private fun buildConfig(): ButtonConfig {
        return ButtonConfig(
            sizeDp = AppPrefs.getBackSize(this),
            bgColor = AppPrefs.getBackBgColor(this),
            fgColor = AppPrefs.getBackFgColor(this),
            iconAlpha = AppPrefs.getBackIconAlpha(this),
            bgAlpha = AppPrefs.getBackBgAlpha(this),
            posX = AppPrefs.getBackPosX(this),
            posY = AppPrefs.getBackPosY(this),
            iconDrawable = ContextCompat.getDrawable(this, R.drawable.ic_back)?.mutate(),
            isCustomIcon = false,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        )
    }
}
