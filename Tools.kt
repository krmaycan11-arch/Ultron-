package com.ultron.assistant

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local device commands. run() returns the spoken reply, or null to let the AI handle it. */
class Tools(private val a: Activity, private val s: Store) {
    private fun x(t: String, e: String) = if (s.lang == "tr") t else e
    private fun re(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private fun go(i: Intent): Boolean = try { a.startActivity(i); true } catch (e: Exception) { false }
    private fun web(u: String) = go(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    private val ws = Regex("\\s+")

    fun run(t: String): String? {
        val loc = Locale(s.lang)
        val l = t.lowercase(loc).replace("’", "").replace("'", "")

        // ---- identity, memory, notes ----
        re("""^(?:bana\s+(.+?)\s+de(?:yin)?|call me\s+(.+))$""").find(t)?.let {
            s.addr = (it.groupValues[1] + it.groupValues[2]).trim()
            return x("Tamam, sana ${s.addr} diyeceğim.", "Understood. I will call you ${s.addr}.")
        }
        re("""^(?:benim adım|adım|my name is)\s+(.+)""").find(t)?.let {
            s.addr = it.groupValues[1].trim(); return x("Memnun oldum ${s.addr}.", "Pleased to meet you, ${s.addr}.")
        }
        re("""^(?:kişiliğin|kişilik|personality)[:,\s]+(.+)""").find(t)?.let {
            s.persona = it.groupValues[1].trim(); return x("Kişiliğimi güncelledim.", "Personality updated.")
        }
        re("""^(?:hatırla|remember(?: that)?)[:,\s]+(.+)""").find(t)?.let {
            val p = s.list("prefs"); p.add(it.groupValues[1].trim()); s.saveList("prefs", p.takeLast(30))
            return x("Hafızaya aldım.", "Stored.")
        }
        if (Regex("^(unut|hafızayı sil|forget everything|clear memory)").containsMatchIn(l)) { s.wipe(); return x("Hafızamı sildim.", "Memory wiped.") }
        re("""^(?:not al|not et|not|note|take a note|write down)[:,\s]+(.+)""").find(t)?.let {
            val n = s.list("notes"); n.add(it.groupValues[1].trim()); s.saveList("notes", n)
            return x("Notu aldım.", "Note saved.")
        }
        if (Regex("notlarım|notları (oku|göster)|my notes|read my notes").containsMatchIn(l)) {
            val n = s.list("notes")
            return if (n.isEmpty()) x("Kayıtlı notun yok.", "You have no notes.") else n.mapIndexed { i, v -> "${i + 1}. $v" }.joinToString(" ")
        }

        re("""(?:ses modeli|voice model)\s*(\d)""").find(t)?.let {
            val n = it.groupValues[1].toInt()
            if (n in 1..3) { s.profile = n - 1; return x("${Voice.MODELS[n - 1].name} sesine geçtim.", "Switched to ${Voice.MODELS[n - 1].name}.") }
        }

        // ---- timer / calendar ----
        val tm = Regex("""(\d+)\s*(saniye|sn|dakika|dk|saat|second|sec|minute|min|hour)""").find(l)
        if (tm != null && Regex("hatırlat|remind|timer|zamanlayıcı|alarm").containsMatchIn(l)) {
            val n = tm.groupValues[1].toInt(); val u = tm.groupValues[2]
            val mult = when (u) { "saniye", "sn", "second", "sec" -> 1; "saat", "hour" -> 3600; else -> 60 }
            val what = t.replace(re("""(\d+)\s*\S+\s*(sonra)?|hatırlat\w*|remind( me)?|\bin\b|\bto\b|timer|alarm"""), " ").replace(ws, " ").trim()
            val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, n * mult)
                .putExtra(AlarmClock.EXTRA_MESSAGE, what.ifEmpty { "Ultron" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (go(i)) x("Tamam, $n ${u} sonrası için zamanlayıcı kurdum.", "Timer set for $n $u.") else x("Saat uygulaması bulunamadı.", "No clock app found.")
        }
        if (Regex("takvim|calendar|randevu").containsMatchIn(l) && Regex("ekle|oluştur|add|create|schedule").containsMatchIn(l)) {
            val cal = Calendar.getInstance()
            if (Regex("yarın|tomorrow").containsMatchIn(l)) cal.add(Calendar.DAY_OF_YEAR, 1)
            Regex("""(\d{1,2})[:.](\d{2})""").find(l)?.let {
                cal.set(Calendar.HOUR_OF_DAY, it.groupValues[1].toInt()); cal.set(Calendar.MINUTE, it.groupValues[2].toInt()); cal.set(Calendar.SECOND, 0)
            }
            val title = t.replace(re("""takvim\w*|calendar|ekle|oluştur|\badd\b|create|schedule|yarın|tomorrow|bugün|today|\bsaat\b|\bat\b|\d{1,2}[:.]\d{2}|\bto\b|\bmy\b"""), " ").replace(ws, " ").trim()
            val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title.ifEmpty { "Ultron" })
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000)
            return if (go(i)) x("Takvim formunu açtım, kaydetmen yeterli.", "Calendar form opened. Just hit save.") else x("Takvim uygulaması yok.", "No calendar app found.")
        }

        // ---- time, battery, network, volume ----
        if (Regex("saat kaç|saati söyle|what time|time is it").containsMatchIn(l)) return SimpleDateFormat("HH:mm", loc).format(Date())
        if (Regex("bugünün tarihi|günlerden ne|hangi gün|tarihi söyle|what(s| is) (the )?date|what day").containsMatchIn(l)) return SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(Date())
        if (Regex("""\bpil\b|şarj|batarya|battery""").containsMatchIn(l)) {
            val b = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val p = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return x("Pil yüzde $p, ${if (b.isCharging) "şarj oluyor" else "şarjda değil"}.", "Battery $p percent, ${if (b.isCharging) "charging" else "not charging"}.")
        }
        if (Regex("wi-?fi|bluetooth|bağlantı durum|internet bağlantı|connection|network status").containsMatchIn(l)) {
            val wm = a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val bt = try { (a.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled } catch (e: SecurityException) { null }
            return x("Wi-Fi ${if (wm.isWifiEnabled) "açık" else "kapalı"}, internet ${if (online) "var" else "yok"}, Bluetooth ${if (bt == null) "bilinmiyor" else if (bt) "açık" else "kapalı"}.",
                "Wi-Fi ${if (wm.isWifiEnabled) "on" else "off"}, internet ${if (online) "up" else "down"}, Bluetooth ${if (bt == null) "unknown" else if (bt) "on" else "off"}.")
        }
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Regex("""(?:ses|volume)[^\d]*(\d{1,3})""").find(l)?.let {
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, it.groupValues[1].toInt().coerceIn(0, 100) * mx / 100, AudioManager.FLAG_SHOW_UI)
            return x("Tamam.", "Done.")
        }
        if (Regex("""sesi?\s*(artır|yükselt)|volume up|louder""").containsMatchIn(l)) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return x("Sesi artırdım.", "Louder.") }
        if (Regex("""sesi?\s*(azalt|kıs|düşür)|volume down|quieter""").containsMatchIn(l)) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return x("Sesi kıstım.", "Quieter.") }

