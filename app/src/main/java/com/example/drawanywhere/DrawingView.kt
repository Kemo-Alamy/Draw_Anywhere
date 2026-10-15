package com.example.drawanywhere

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.*

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class PenType { STANDARD, LASER, ARTISTIC, HIGHLIGHTER, CALLIGRAPHY, SHAPE_RECT, SHAPE_ELLIPSE, ERASER_STROKE, ERASER_PIXEL }

    data class Stroke(
        val path: Path,
        val paint: Paint,
        val penType: PenType,
        val birth: Long = System.currentTimeMillis(),
        var lifespan: Long = 0L
    )

    private val strokes = mutableListOf<Stroke>()
    private val undone = mutableListOf<Stroke>()
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private var currentPen = PenType.STANDARD
    private val penColorMemory = mutableMapOf<PenType, Int>()
    private var lastX = 0f
    private var lastY = 0f

    // Offscreen bitmap for persistent drawing and eraser operations
    private var offscreenBitmap: Bitmap? = null
    private var offscreenCanvas: Canvas? = null

    // Laser fade handler
    private val handler = Handler(Looper.getMainLooper())
    private val laserRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            var changed = false
            val it = strokes.iterator()
            while (it.hasNext()) {
                val s = it.next()
                if (s.penType == PenType.LASER) {
                    val elapsed = now - s.birth
                    if (elapsed >= s.lifespan) {
                        it.remove(); changed = true
                    } else {
                        val alpha = ((1f - elapsed.toFloat() / s.lifespan) * 255).toInt().coerceIn(0,255)
                        s.paint.alpha = alpha
                        changed = true
                    }
                }
            }
            if (changed) invalidate()
            handler.postDelayed(this, 33)
        }
    }

    // Image overlay
    private var overlayBitmap: Bitmap? = null
    private var overlayMatrix = Matrix()
    private var scaleDetector: ScaleGestureDetector
    private var overlayOffsetX = 0f
    private var overlayOffsetY = 0f

    init {
        penColorMemory[PenType.STANDARD] = Color.RED
        penColorMemory[PenType.LASER] = Color.YELLOW
        penColorMemory[PenType.ARTISTIC] = Color.MAGENTA
        penColorMemory[PenType.HIGHLIGHTER] = Color.argb(120, 255, 255, 0)
        penColorMemory[PenType.CALLIGRAPHY] = Color.BLACK

        scaleDetector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                overlayMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        })

        handler.post(laserRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            offscreenBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            offscreenCanvas = Canvas(offscreenBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw overlay image
        overlayBitmap?.let {
            canvas.withSave {
                concat(overlayMatrix)
                drawBitmap(it, 0f, 0f, null)
            }
        }

        // Draw strokes
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }

        // Also draw current path
        currentPath?.let { p -> currentPaint?.let { paint -> canvas.drawPath(p, paint) } }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle scaling for overlay
        scaleDetector.onTouchEvent(event)

        // If overlay touched, handle pan
        overlayBitmap?.let {
            // Simple hit test: invert matrix and map point
            val inv = Matrix()
            if (overlayMatrix.invert(inv)) {
                val pts = floatArrayOf(event.x, event.y)
                inv.mapPoints(pts)
                val x = pts[0]; val y = pts[1]
                if (x >= 0 && y >= 0 && x <= it.width && y <= it.height) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lastX = event.x; lastY = event.y
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - lastX; val dy = event.y - lastY
                            overlayMatrix.postTranslate(dx, dy)
                            lastX = event.x; lastY = event.y
                            invalidate(); return true
                        }
                    }
                }
            }
        }

        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                undone.clear()
                currentPath = Path().apply { moveTo(x,y) }
                currentPaint = createPaintFor(currentPen)
                lastX = x; lastY = y
                if (currentPen == PenType.LASER) {
                    // laser lifespan 3500ms
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= 2f || dy >= 2f) {
                    currentPath?.quadTo(lastX, lastY, (x+lastX)/2, (y+lastY)/2)
                    lastX = x; lastY = y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.let { p -> currentPaint?.let { paint ->
                    // finalize stroke
                    val stroke = Stroke(Path(p), Paint(paint), currentPen, System.currentTimeMillis())
                    if (currentPen == PenType.LASER) stroke.lifespan = 3500L
                    strokes.add(stroke)
                    // If eraser stroke, we apply CLEAR on offscreen canvas
                    if (currentPen == PenType.ERASER_STROKE || currentPen == PenType.ERASER_PIXEL) {
                        // draw with CLEAR mode to offscreen
                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        offscreenCanvas?.drawPath(p, paint)
                    }
                }}
                currentPath = null; currentPaint = null
                invalidate(); return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun createPaintFor(pen: PenType): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        val color = penColorMemory.getOrElse(pen) { Color.RED }
        when (pen) {
            PenType.STANDARD -> { paint.color = color; paint.strokeWidth = 8f }
            PenType.LASER -> { paint.color = color; paint.strokeWidth = 12f; paint.alpha = 255 }
            PenType.ARTISTIC -> { paint.color = color; paint.strokeWidth = 18f; paint.setPathEffect(ComposePathEffect(CornerPathEffect(8f), null)) }
            PenType.HIGHLIGHTER -> { paint.color = color; paint.strokeWidth = 36f; paint.alpha = 160 }
            PenType.CALLIGRAPHY -> { paint.color = color; paint.strokeWidth = 14f }
            PenType.ERASER_STROKE -> { paint.color = Color.TRANSPARENT; paint.strokeWidth = 40f; paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            PenType.ERASER_PIXEL -> { paint.color = Color.TRANSPARENT; paint.strokeWidth = 12f; paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            else -> { paint.color = color; paint.strokeWidth = 8f }
        }
        return paint
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            val s = strokes.removeAt(strokes.lastIndex)
            undone.add(s)
            invalidate()
        }
    }

    fun clear() {
        strokes.clear(); undone.clear(); offscreenCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); invalidate()
    }

    fun cyclePen() {
        val arr = PenType.values()
        val next = (arr.indexOf(currentPen) + 1) % arr.size
        currentPen = arr[next]
        // ensure color exists
        penColorMemory.putIfAbsent(currentPen, Color.BLACK)
    }

    fun openColorPicker() {
        // Simple temporary color switch — pick random color for demo
        val col = (0xFF shl 24) or (Math.random() * 0xFFFFFF).toInt()
        penColorMemory[currentPen] = col
    }

    fun setOverlayImage(uri: Uri) {
        try {
            val isr = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(isr)
            isr?.close()
            overlayBitmap = bmp
            // reset matrix
            overlayMatrix.reset()
            val sx = width.toFloat() / (bmp?.width?.toFloat() ?: 1f)
            val sy = height.toFloat() / (bmp?.height?.toFloat() ?: 1f)
            val scale = min(1f, min(sx, sy))
            overlayMatrix.postScale(scale, scale)
            overlayMatrix.postTranslate((width - (bmp?.width ?: 0) * scale)/2f, (height - (bmp?.height ?: 0) * scale)/2f)
            invalidate()
        } catch (e: Exception) {
            // ignore
        }
    }
}
