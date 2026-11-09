package com.ultron.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KProperty

/** Everything is kept in app-private SharedPreferences (the API key too). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("ultron", Context.MODE_PRIVATE)

    private inner class Str(val k: String, val d: String = "") {
        operator fun getValue(t: Any?, pr: KProperty<*>): String = p.getString(k, d) ?: d
        operator fun setValue(t: Any?, pr: KProperty<*>, v: String) { p.edit().putString(k, v).apply() }
    }

    var apiKey by Str("key")
    var lang by Str("lang", "tr")
    var addr by Str("addr")
    var persona by Str("persona")
    var tone by Str("tone", "c")
    var voice by Str("voice")
    private var rateS by Str("rate", "1.0")
    private var pitchS by Str("pitch", "1.0")
    var rate: Float
        get() = rateS.toFloatOrNull() ?: 1f
        set(v) { rateS = v.toString() }
    var pitch: Float
        get() = pitchS.toFloatOrNull() ?: 1f
        set(v) { pitchS = v.toString() }
    var wake: Boolean
        get() = p.getBoolean("wake", false)
        set(v) { p.edit().putBoolean("wake", v).apply() }

    private var profileS by Str("profile", "0")
    var profile: Int
        get() = profileS.toIntOrNull()?.coerceIn(0, 2) ?: 0
        set(v) { profileS = v.toString() }

    fun list(k: String): MutableList<String> {
        val a = JSONArray(p.getString(k, "[]") ?: "[]")
        return MutableList(a.length()) { a.getString(it) }
    }
    fun saveList(k: String, l: List<String>) { p.edit().putString(k, JSONArray(l).toString()).apply() }

    fun hist(): MutableList<Pair<String, String>> {
        val a = JSONArray(p.getString("hist", "[]") ?: "[]")
        return MutableList(a.length()) { val o = a.getJSONObject(it); o.getString("r") to o.getString("c") }
    }
    fun saveHist(h: List<Pair<String, String>>) {
        val a = JSONArray()
        h.takeLast(30).forEach { a.put(JSONObject().put("r", it.first).put("c", it.second)) }
        p.edit().putString("hist", a.toString()).apply()
    }
    fun wipe() { p.edit().remove("hist").remove("prefs").remove("addr").remove("persona").apply() }
}
