package com.example.drawanywhere

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "com.example.drawanywhere.action.START"
        const val ACTION_ADD_IMAGE = "com.example.drawanywhere.action.ADD_IMAGE"
        const val ACTION_REQUEST_IMAGE_PICK = "com.example.drawanywhere.action.REQUEST_IMAGE_PICK"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val NOTIF_CHANNEL = "draw_anywhere_channel"
        const val NOTIF_ID = 4242
    }

    private lateinit var windowManager: WindowManager
    private var toolbarView: View? = null
    private var drawingView: DrawingView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fadeDelay = 3000L
    private val fadeAlpha = 0.3f
    private val fadeRunnable = Runnable { toolbarView?.animate()?.alpha(fadeAlpha)?.setDuration(250)?.start() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        inflateToolbar()
        addDrawingView()
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Draw Anywhere")
            .setContentText("Overlay active")
            .setSmallIcon(R.drawable.ic_palette)
            .setOngoing(true)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(NOTIF_CHANNEL, "Draw Anywhere", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    private fun inflateToolbar() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        toolbarView = inflater.inflate(R.layout.overlay_toolbar, null)

        toolbarView?.alpha = 1f

        // setup buttons
        toolbarView?.findViewById<ImageButton>(R.id.btn_toggle_visibility)?.setOnClickListener {
            drawingView?.let { dv -> dv.visibility = if (dv.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_undo)?.setOnClickListener {
            drawingView?.undo()
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_clear)?.setOnClickListener {
            drawingView?.clear()
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_image_overlay)?.setOnClickListener {
            requestImagePick()
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_pen_switch)?.setOnClickListener {
            drawingView?.cyclePen()
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_color_picker)?.setOnClickListener {
            drawingView?.openColorPicker()
        }
        toolbarView?.findViewById<ImageButton>(R.id.btn_settings_exit)?.setOnClickListener {
            stopSelf()
        }

        // touch / hover to cancel or schedule fade
        toolbarView?.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> cancelFade()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scheduleFade()
            }
            false
        }
        toolbarView?.setOnHoverListener { _, _ -> cancelFade(); false }

        scheduleFade()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 100

        windowManager.addView(toolbarView, params)
    }

    private fun addDrawingView() {
        drawingView = DrawingView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        windowManager.addView(drawingView, params)
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fadeRunnable)
        handler.postDelayed(fadeRunnable, fadeDelay)
    }

    private fun cancelFade() {
        handler.removeCallbacks(fadeRunnable)
        toolbarView?.animate()?.alpha(1f)?.setDuration(50)?.start()
    }

    private fun requestImagePick() {
        // Launch an activity to pick image - since Service cannot start activity for result easily
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(intent, "Select image for overlay")
        startActivity(chooser)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_ADD_IMAGE -> {
                    val uriString = it.getStringExtra(EXTRA_IMAGE_URI)
                    uriString?.let { s ->
                        val uri = Uri.parse(s)
                        drawingView?.setOverlayImage(uri)
                    }
                }
                ACTION_START -> {
                    // noop: already started onCreate
                }
                ACTION_REQUEST_IMAGE_PICK -> requestImagePick()
                else -> {
                    // Handle unknown or null action safely
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            toolbarView?.let { windowManager.removeView(it) }
            drawingView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
