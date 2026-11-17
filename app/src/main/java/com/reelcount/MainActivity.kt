package com.reelcount

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : Activity() {

    private val BG = 0xFF0E0E12.toInt()
    private val CARD = 0xFF1A1A22.toInt()
    private val ACC = 0xFFFF4D6D.toInt()
    private val DIM = 0xFF8A8A98.toInt()
    private lateinit var s: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Store(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun bg(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(CARD, 20)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun btn(t: String, color: Int = ACC, w: Int = -1, onClick: () -> Unit) =
        Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = bg(color, 14)
            layoutParams = LinearLayout.LayoutParams(w, dp(46)).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun sw(label: String, on: Boolean, set: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        textSize = 16f
        isChecked = on
        setTextColor(Color.WHITE)
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> set(v) }
    }

    private fun enabled(): Boolean {
        val v = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return v.contains("$packageName/") && v.contains("ReelService")
    }

    private fun render() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        col.addView(tv("Reel Counter", 28f, bold = true))
        col.addView(tv("Count the scroll. Break the loop.", 14f, DIM))

        if (!enabled()) {
            val c = card()
            c.addView(tv("Not active yet", 18f, bold = true))
            c.addView(tv("Turn on Reel Counter under Accessibility > Installed apps. It only reads which screen is open, nothing else.", 14f, DIM))
            c.addView(btn("Open Accessibility settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            c.addView(tv("If the toggle is greyed out on Android 13+: open App info, tap the three dots, choose Allow restricted settings.", 12f, DIM).apply {
                setPadding(0, dp(10), 0, 0)
            })
            c.addView(btn("Open App info", 0xFF3A3A48.toInt()) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            })
            col.addView(c)
        }

        val today = LocalDate.now()
        val ig = s.count(today, "ig")
        val yt = s.count(today, "yt")
        val total = ig + yt
        col.addView(card().apply {
            addView(tv("$total", 56f, ACC, true))
            addView(tv("reels and shorts today  ·  ≈ ${total / 2} min", 14f, DIM))
            addView(tv("Instagram $ig   ·   YouTube $yt", 15f).apply { setPadding(0, dp(10), 0, 0) })
        })

        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val tots = days.map { s.total(it) }
        val mx = maxOf(1, tots.max())
        col.addView(card().apply {
            addView(tv("Last 7 days", 16f, bold = true))
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, dp(130)).apply { topMargin = dp(10) }
            }
            days.forEachIndexed { i, d ->
                val c = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                c.addView(tv("${tots[i]}", 11f, DIM))
                c.addView(View(this@MainActivity).apply {
                    background = bg(if (i == 6) ACC else 0xFF3A3A48.toInt(), 6)
                    layoutParams = LinearLayout.LayoutParams(dp(18), maxOf(dp(3), tots[i] * dp(70) / mx))
                        .apply { topMargin = dp(3) }
                })
                c.addView(tv(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), 11f, DIM))
                row.addView(c, LinearLayout.LayoutParams(0, -1, 1f))
            }
            addView(row)
        })

        col.addView(card().apply {
            addView(tv("Settings", 16f, bold = true))
            val step = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            step.addView(tv("Break every ${s.interval} reels", 16f), LinearLayout.LayoutParams(0, -2, 1f))
            step.addView(btn("−", 0xFF3A3A48.toInt(), dp(48)) { s.interval -= 10; render() })
            step.addView(btn("+", 0xFF3A3A48.toInt(), dp(48)) { s.interval += 10; render() })
            addView(step)
            addView(sw("Counter on top of the screen", s.pill) { s.pill = it })
            addView(sw("Intrusive break", s.blocker) { s.blocker = it })
        })

        if (s.diag.isNotEmpty()) col.addView(card().apply {
            addView(tv("Debug: last screen not detected as reels", 14f, bold = true))
            addView(tv(s.diag, 11f, DIM).apply { setTextIsSelectable(true) })
        })

        col.addView(btn("Reset today", 0xFF3A3A48.toInt()) { s.resetToday(); render() }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(col)
        })
    }
}
