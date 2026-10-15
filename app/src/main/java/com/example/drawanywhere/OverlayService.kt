package com.example.drawanywhere

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var toolbarView: View
    private lateinit var drawingView: DrawingView

    private var isToolbarHidden = false
    private var isHorizontalLayout = true
    private var clearOnHide = false

    private val handler = Handler(Looper.getMainLooper())
    private val fadeRunnable = Runnable { fadeToolbar(true) }

    private val toolColors = mutableMapOf<PenToolType, Int>()
    private val toolWidths = mutableMapOf<PenToolType, Float>()

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "draw_channel")
            .setContentTitle("Draw With Kemo Active")
            .setContentText("Tap overlay toolbar to draw anywhere.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build()
        startForeground(1, notification)

        setupDrawingOverlay()
        setupToolbarOverlay()
    }

    private fun setupDrawingOverlay() {
        drawingView = DrawingView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(drawingView, params)
    }

    private fun setupToolbarOverlay() {
        toolbarView = LayoutInflater.from(this).inflate(R.layout.toolbar_layout, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        setupToolbarInteractions(toolbarView, params)
        windowManager.addView(toolbarView, params)
        resetFadeTimer()
    }

    private fun setupToolbarInteractions(toolbar: View, params: WindowManager.LayoutParams) {
        val rootLayout = toolbar.findViewById<LinearLayout>(R.id.toolbarRoot)
        val btnHide = toolbar.findViewById<ImageView>(R.id.btnHide)
        val btnUndo = toolbar.findViewById<ImageView>(R.id.btnUndo)
        val btnClear = toolbar.findViewById<ImageView>(R.id.btnClear)
        val btnTools = toolbar.findViewById<ImageView>(R.id.btnTools)
        val btnColor = toolbar.findViewById<ImageView>(R.id.btnColor)
        val btnSettings = toolbar.findViewById<ImageView>(R.id.btnSettings)

        rootLayout.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                fadeToolbar(false)
                resetFadeTimer()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(toolbar, params)
                        return true
                    }
                }
                return false
            }
        })

        btnHide.setOnClickListener {
            isToolbarHidden = !isToolbarHidden
            if (isToolbarHidden) {
                if (clearOnHide) drawingView.clearCanvas()
                drawingView.visibility = View.GONE
                rootLayout.visibility = View.GONE
            } else {
                drawingView.visibility = View.VISIBLE
                rootLayout.visibility = View.VISIBLE
            }
        }

        btnUndo.setOnClickListener { drawingView.undo() }
        btnClear.setOnClickListener { drawingView.clearCanvas() }

        btnTools.setOnClickListener { showToolSelectorPopup(it) }
        btnColor.setOnClickListener { showColorPickerPopup(it) }
        btnSettings.setOnClickListener { showSettingsDialog() }
    }

    private fun showToolSelectorPopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Standard Pen")
        popup.menu.add("Laser Pointer")
        popup.menu.add("Artistic Brush")
        popup.menu.add("Highlighter")
        popup.menu.add("Calligraphy Pen")
        popup.menu.add("Rectangle")
        popup.menu.add("Ellipse")
        popup.menu.add("Stroke Eraser")

        popup.setOnMenuItemClickListener { item ->
            saveCurrentToolMemory()
            drawingView.activeTool = when (item.title) {
                "Laser Pointer" -> PenToolType.LASER
                "Artistic Brush" -> PenToolType.BRUSH
                "Highlighter" -> PenToolType.HIGHLIGHTER
                "Calligraphy Pen" -> PenToolType.CALLIGRAPHY
                "Rectangle" -> PenToolType.RECTANGLE
                "Ellipse" -> PenToolType.ELLIPSE
                "Stroke Eraser" -> PenToolType.STROKE_ERASER
                else -> PenToolType.STANDARD
            }
            loadCurrentToolMemory()
            true
        }
        popup.show()
    }

    private fun showColorPickerPopup(anchor: View) {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.BLACK, Color.MAGENTA, Color.CYAN)
        val popup = PopupMenu(this, anchor)
        colors.forEachIndexed { index, color ->
            popup.menu.add(0, index, index, getColorName(color))
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedColor = colors[item.itemId]
            drawingView.currentColor = selectedColor
            toolColors[drawingView.activeTool] = selectedColor
            true
        }
        popup.show()
    }

    private fun getColorName(color: Int): String = when (color) {
        Color.RED -> "Red"
        Color.GREEN -> "Green"
        Color.BLUE -> "Blue"
        Color.YELLOW -> "Yellow"
        Color.BLACK -> "Black"
        Color.MAGENTA -> "Magenta"
        Color.CYAN -> "Cyan"
        else -> "Color"
    }

    private fun saveCurrentToolMemory() {
      toolColors[drawingView.activeTool] = drawingView.currentColor
      toolWidths[drawingView.activeTool] = drawingView.currentStrokeWidth
    }

    private fun loadCurrentToolMemory() {
        drawingView.currentColor = toolColors[drawingView.activeTool] ?: Color.RED
        drawingView.currentStrokeWidth = toolWidths[drawingView.activeTool] ?: 12f
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.settings_dialog, null)
        val layoutSwitch = dialogView.findViewById<Switch>(R.id.switchLayout)
        val clearHideCheck = dialogView.findViewById<CheckBox>(R.id.chkClearOnHide)
        val touchRoutingCheck = dialogView.findViewById<CheckBox>(R.id.chkTouchRouting)
        val btnQuit = dialogView.findViewById<Button>(R.id.btnQuitService)

        layoutSwitch.isChecked = isHorizontalLayout
        clearHideCheck.isChecked = clearOnHide
        touchRoutingCheck.isChecked = drawingView.isFingerDrawingEnabled

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Draw With Kemo Settings")
            .setView(dialogView)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        layoutSwitch.setOnCheckedChangeListener { _, isChecked ->
            isHorizontalLayout = isChecked
            val root = toolbarView.findViewById<LinearLayout>(R.id.toolbarRoot)
            root.orientation = if (isChecked) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        clearHideCheck.setOnCheckedChangeListener { _, isChecked -> clearOnHide = isChecked }
        touchRoutingCheck.setOnCheckedChangeListener { _, isChecked ->
            drawingView.isFingerDrawingEnabled = isChecked
            val drawingParams = drawingView.layoutParams as WindowManager.LayoutParams
            if (isChecked) {
                drawingParams.flags = drawingParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                drawingParams.flags = drawingParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            windowManager.updateViewLayout(drawingView, drawingParams)
        }

        btnQuit.setOnClickListener {
            dialog.dismiss()
            stopSelf()
        }

        dialog.show()
    }

    private fun fadeToolbar(fade: Boolean) {
        toolbarView.alpha = if (fade) 0.3f else 1.0f
    }

    private fun resetFadeTimer() {
        handler.removeCallbacks(fadeRunnable)
        handler.postDelayed(fadeRunnable, 3000)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "draw_channel",
            "Drawing Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacks(fadeRunnable)
        if (::toolbarView.isInitialized) windowManager.removeView(toolbarView)
        if (::drawingView.isInitialized) windowManager.removeView(drawingView)
    }
}