        // ---- notifications ----
        if (Regex("bildirim|notification").containsMatchIn(l) && Regex("oku|göster|read|show|var mı").containsMatchIn(l)) {
            val svc = NotifService.inst
            if (svc == null) {
                go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                return x("Bildirim erişimi kapalı. Ayarları açtım, Ultron'a izin ver.", "Notification access is off. I opened settings; allow Ultron.")
            }
            val list = svc.activeNotifications.filter { !it.isOngoing }.take(5).map { n ->
                val e = n.notification.extras
                "${e.getCharSequence("android.title") ?: ""}: ${e.getCharSequence("android.text") ?: ""}"
            }
            return if (list.isEmpty()) x("Bildirim yok.", "No notifications.") else list.joinToString(". ")
        }

        // ---- language ----
        if (Regex("ingilizce konuş|speak english|switch to english").containsMatchIn(l)) { s.lang = "en"; return "English mode on." }
        if (Regex("türkçe konuş|speak turkish|türkçeye geç").containsMatchIn(l)) { s.lang = "tr"; return "Türkçe moda geçtim." }

        // ---- apps, web ----
        re("""^(?:aç|open|başlat|launch)\s+(.+)""").find(t)?.let {
            val name = it.groupValues[1].trim().lowercase(loc)
            val pm = a.packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            val hit = apps.firstOrNull { r -> r.loadLabel(pm).toString().lowercase(loc).contains(name) }
            if (hit != null) {
                val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
                if (li != null && go(li)) return x("${hit.loadLabel(pm)} açılıyor.", "Opening ${hit.loadLabel(pm)}.")
            }
            if (name.contains(".") && !name.contains(" ") && web("https://$name")) return x("Siteyi açıyorum.", "Opening the site.")
            return x("$name adlı uygulamayı bulamadım.", "I could not find an app named $name.")
        }
        if (l.contains("youtube")) {
            val k = t.replace(re("""youtube\S*|\b(ara|search|aç|open|find|video\w*)\b"""), " ").replace(ws, " ").trim()
            web(if (k.isEmpty()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=${Uri.encode(k)}")
            return x("YouTube açılıyor.", "Opening YouTube.")
        }
        re("""^(?:ara|search(?: for)?|google)[:\s]+(.+)""").find(t)?.let {
            web("https://www.google.com/search?q=${Uri.encode(it.groupValues[1])}"); return x("Arıyorum.", "Searching.")
        }

        // ---- calculator ----
        var n = l.replace("artı", "+").replace("eksi", "-").replace("çarpı", "*").replace("bölü", "/")
            .replace(" plus ", "+").replace(" minus ", "-").replace(" times ", "*").replace("divided by", "/").replace("÷", "/")
        n = Regex("""(\d)\s*[x×]\s*(\d)""").replace(n, "$1*$2")
        val e = Regex("""[\d(][\d\s+\-*/().,^]*[\d)]""").find(n)
        if (e != null && Regex("[+\\-*/^]").containsMatchIn(e.value) &&
            (Regex("hesap|calc|kaç eder|ne eder|how much|what is").containsMatchIn(l) || Regex("""^[\d\s+\-*/().,^x×÷]+$""").matches(l))) {
            try {
                val v = calc(e.value.replace(',', '.'))
                if (v.isFinite()) return if (v == Math.rint(v) && Math.abs(v) < 1e12) v.toLong().toString() else String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
            } catch (ex: Exception) { }
        }
        return null
    }

    /** Tiny recursive-descent calculator: + - * / ^ ( ) */
    private fun calc(src: String): Double {
        val str = src.replace(" ", "")
        var i = 0
        fun peek() = if (i < str.length) str[i] else '\u0000'
        fun num(): Double { val st = i; while (peek().isDigit() || peek() == '.') i++; return str.substring(st, i).toDouble() }
        lateinit var expr: () -> Double
        fun atom(): Double {
            if (peek() == '(') { i++; val v = expr(); i++; return v }
            if (peek() == '-') { i++; return -atom() }
            return num()
        }
        fun pow(): Double { val b = atom(); if (peek() == '^') { i++; return Math.pow(b, pow()) }; return b }
        fun term(): Double {
            var v = pow()
            while (peek() == '*' || peek() == '/') { val o = str[i++]; val r = pow(); v = if (o == '*') v * r else v / r }
            return v
        }
        expr = {
            var v = term()
            while (peek() == '+' || peek() == '-') { val o = str[i++]; val r = term(); v = if (o == '+') v + r else v - r }
            v
        }
        return expr()
    }
}
