package com.reelcount

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ReelService : AccessibilityService() {

    companion object {
        private val APPS = mapOf(
            "com.instagram.android" to "ig",
            "com.google.android.youtube" to "yt"
        )

        // Substrings of view IDs that only exist while the Reels / Shorts player is on screen.
        // If Instagram or YouTube rename their views, tweak these two lists.
        private val KEYS = mapOf(
            "ig" to listOf("clips_viewer", "clips_video_container"),
            "yt" to listOf("reel_player", "reel_recycler", "shorts_")
        )

        private val PROMPTS = listOf(
            "Stand up. Stretch your neck and shoulders for 20 seconds.",
            "Drink a glass of water. Phone stays here.",
            "Do 15 push-ups or squats, then decide if you still want to scroll.",
            "Look at something far away for 20 seconds. Your eyes need it.",
            "What were you supposed to be doing right now?",
            "Take 5 slow breaths. In through the nose, out through the mouth.",
            "Text someone you actually care about instead.",
            "Close your eyes for 30 seconds. Notice how your head feels."
        )

        private const val SECONDS_PER_REEL = 30
        private const val SESSION_GAP_MS = 60_000L
    }

    private lateinit var store: Store
    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())

    private var pill: TextView? = null
    private var block: View? = null

    private var lastCheck = 0L
    private var lastCount = 0L
    private var lastSeen = 0L
    private var reelsUntil = 0L
    private var session = 0
    private var triggers = 0

    override fun onServiceConnected() {
        store = Store(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val app = APPS[pkg]
        if (app == null) {
            // Left Instagram/YouTube (keyboard, notification shade and our own overlays don't count).
            if (pkg != "com.android.systemui" && pkg != packageName) {
                reelsUntil = 0
                hidePill()
            }
            return
        }
        if (block != null) return

        val now = SystemClock.uptimeMillis()
        val scroll = e.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        if (scroll && now < reelsUntil) {
            count(app, now)
            return
        }
        if (now - lastCheck < 300) return
        lastCheck = now

        if (isReels(rootInActiveWindow, app)) {
            reelsUntil = now + 2500
            if (now - lastSeen > SESSION_GAP_MS) {
                session = 0
                triggers = 0
            }
            lastSeen = now
            showPill()
            if (scroll) count(app, now)
        } else {
            reelsUntil = 0
            hidePill()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        h.removeCallbacksAndMessages(null)
        hidePill()
        removeBlock()
        return super.onUnbind(intent)
    }

    // ---- detection ----

    private fun isReels(root: AccessibilityNodeInfo?, app: String): Boolean {
        root ?: return false
        return walk(root, KEYS.getValue(app), 0)
    }

    private fun walk(n: AccessibilityNodeInfo, keys: List<String>, depth: Int): Boolean {
        if (depth > 14) return false
        val id = n.viewIdResourceName
        if (id != null && keys.any { id.contains(it) }) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (walk(c, keys, depth + 1)) return true
        }
        return false
    }

    private fun count(app: String, now: Long) {
        if (now - lastCount < 700) return
        lastCount = now
        session++
        val total = store.inc(app)
        if (store.blocker && total % store.interval == 0) showBlock(total) else updatePill()
    }

    // ---- counter pill ----

    private fun showPill() {
        if (!store.pill) return
        if (pill == null) {
            val tv = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(6), dp(14), dp(6))
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(40)
            }
            try {
                wm.addView(tv, lp)
                pill = tv
            } catch (_: Exception) {
            }
        }
        updatePill()
    }

    private fun updatePill() {
        val tv = pill ?: return
        val total = store.total()
        val left = store.interval - total % store.interval
        tv.text = "$session now  ·  $total today"
        tv.background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(if (left <= 5 && store.blocker) 0xE6D6264A.toInt() else 0xCC000000.toInt())
        }
    }

    private fun hidePill() {
        pill?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        pill = null
    }

    // ---- intrusive break ----

    private fun showBlock(total: Int) {
        hidePill()
        triggers++
        val wait = (5 + 5 * triggers).coerceAtMost(30)
        val minutes = total * SECONDS_PER_REEL / 60

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF2101014.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
        }

        fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) =
            TextView(this).apply {
                this.text = t
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
            }

        fun button(t: String, color: Int) = Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
        }

        root.addView(text("$total", 72f, 0xFFFF4D6D.toInt(), bold = true))
        root.addView(text("reels and shorts today", 18f, Color.WHITE))
        root.addView(text("≈ $minutes min of your life, roughly", 14f, 0xFF8A8A98.toInt(), top = 6))
        root.addView(text(PROMPTS.random(), 20f, Color.WHITE, bold = true, top = 40))

        val cont = button("Continue in $wait", 0xFF3A3A48.toInt()).apply {
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(48) }
        }
        val leave = button("Close the app", 0xFFFF4D6D.toInt())
        leave.setOnClickListener {
            removeBlock()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        cont.setOnClickListener { removeBlock() }
        root.addView(cont)
        root.addView(leave)

        var left = wait
        val tick = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    cont.isEnabled = true
                    cont.text = "Continue"
                } else {
                    cont.text = "Continue in $left"
                    h.postDelayed(this, 1000)
                }
            }
        }
        h.postDelayed(tick, 1000)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            block = root
        } catch (_: Exception) {
            h.removeCallbacksAndMessages(null)
        }
    }

    private fun removeBlock() {
        h.removeCallbacksAndMessages(null)
        block?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        block = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
