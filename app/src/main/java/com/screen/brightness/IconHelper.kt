package com.screen.brightness

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG

object IconHelper {

    val PRESET_TYPES = arrayOf("sun", "brightness", "lightbulb", "lightning", "star", "circle")

    fun loadIconDrawable(context: Context): Drawable? {
        val iconType = AppPrefs.getIconType(context)
        if (iconType == "custom") {
            val svgContent = AppPrefs.getCustomSvg(context)
                ?: return getPresetDrawable(context, "sun")
            return renderSvg(context, svgContent) ?: getPresetDrawable(context, "sun")
        }
        return getPresetDrawable(context, iconType)
    }

    fun getPresetDrawable(context: Context, type: String): Drawable? {
        val resId = when (type) {
            "sun" -> R.drawable.ic_sun
            "brightness" -> R.drawable.ic_brightness
            "lightbulb" -> R.drawable.ic_lightbulb
            "lightning" -> R.drawable.ic_lightning
            "star" -> R.drawable.ic_star
            "circle" -> R.drawable.ic_circle
            else -> R.drawable.ic_sun
        }
        return ContextCompat.getDrawable(context, resId)?.mutate()
    }

    fun renderSvg(context: Context, svgContent: String): Drawable? {
        return try {
            val svg = SVG.getFromString(svgContent)
            val density = context.resources.displayMetrics.density
            val size = (48 * density).toInt()
            svg.setDocumentWidth(size.toFloat())
            svg.setDocumentHeight(size.toFloat())
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            svg.renderToCanvas(Canvas(bitmap))
            BitmapDrawable(context.resources, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    fun isValidSvg(svgContent: String): Boolean {
        return try {
            SVG.getFromString(svgContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isCustomIcon(context: Context): Boolean =
        AppPrefs.getIconType(context) == "custom"
}
