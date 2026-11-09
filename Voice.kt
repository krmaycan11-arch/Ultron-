package com.ultron.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speech in (SpeechRecognizer loop + "Ultron" wake word) and speech out (TextToSpeech). */
class Voice(private val a: Activity, private val s: Store) {
    var onText: (String) -> Unit = {}
    var onRms: (Float) -> Unit = {}
    var onChange: () -> Unit = {}
    var speaking = false
    var once = false
    var armed = false
    val active: Boolean get() = once || armed

    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var rec: SpeechRecognizer? = null
    private var running = false
    private val wakeRe = Regex("""\b(?:ultron|ultrón|altron|ultran)\b[\s,.:!?-]*(.*)""", RegexOption.IGNORE_CASE)
    private fun want() = s.wake || once || armed

    init {
        tts = TextToSpeech(a) { ok ->
            if (ok) {
                apply()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { h.post { speaking = true; onChange() } }
                    override fun onDone(id: String?) { h.post { speaking = false; onChange() } }
                    override fun onError(id: String?) { h.post { speaking = false; onChange() } }
                })
            }
        }
    }

    // ---------- speech out ----------
    class Model(val name: String, val pitch: Float, val rate: Float, val pick: Int)
    companion object {
        val MODELS = listOf(Model("ULTRON", .6f, .9f, 0), Model("NOVA", 1f, 1f, 1), Model("ECHO", 1.4f, 1.15f, 2))
    }

    fun apply() {
        val t = tts ?: return
        val m = MODELS[s.profile]
        t.setLanguage(if (s.lang == "tr") Locale("tr", "TR") else Locale.US)
        t.setSpeechRate(m.rate * s.rate)
        t.setPitch(m.pitch)
        try {
            val vs = (t.voices ?: emptySet()).filter { it.locale.language == s.lang }
                .sortedBy { (if (it.isNetworkConnectionRequired) "1" else "0") + it.name }
            if (vs.isNotEmpty()) t.setVoice(vs[m.pick % vs.size])
        } catch (e: Exception) { }
    }

    fun speak(t: String) { apply(); tts?.speak(t.take(3900), TextToSpeech.QUEUE_FLUSH, null, "u") }
    fun stopSpeak() { tts?.stop(); speaking = false; onChange() }

    // ---------- speech in ----------
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) { onRms(((v + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
        override fun onPartialResults(p: Bundle?) {}
        override fun onError(e: Int) {
            running = false
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { s.wake = false; once = false; armed = false; stopAll(); onChange(); return }
            again(if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 200 else 1200)
        }
        override fun onResults(r: Bundle?) {
            running = false
            val tx = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            if (tx.isNotBlank()) heard(tx.trim())
            again(300)
        }
    }

    private fun heard(tx: String) {
        val m = wakeRe.find(tx)
        if (m != null && speaking) stopSpeak()                       // barge-in
        if (once || armed) {
            once = false; armed = false; onChange()
            onText(m?.groupValues?.get(1)?.ifBlank { tx } ?: tx)
        } else if (s.wake && m != null) {
            val rest = m.groupValues[1].trim()
            if (rest.length > 1) onText(rest)
            else { armed = true; onChange(); h.postDelayed({ if (armed) { armed = false; onChange() } }, 9000) }
        }
    }

    private fun begin() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(a)) return
        if (rec == null) { rec = SpeechRecognizer.createSpeechRecognizer(a); rec?.setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (s.lang == "tr") "tr-TR" else "en-US")
        running = true
        rec?.startListening(i)
    }

    private fun again(ms: Long) { h.postDelayed({ if (want()) begin() else stopAll() }, ms) }

    /** Start listening for one command (mic button). */
    fun listenOnce() {
        stopSpeak(); once = true; onChange(); begin()
        h.postDelayed({ if (once) { once = false; onChange() } }, 10000)
    }
    fun cancelOnce() { once = false; armed = false; onChange(); if (!s.wake) stopAll() }
    fun ensure() { if (want()) begin() }

    fun stopAll() {
        running = false
        rec?.destroy(); rec = null
    }

    fun shutdown() { stopAll(); tts?.stop(); tts?.shutdown() }
}
