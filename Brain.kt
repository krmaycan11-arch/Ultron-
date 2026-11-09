package com.ultron.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

data class Ans(val text: String, val src: List<String>, val ok: Boolean)

/** Talks to the Anthropic Messages API. Call from a background thread. */
class Brain(private val s: Store) {
    private fun x(t: String, e: String) = if (s.lang == "tr") t else e
    private val live = Regex("haber|hava|news|weather|güncel|latest|bugün|today|fiyat|price|skor|score|araştır|research|kimdir|who is", RegexOption.IGNORE_CASE)

    private fun sys(): String {
        val tone = when (s.tone) { "f" -> "warm and friendly"; "r" -> "formal and professional"; else -> "calm, precise, dry wit, quietly confident" }
        val prefs = s.list("prefs")
        return "You are ULTRON, a voice AI assistant on the user's Android tablet. Reply in ${if (s.lang == "tr") "Turkish" else "English"}. Style: $tone" +
            (if (s.persona.isNotEmpty()) "; extra personality: ${s.persona}" else "") +
            ". Answer in at most 3 short sentences unless the user wants detail or code. Plain text only, no markdown, no emojis. " +
            (if (s.addr.isNotEmpty()) "Address the user as ${s.addr}. " else "") +
            (if (prefs.isNotEmpty()) "Known about the user: ${prefs.joinToString("; ")}. " else "") +
            "Now: ${Date()}. Device actions (apps, timers, calendar, volume, notifications) are handled before you; never claim to have done them yourself."
    }

    private fun post(body: JSONObject): Pair<Int, String> {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("x-api-key", s.apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to txt
    }

    fun ask(text: String, hist: List<Pair<String, String>>): Ans {
        if (s.apiKey.isBlank()) return Ans(x("API anahtarı yok. Ayarlardan ekle.", "No API key. Add it in settings."), emptyList(), false)
        val m = JSONArray()
        var last = ""
        for ((r, c) in hist.takeLast(10)) {
            if (m.length() == 0 && r != "user") continue
            if (r == last) continue
            m.put(JSONObject().put("role", r).put("content", c)); last = r
        }
        if (last == "user") m.remove(m.length() - 1)
        m.put(JSONObject().put("role", "user").put("content", text))
        val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 600).put("system", sys()).put("messages", m)
        val useWeb = live.containsMatchIn(text)
        try {
            if (useWeb) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 3)))
            var (code, res) = post(body)
            if (useWeb && code == 400) { body.remove("tools"); val r2 = post(body); code = r2.first; res = r2.second }
            if (code == 401) return Ans(x("API anahtarı geçersiz.", "Invalid API key."), emptyList(), false)
            if (code !in 200..299) return Ans(x("Sunucu hatası ($code).", "Server error ($code)."), emptyList(), false)
            val c = JSONObject(res).getJSONArray("content")
            val sb = StringBuilder()
            val src = LinkedHashSet<String>()
            for (i in 0 until c.length()) {
                val b = c.getJSONObject(i)
                when (b.optString("type")) {
                    "text" -> sb.append(b.optString("text"))
                    "web_search_tool_result" -> {
                        val a = b.optJSONArray("content")
                        if (a != null) for (j in 0 until a.length()) {
                            val u = a.getJSONObject(j).optString("url")
                            if (u.isNotEmpty()) src.add(u)
                        }
                    }
                }
            }
            return Ans(sb.toString().trim().ifEmpty { "…" }, src.take(3), true)
        } catch (e: Exception) {
            return Ans(x("Bağlantı kurulamadı.", "Could not connect."), emptyList(), false)
        }
    }
}
