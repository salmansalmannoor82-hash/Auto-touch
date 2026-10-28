package com.autotouch.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Auto Touch"
            textSize = 32f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        val b1 = Button(this).apply {
            text = "1. Accessibility On Karo"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val b2 = Button(this).apply {
            text = "2. Floating Panel Start Karo"
            setOnClickListener {
                val s = ClickService.instance
                if (s == null) {
                    Toast.makeText(
                        this@MainActivity,
                        "Pehle Accessibility me Auto Touch on karo",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    s.showPanel()
                    moveTaskToBack(true)
                }
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(b1)
        root.addView(b2)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ClickService.instance != null) {
            "Service ON ✅"
        } else {
            "Service OFF ❌\nAccessibility me Auto Touch on karo"
        }
    }
}
