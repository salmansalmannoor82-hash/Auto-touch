package com.autotouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
    }

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private val points = mutableListOf<TextView>()
    private var editMode = true
    private var busy = false
    private var dragPx = 300f
    private val dragMs = 300L
    private val pointSize = 120
    private lateinit var distTxt: TextView

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

    fun showPanel() {
        if (panel != null) return
        editMode = true
        points.forEach {
            styleDot(it, false)
            setTouchable(it, true)
        }

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
        row1.addView(smallBtn("✔") { goLive() })
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
        p.x = 20
        p.y = 300
        wm.addView(box, p)
        panel = box
        makeDraggable(handle, box)
    }

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
        styleDot(v, false)
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
            } else if (e.action == MotionEvent.ACTION_DOWN) {
                fire(v)
            }
            true
        }
    }

    private fun clearPoints() {
        points.forEach { try { wm.removeView(it) } catch (e: Exception) {} }
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
        } catch (e: Exception) {}
    }

    private fun goLive() {
        if (points.isEmpty()) {
            Toast.makeText(this, "Pehle ➕ se point lagao", Toast.LENGTH_SHORT).show()
            return
        }
        editMode = false
        points.forEach { styleDot(it, true) }
        panel?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        panel = null
    }

    private fun fire(v: View) {
        if (busy) return
        busy = true
        val p = v.layoutParams as WindowManager.LayoutParams
        val x = p.x + pointSize / 2f
        val y = p.y + pointSize / 2f
        setTouchable(v, false)

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, (y - dragPx).coerceAtLeast(0f))
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, dragMs))
            .build()

        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { finish(v) }
            override fun onCancelled(g: GestureDescription?) { finish(v) }
        }, null)
        if (!ok) finish(v)
    }

    private fun finish(v: View) {
        busy = false
        if (!editMode) setTouchable(v, true)
    }

    private fun closeAll() {
        clearPoints()
        panel?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        panel = null
        editMode = true
    }
}
