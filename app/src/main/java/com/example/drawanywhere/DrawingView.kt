package com.example.drawanywhere

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.util.*

enum class PenToolType {
    STANDARD, LASER, BRUSH, HIGHLIGHTER, CALLIGRAPHY, RECTANGLE, ELLIPSE, STROKE_ERASER, PIXEL_ERASER
}

data class StrokeItem(
    val path: Path,
    val paint: Paint,
    val type: PenToolType,
    val timestamp: Long = System.currentTimeMillis()
)

class DrawingView(context: Context) : View(context) {

    private val strokes = mutableListOf<StrokeItem>()
    private val undoneStrokes = mutableListOf<StrokeItem>()
    private var currentPath = Path()
    private var currentPaint = Paint()

    var activeTool: PenToolType = PenToolType.STANDARD
        set(value) {
            field = value
            updatePaintSettings()
        }

    var currentColor: Int = Color.RED
        set(value) {
            field = value
            currentPaint.color = value
        }

    var currentStrokeWidth: Float = 12f
        set(value) {
            field = value
            currentPaint.strokeWidth = value
        }

    var currentOpacity: Int = 255
        set(value) {
            field = value
            currentPaint.alpha = value
        }

    var isFingerDrawingEnabled: Boolean = true

    var overlayBitmap: Bitmap? = null
    private var imageMatrix = Matrix()

    init {
        setupPaint()
        Timer().schedule(object : TimerTask() {
            override fun run() {
                if (strokes.any { it.type == PenToolType.LASER }) {
                    val now = System.currentTimeMillis()
                    strokes.removeAll { it.type == PenToolType.LASER && (now - it.timestamp > 3500) }
                    postInvalidate()
                }
            }
        }, 0, 500)
    }

    private fun setupPaint() {
        currentPaint.isAntiAlias = true
        currentPaint.style = Paint.Style.STROKE
        currentPaint.strokeJoin = Paint.Join.ROUND
        currentPaint.strokeCap = Paint.Cap.ROUND
        updatePaintSettings()
    }

    private fun updatePaintSettings() {
        currentPaint.color = currentColor
        currentPaint.strokeWidth = currentStrokeWidth
        currentPaint.alpha = currentOpacity

        when (activeTool) {
            PenToolType.HIGHLIGHTER -> {
                currentPaint.style = Paint.Style.STROKE
                currentPaint.strokeWidth = currentStrokeWidth * 3f
                currentPaint.alpha = (255 * 0.35).toInt()
                currentPaint.xfermode = null
            }
            PenToolType.STROKE_ERASER -> {
                currentPaint.style = Paint.Style.STROKE
                currentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {
                currentPaint.style = Paint.Style.STROKE
                currentPaint.xfermode = null
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        overlayBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, imageMatrix, null)
        }
        for (item in strokes) {
            canvas.drawPath(item.path, item.paint)
        }
        canvas.drawPath(currentPath, currentPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isFingerDrawingEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                undoneStrokes.clear()
                currentPath = Path()
                currentPath.moveTo(x, y)
                if (activeTool == PenToolType.STROKE_ERASER) {
                    performStrokeEraser(x, y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == PenToolType.STROKE_ERASER) {
                    performStrokeEraser(x, y)
                } else {
                    currentPath.lineTo(x, y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activeTool != PenToolType.STROKE_ERASER) {
                    strokes.add(StrokeItem(currentPath, Paint(currentPaint), activeTool))
                }
                currentPath = Path()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun performStrokeEraser(x: Float, y: Float) {
        val iterator = strokes.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val bounds = RectF()
            item.path.computeBounds(bounds, true)
            if (bounds.contains(x, y)) {
                iterator.remove()
            }
        }
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            val removed = strokes.removeAt(strokes.size - 1)
            undoneStrokes.add(removed)
            invalidate()
        }
    }

    fun clearCanvas() {
        strokes.clear()
        undoneStrokes.clear()
        currentPath.reset()
        invalidate()
    }
}
