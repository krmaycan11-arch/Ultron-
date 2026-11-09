package com.ultron.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Orange energy sphere: glowing core, rotating rings, latitude lines, inner shell, drifting dust. */
class OrbView(c: Context) : View(c) {
    enum class Mode(val sc: Float, val sp: Float, val gl: Float) {
        IDLE(1f, .25f, .8f), LISTEN(1.06f, .5f, 1.1f), THINK(.95f, 2.6f, 1f), SPEAK(1f, .6f, 1.3f)
    }

    var mode = Mode.IDLE
    var level = 0f
    private var sc = 1f
    private var sp = .25f
    private var gl = .8f
    private var amp = 0f
    private var ang = 0f
    private var last = 0L

    private val dust = java.util.Random(11).let { r ->
        List(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), .6f + r.nextFloat() * 1.6f, r.nextFloat() * 6.283f) }
    }
    private class P(var a: Float, val r: Float, val s: Float, val z: Float)
    private val ps = java.util.Random(7).let { rnd ->
        List(70) { P(rnd.nextFloat() * 6.283f, rnd.nextFloat(), .3f + rnd.nextFloat() * .9f, 1.2f + rnd.nextFloat() * 2.5f) }
    }
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    private fun al(v: Float) = v.toInt().coerceIn(0, 255)
    private fun ell(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry); cv.drawOval(oval, pt)
    }

    override fun onDraw(cv: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f)
        last = now
        val t = ((now / 1e6) % 1e6).toFloat() / 1000f

        sc += (mode.sc - sc) * .08f; sp += (mode.sp - sp) * .06f; gl += (mode.gl - gl) * .08f
        ang += dt * sp
        val wa = when (mode) {
            Mode.SPEAK -> .5f + .5f * abs(sin(t * 7f) * sin(t * 3.1f + 1f))
            Mode.LISTEN -> .25f + level * .75f
            else -> .12f + .08f * sin(t * 1.3f)
        }
        amp += (wa - amp) * .15f

        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h * .42f
        val r = min(w, h) * .27f * sc * (1f + amp * .1f)

        // background dust
        pt.style = Paint.Style.FILL; pt.shader = null
        for (d in dust) {
            val tw = .5f + .5f * sin(t * .8f + d[3])
            pt.color = Color.argb(al(30 + 70 * tw), 255, 190, 110)
            cv.drawCircle(d[0] * w, d[1] * h, d[2], pt)
        }

        // halo
        pt.shader = RadialGradient(cx, cy, r * 2.4f, Color.argb(al(70 * gl), 255, 110, 20), Color.argb(0, 255, 70, 0), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 2.4f, pt)
        pt.shader = null

        // latitude lines (dense near the equator)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 1.2f
        val n = 30
        for (i in 0..n) {
            val u = (i - n / 2f) / (n / 2f)
            val y = r * u * abs(u)
            val rx = sqrt(max(0f, r * r - y * y))
            val near = 1f - abs(u)
            val wob = if (mode == Mode.THINK) .07f * sin(t * 3f + i * .5f) else .02f * sin(t + i * .4f)
            val ry = rx * (.09f + .05f * abs(u) + wob + amp * .04f)
            pt.color = Color.argb(al(30 + 110 * near * near + 30 * amp), 255, 150, 50)
            ell(cv, cx, cy + y, rx, ry)
        }
        // bright equator band
        for (i in -8..8) {
            val y = i * r * .013f
            pt.color = Color.argb(al(120 - abs(i) * 11 + 40 * amp), 255, 170, 70)
            cv.drawLine(cx - r * 1.04f, cy + y, cx + r * 1.04f, cy + y, pt)
        }

        // two big rotating rings
        for (k in 0..1) {
            val a = ang * .7f + k * 1.15f
            val rxk = max(2f, r * abs(cos(a)))
            pt.strokeWidth = 8f; pt.color = Color.argb(40, 255, 120, 20); ell(cv, cx, cy, rxk, r)
            pt.strokeWidth = 1.2f
            for (j in 1..5) { pt.color = Color.argb(al(70 + 25 * j), 255, 190 - j * 8, 80); ell(cv, cx, cy, max(2f, rxk * (1f - j * .012f)), r) }
            pt.strokeWidth = 3f; pt.color = Color.argb(235, 255, 215, 130); ell(cv, cx, cy, rxk, r)
        }

        // inner shell with swirls
        val ri = r * .42f
        pt.style = Paint.Style.FILL
        pt.shader = RadialGradient(cx, cy, ri, Color.argb(10, 255, 150, 40), Color.argb(60, 255, 150, 40), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, ri, pt)
        pt.shader = null
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 1.5f
        pt.color = Color.argb(110, 255, 190, 100); cv.drawCircle(cx, cy, ri, pt)
        for (i in 0 until 7) {
            pt.color = Color.argb(al(55 + 15 * sin(t + i)), 255, 200, 120)
            val rr = ri * (.35f + .1f * i)
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
            val start = Math.toDegrees((ang * 1.5f * (if (i % 2 == 1) 1 else -1) + i * 1.1f).toDouble()).toFloat()
            cv.drawArc(oval, start, 140f, false, pt)
        }

        // orbiting sparks
        pt.style = Paint.Style.FILL
        val th = mode == Mode.THINK
        for (p in ps) {
            p.a += dt * p.s * sp * (if (th) 1.6f else .5f)
            val rr = r * (1.05f + p.r * (if (th) .5f else .25f) + amp * .2f * p.r)
            pt.color = Color.argb(al((if (th) 90f else 35f) + 110 * p.r), 255, 150 + (80 * p.r).toInt(), 60)
            cv.drawCircle(cx + cos(p.a) * rr, cy + sin(p.a) * rr * .85f, p.z, pt)
        }

        // glowing core
        val rc = r * .13f * (1f + amp * .6f)
        pt.shader = RadialGradient(
            cx, cy, rc * 4f,
            intArrayOf(Color.argb(al(200 * gl), 255, 170, 50), Color.argb(60, 255, 120, 20), Color.argb(0, 255, 90, 0)),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(cx, cy, rc * 4f, pt)
        pt.shader = RadialGradient(cx, cy, rc, intArrayOf(0xFFFFF6D0.toInt(), 0xFFFFC24A.toInt()), floatArrayOf(.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, rc, pt)
        pt.shader = null

        // light waves
        if (mode == Mode.SPEAK || mode == Mode.LISTEN) {
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f
            for (i in 0 until 3) {
                val ph = (t * .7f + i / 3f) % 1f
                val q = if (mode == Mode.SPEAK) 1.05f + ph * 1.0f else 2.0f - ph * 1.0f
                val a2 = if (mode == Mode.SPEAK) 1f - ph else ph
                pt.color = Color.argb(al(100 * a2), 255, 140, 40)
                cv.drawCircle(cx, cy, r * q, pt)
            }
        }
        postInvalidateOnAnimation()
    }
}
