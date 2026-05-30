package com.touchoffset.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Custom view that draws a crosshair visualisation:
 *  - White crosshair at centre  → where your finger touches
 *  - Purple dot with arrow line → where the touch lands after offset
 */
class OffsetPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var offsetX: Int = 0
        set(value) { field = value; invalidate() }
    var offsetY: Int = 0
        set(value) { field = value; invalidate() }

    // Scale: how many dp per pixel of offset shown in the preview
    private val previewScale = 0.4f

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2C")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }
    private val redLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CF6679")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC")
        style = Paint.Style.FILL
    }
    private val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val arm = min(w, h) * 0.32f

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Grid lines every 20% of size
        val step = min(w, h) / 5f
        var x = cx % step
        while (x <= w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += step }
        var y = cy % step
        while (y <= h) { canvas.drawLine(0f, y, w, y, gridPaint); y += step }

        // Centre crosshair (finger position)
        canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
        canvas.drawCircle(cx, cy, 10f, Paint(crossPaint).apply { style = Paint.Style.STROKE; strokeWidth = 2f })

        // Offset destination
        val dx = offsetX * previewScale
        val dy = offsetY * previewScale
        val tx = (cx + dx).coerceIn(16f, w - 16f)
        val ty = (cy + dy).coerceIn(16f, h - 16f)

        if (offsetX != 0 || offsetY != 0) {
            // Dashed line from centre to offset point
            canvas.drawLine(cx, cy, tx, ty, redLinePaint)
            // Offset dot
            canvas.drawCircle(tx, ty, 10f, dotPaint.apply { alpha = 200 })
            canvas.drawCircle(tx, ty, 10f, dotOutlinePaint)
            // Small cross at offset point
            val sm = 14f
            canvas.drawLine(tx - sm, ty, tx + sm, ty, dotOutlinePaint)
            canvas.drawLine(tx, ty - sm, tx, ty + sm, dotOutlinePaint)
        }
    }
}
