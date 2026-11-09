package com.ultron.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var s: Store
    private lateinit var voice: Voice
    private lateinit var tools: Tools
    private lateinit var brain: Brain
    private lateinit var orb: OrbView
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var panel: LinearLayout
    private lateinit var input: EditText
    private var thinking = false
    private val hist = mutableListOf<Pair<String, String>>()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun x(t: String, e: String) = if (s.lang == "tr") t else e
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun askPerms() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
        requestPermissions(p.toTypedArray(), 1)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        s = Store(this); voice = Voice(this, s); tools = Tools(this, s); brain = Brain(s)
        hist.addAll(s.hist())
        build()
        voice.onText = { handle(it) }
        voice.onRms = { orb.level = it }
        voice.onChange = { refresh() }
        if (!hasMic()) askPerms()
        add("◆ " + x("Ultron çevrimiçi. Seni dinliyorum.", "Ultron online. I am listening."))
        if (s.apiKey.isBlank()) askKey()
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(0xFFFFB347.toInt()); minWidth = dp(48); isAllCaps = false
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x1FFF6A00); setStroke(dp(1), 0x59FF8C28) }
        setOnClickListener { f() }
    }

    private fun build() {
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF050403.toInt())
        orb = OrbView(this)
        orb.setOnClickListener { if (voice.speaking) voice.stopSpeak() else panel.visibility = View.VISIBLE }
        root.addView(orb, FrameLayout.LayoutParams(MATCH, MATCH))

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(64), 0) }
        head.addView(TextView(this).apply { text = "ULTRON"; setTextColor(0xFFFFB347.toInt()); letterSpacing = .35f; textSize = 18f }, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(btn("⚙") { settings() })
        panel.addView(head)
        panel.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        status = TextView(this).apply { setTextColor(0xFFFFB347.toInt()); textSize = 13f; gravity = Gravity.CENTER; letterSpacing = .2f }
        panel.addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
        log = TextView(this).apply { setTextColor(0xFFF3E6D8.toInt()); textSize = 15f; setPadding(0, dp(6), 0, dp(6)) }
        scroll = ScrollView(this).apply { addView(log) }
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH, (resources.displayMetrics.heightPixels * .26).toInt()))

        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = x("Ultron’a yaz…", "Type to Ultron…"); setTextColor(0xFFF3E6D8.toInt()); setHintTextColor(0xFF8A7766.toInt())
            setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> send(); true }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(btn("🎙") { mic() })
        bar.addView(btn("➤") { send() })
        panel.addView(bar)
        root.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))

        root.addView(btn("◐") { panel.visibility = if (panel.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE },
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(14), 0) })
        setContentView(root)
        refresh()
    }

    private fun add(line: String) { log.append(line + "\n\n"); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }

    private fun refresh() {
        orb.mode = when {
            thinking -> OrbView.Mode.THINK
            voice.speaking -> OrbView.Mode.SPEAK
            voice.active -> OrbView.Mode.LISTEN
            else -> OrbView.Mode.IDLE
        }
        if (orb.mode != OrbView.Mode.LISTEN) orb.level = 0f
        status.text = when (orb.mode) {
            OrbView.Mode.THINK -> x("DÜŞÜNÜYORUM", "THINKING")
            OrbView.Mode.SPEAK -> x("KONUŞUYORUM", "SPEAKING")
            OrbView.Mode.LISTEN -> x("DİNLİYORUM", "LISTENING")
            else -> if (s.wake) x("\"ULTRON\" DE", "SAY \"ULTRON\"") else x("HAZIR", "READY")
        }
    }

    private fun send() { val v = input.text.toString(); input.setText(""); handle(v) }

    private fun mic() {
        if (!hasMic()) { askPerms(); return }
        if (voice.active) voice.cancelOnce() else voice.listenOnce()
    }

    private fun handle(t0: String) {
        val t = t0.trim()
        if (t.isEmpty()) return
        voice.stopSpeak(); voice.once = false; voice.armed = false
        add("▸ $t")
        val r = try { tools.run(t) } catch (e: Exception) { null }
        if (r != null) { finish(t, Ans(r, emptyList(), true)); return }
        thinking = true; refresh()
        val snapshot = hist.toList()
        Thread {
            val ans = brain.ask(t, snapshot)
            runOnUiThread { thinking = false; finish(t, ans) }
        }.start()
    }

    private fun finish(t: String, a: Ans) {
        if (a.ok) { hist.add("user" to t); hist.add("assistant" to a.text); while (hist.size > 30) hist.removeAt(0); s.saveHist(hist) }
        add("◆ " + a.text + a.src.joinToString("") { "\n  ↗ $it" })
        voice.speak(a.text)
        refresh()
    }

    private fun askKey() {
        val e = EditText(this)
        e.hint = "sk-ant-…"; e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this).setTitle("Anthropic API key (console.anthropic.com)").setView(e)
            .setPositiveButton("OK") { _, _ -> s.apiKey = e.text.toString().trim() }
            .setNegativeButton(x("İptal", "Cancel"), null).show()
    }

    private fun settings() {
        val items = arrayOf(
            "API key", "TR / EN", x("Ses modeli", "Voice model"), x("Konuşma hızı", "Speech speed"),
            x("Kişilik tonu", "Personality tone"), x("Uyandırma kelimesi: ", "Wake word: ") + (if (s.wake) "ON" else "OFF"),
            x("Bildirim erişimi", "Notification access"), x("Hafızayı sil", "Wipe memory")
        )
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> askKey()
                1 -> { s.lang = if (s.lang == "tr") "en" else "tr"; voice.stopAll(); voice.ensure(); voice.apply(); refresh(); add("◆ " + s.lang.uppercase()) }
                2 -> pickVoice()
                3 -> { val r = listOf(.8f, 1f, 1.2f, 1.5f); s.rate = r[(r.indexOf(s.rate) + 1) % r.size]; add("◆ ×${s.rate}") }
                4 -> { val r = listOf("c", "f", "r"); s.tone = r[(r.indexOf(s.tone) + 1) % r.size]; add("◆ " + listOf("cold", "friendly", "formal")[r.indexOf(s.tone)]) }
                5 -> {
                    if (!hasMic()) { askPerms() } else {
                        s.wake = !s.wake
                        if (s.wake) voice.ensure() else if (!voice.active) voice.stopAll()
                        refresh()
                    }
                }
                6 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                7 -> { s.wipe(); hist.clear(); add("◆ " + x("Hafıza silindi.", "Memory wiped.")) }
            }
        }.show()
    }

    private fun pickVoice() {
        val names = Voice.MODELS.mapIndexed { i, m -> (if (i == s.profile) "● " else "○ ") + m.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(x("Ses modeli", "Voice model")).setItems(names) { _, i ->
            s.profile = i
            voice.speak(x("Ben ${Voice.MODELS[i].name}. Seni dinliyorum.", "I am ${Voice.MODELS[i].name}. I am listening."))
        }.show()
    }

    override fun onResume() { super.onResume(); if (s.wake && hasMic()) voice.ensure(); refresh() }
    override fun onPause() { super.onPause(); voice.stopAll(); voice.stopSpeak() }
    override fun onDestroy() { super.onDestroy(); voice.shutdown() }
}
