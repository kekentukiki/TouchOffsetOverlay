package com.touchoffset.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Mini drawing canvas to preview the offset effect:
 * - White stroke = your actual finger path
 * - Purple stroke = where the touch lands after offset
 */
class TestSketchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var shiftX: Int = 0
    var shiftY: Int = 0

    private val fingerPath = Path()
    private val offsetPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    private val bgPaint = Paint().apply { color = Color.parseColor("#111122") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E33"); strokeWidth = 1f
    }
    private val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val offsetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fingerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val offsetDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC"); style = Paint.Style.FILL
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444466"); textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private var dotX = -1f; private var dotY = -1f
    private var hasDrawn = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // Grid
        var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += 40f }
        var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += 40f }

        if (!hasDrawn) {
            canvas.drawText("Draw here to test offset →", width / 2f, height / 2f, hintPaint)
            return
        }
        canvas.drawPath(fingerPath, fingerPaint)
        canvas.drawPath(offsetPath, offsetPaint)
        if (dotX >= 0) {
            canvas.drawCircle(dotX, dotY, 8f, fingerDotPaint)
            canvas.drawCircle(dotX + shiftX, dotY + shiftY, 8f, offsetDotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Prevent parent ScrollView from stealing our touch events
        parent?.requestDisallowInterceptTouchEvent(true)
        val fx = event.x; val fy = event.y
        val ox = fx + shiftX; val oy = fy + shiftY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hasDrawn = true
                fingerPath.moveTo(fx, fy)
                offsetPath.moveTo(ox, oy)
                lastX = fx; lastY = fy
            }
            MotionEvent.ACTION_MOVE -> {
                fingerPath.quadTo(lastX, lastY, (fx + lastX) / 2, (fy + lastY) / 2)
                offsetPath.quadTo(lastX + shiftX, lastY + shiftY,
                    (ox + lastX + shiftX) / 2, (oy + lastY + shiftY) / 2)
                lastX = fx; lastY = fy
            }
            MotionEvent.ACTION_UP -> { }
        }
        dotX = fx; dotY = fy
        invalidate()
        return true
    }

    fun clear() {
        fingerPath.reset(); offsetPath.reset()
        dotX = -1f; dotY = -1f; hasDrawn = false
        invalidate()
    }
}
