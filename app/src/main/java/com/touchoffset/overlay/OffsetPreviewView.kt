package com.touchoffset.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OffsetPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    // Use shiftX/shiftY to avoid any View base-class name conflicts
    var shiftX: Int = 0
        set(value) { field = value; invalidate() }
    var shiftY: Int = 0
        set(value) { field = value; invalidate() }

    // How many pixels of offset = 1 unit in preview
    private val scale = 1.2f

    private val bgPaint = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2C"); strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A3A4A"); strokeWidth = 1.5f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 2.5f; style = Paint.Style.STROKE
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CF6679"); strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(7f, 5f), 0f)
    }
    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC"); style = Paint.Style.FILL
    }
    private val dotOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA"); textSize = 24f; typeface = Typeface.MONOSPACE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h / 2f

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Grid every 30px
        var gx = cx % 30f; while (gx < w) { canvas.drawLine(gx, 0f, gx, h, gridPaint); gx += 30f }
        var gy = cy % 30f; while (gy < h) { canvas.drawLine(0f, gy, w, gy, gridPaint); gy += 30f }

        // Axis lines (brighter)
        canvas.drawLine(cx, 0f, cx, h, axisPaint)
        canvas.drawLine(0f, cy, w, cy, axisPaint)

        // Finger crosshair (centre)
        val arm = 22f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
        canvas.drawCircle(cx, cy, 9f, crossPaint)

        // Offset destination
        val dx = shiftX * scale
        val dy = shiftY * scale
        val tx = (cx + dx).coerceIn(14f, w - 14f)
        val ty = (cy + dy).coerceIn(14f, h - 14f)

        // Dashed arrow from centre to offset
        canvas.drawLine(cx, cy, tx, ty, dashPaint)

        // Offset dot
        canvas.drawCircle(tx, ty, 9f, dotFillPaint.apply { alpha = 200 })
        canvas.drawLine(tx - 14f, ty, tx + 14f, ty, dotOutPaint)
        canvas.drawLine(tx, ty - 14f, tx, ty + 14f, dotOutPaint)

        // Scale ruler hint at bottom-left
        val rulerPx = 30 * scale   // 30 units in preview space
        canvas.drawLine(8f, h - 10f, 8f + rulerPx, h - 10f,
            Paint(labelPaint).apply { color = Color.parseColor("#555555"); strokeWidth = 2f })
        canvas.drawText("30px", 8f, h - 14f, Paint(labelPaint).apply { textSize = 18f })
    }
}
