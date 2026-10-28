package com.autotouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

// NOTE: minSdk 26 chahiye (continueStroke ke liye)
// accessibility_service_config.xml me: android:canPerformGestures="true"

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
        private const val TAG = "ClickService"
    }

    private lateinit var wm: WindowManager

    // UI
    private var bubble: TextView? = null      // side ka chhota banda
    private var panel: View? = null
    private var liveBtn: Button? = null
    private lateinit var distTxt: TextView
    private val points = mutableListOf<TextView>()

    // settings
    private var editMode = true
    private var dragPx = 300f
    private val cycleMs = 300L                // ek swipe cycle ka time
    private val pointSize = 120
    private val bubbleSize = 110

    // panel ki position yaad rakhne ke liye
    private var panelX = 20
    private var panelY = 300

    // hold state
    private var activeDot: View? = null
    private var holding = false
    private var lastStroke: GestureDescription.StrokeDescription? = null
    private var gx = 0f
    private var gy = 0f

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        closeAll()
        instance = null
        return super.onUnbind(intent)
    }

    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    // ---------------------------------------------------------------
    // BUBBLE (chhota banda): tap = panel open/close, drag = move
    // ---------------------------------------------------------------

    /** Purane code se compatible: MainActivity yahi call kare to bubble aa jayega */
    fun showPanel() = showBubble()

    fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "◉"
            textSize = 22f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC111111"))
                setStroke(4, Color.parseColor("#00E5FF"))
            }
        }
        val p = overlayParams(bubbleSize, bubbleSize)
        p.x = 0
        p.y = 400
        wm.addView(b, p)
        bubble = b
        attachBubbleTouch(b)
    }

    private fun attachBubbleTouch(b: View) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        b.setOnTouchListener { v, e ->
            val p = b.layoutParams as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        p.x = sx + dx.toInt()
                        p.y = sy + dy.toInt()
                        wm.updateViewLayout(b, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        v.performClick()
                        togglePanel()
                    }
                }
            }
            true
        }
    }

    private fun togglePanel() {
        if (panel == null) openPanel() else closePanel()
    }

    // ---------------------------------------------------------------
    // PANEL
    // ---------------------------------------------------------------

    private fun makeDraggable(handle: View, target: View) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        handle.setOnTouchListener { _, e ->
            val p = target.layoutParams as WindowManager.LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = sx + (e.rawX - tx).toInt()
                    p.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(target, p)
                }
            }
            true
        }
    }

    private fun smallBtn(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 8, 24, 8)
            setOnClickListener { action() }
        }
    }

    private fun openPanel() {
        if (panel != null) return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6111111"))
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        val handle = TextView(this).apply {
            text = "≡ Auto Touch"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val live = smallBtn(if (editMode) "✔" else "✏") { toggleLive() }
        liveBtn = live
        row1.addView(live)
        row1.addView(smallBtn("➕") { addPoint() })
        row1.addView(smallBtn("🗑") { clearPoints() })
        row1.addView(smallBtn("✖") { closeAll() })

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        distTxt = TextView(this).apply {
            text = "↑${dragPx.toInt()}"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 0)
        }
        row2.addView(smallBtn("−") {
            if (dragPx > 50f) dragPx -= 50f
            distTxt.text = "↑${dragPx.toInt()}"
        })
        row2.addView(distTxt)
        row2.addView(smallBtn("+") {
            if (dragPx < 1000f) dragPx += 50f
            distTxt.text = "↑${dragPx.toInt()}"
        })

        box.addView(handle)
        box.addView(row1)
        box.addView(row2)

        val p = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        p.x = panelX
        p.y = panelY
        wm.addView(box, p)
        panel = box
        makeDraggable(handle, box)
    }

    /** Sirf panel hatata hai, dots jaise the waise rehte hain */
    private fun closePanel() {
        panel?.let {
            try {
                val p = it.layoutParams as WindowManager.LayoutParams
                panelX = p.x
                panelY = p.y
                wm.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "closePanel", e)
            }
        }
        panel = null
        liveBtn = null
    }

    // ---------------------------------------------------------------
    // DOTS
    // ---------------------------------------------------------------

    private fun styleDot(v: TextView, live: Boolean) {
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (live) "#4400E5FF" else "#9900E5FF"))
            setStroke(4, Color.parseColor("#B400FF"))
        }
    }

    private fun addPoint() {
        val n = points.size
        val v = TextView(this).apply {
            text = "${n + 1}"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        styleDot(v, !editMode)
        val p = overlayParams(pointSize, pointSize)
        p.x = 300 + n * 30
        p.y = 600 + n * 30
        wm.addView(v, p)
        points.add(v)
        attachPointTouch(v)
    }

    private fun attachPointTouch(v: View) {
        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        v.setOnTouchListener { _, e ->
            val p = v.layoutParams as WindowManager.LayoutParams
            if (editMode) {
                // edit mode: dot ko drag karke fire button ke upar rakho
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = sx + (e.rawX - tx).toInt()
                        p.y = sy + (e.rawY - ty).toInt()
                        wm.updateViewLayout(v, p)
                    }
                }
            } else {
                // live mode: dabao = action shuru, hatao = action band
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> startHold(v)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> stopHold(v)
                }
            }
            true
        }
    }

    private fun clearPoints() {
        holding = false
        lastStroke = null
        activeDot = null
        points.forEach {
            try { wm.removeView(it) } catch (e: Exception) { Log.w(TAG, "clearPoints", e) }
        }
        points.clear()
    }

    private fun setTouchable(v: View, touchable: Boolean) {
        try {
            val p = v.layoutParams as WindowManager.LayoutParams
            p.flags = if (touchable) {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            wm.updateViewLayout(v, p)
        } catch (e: Exception) {
            Log.w(TAG, "setTouchable", e)
        }
    }

    // ---------------------------------------------------------------
    // LIVE / EDIT toggle (panel band hone par bhi dots chalte rehte hain)
    // ---------------------------------------------------------------

    private fun toggleLive() {
        if (editMode) {
            if (points.isEmpty()) {
                Toast.makeText(this, "Pehle ➕ se point lagao", Toast.LENGTH_SHORT).show()
                return
            }
            editMode = false
        } else {
            editMode = true
        }
        points.forEach { styleDot(it, !editMode) }
        liveBtn?.text = if (editMode) "✔" else "✏"
    }

    // ---------------------------------------------------------------
    // HOLD: dot dabao = swipe cycle repeat, ungli hatao = ruk jao
    // Gesture dot ke center par hota hai (jahan fire button hai),
    // isliye dot fire button ke saath hi kaam karta hai.
    // ---------------------------------------------------------------

    private fun startHold(v: View) {
        if (activeDot != null) return
        activeDot = v
        holding = true
        val p = v.layoutParams as WindowManager.LayoutParams
        gx = p.x + pointSize / 2f
        gy = p.y + pointSize / 2f
        // dot ko untouchable karo taki gesture neeche fire button tak pahunche
        setTouchable(v, false)
        sendStroke(true)
    }

    private fun stopHold(v: View) {
        if (activeDot === v) holding = false   // agla callback gesture ko khatam kar dega
    }

    private fun sendStroke(first: Boolean) {
        val more = holding
        val path = Path().apply {
            moveTo(gx, gy)
            if (more) {
                lineTo(gx, (gy - dragPx).coerceAtLeast(0f))
                lineTo(gx, gy)
            }
        }
        val dur = if (more) cycleMs else 10L
        val stroke = if (first || lastStroke == null) {
            GestureDescription.StrokeDescription(path, 0, dur, more)
        } else {
            lastStroke!!.continueStroke(path, 0, dur, more)
        }
        lastStroke = stroke

        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (more) sendStroke(false) else endHold()
                }
                override fun onCancelled(g: GestureDescription?) { endHold() }
            },
            null
        )
        if (!ok) endHold()
    }

    private fun endHold() {
        holding = false
        lastStroke = null
        activeDot?.let { setTouchable(it, true) }
        activeDot = null
    }

    // ---------------------------------------------------------------

    private fun closeAll() {
        holding = false
        lastStroke = null
        activeDot = null
        clearPoints()
        closePanel()
        bubble?.let {
            try { wm.removeView(it) } catch (e: Exception) { Log.w(TAG, "closeAll", e) }
        }
        bubble = null
        editMode = true
    }
}
