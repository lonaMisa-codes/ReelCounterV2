package com.reelcount

import android.content.Context
import java.time.LocalDate

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("reels", Context.MODE_PRIVATE)

    var interval: Int
        get() = p.getInt("interval", 50)
        set(v) = p.edit().putInt("interval", v.coerceIn(10, 500)).apply()

    var pill: Boolean
        get() = p.getBoolean("pill", true)
        set(v) = p.edit().putBoolean("pill", v).apply()

    var blocker: Boolean
        get() = p.getBoolean("blocker", true)
        set(v) = p.edit().putBoolean("blocker", v).apply()

    /** Debug: view IDs seen the last time Instagram/YouTube was open but reels weren't detected. */
    var diag: String
        get() = p.getString("diag", "") ?: ""
        set(v) = p.edit().putString("diag", v).apply()

    private fun key(d: LocalDate, app: String) = "c_${d}_$app"

    fun count(d: LocalDate, app: String) = p.getInt(key(d, app), 0)

    fun total(d: LocalDate = LocalDate.now()) = count(d, "ig") + count(d, "yt")

    /** Adds one to today's count for [app] ("ig" or "yt") and returns today's total. */
    fun inc(app: String): Int {
        val k = key(LocalDate.now(), app)
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
        return total()
    }

    fun resetToday() {
        val t = LocalDate.now()
        p.edit().remove(key(t, "ig")).remove(key(t, "yt")).apply()
    }
}
