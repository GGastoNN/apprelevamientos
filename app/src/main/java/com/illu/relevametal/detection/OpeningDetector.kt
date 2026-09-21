package com.illu.relevametal.detection

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detector offline liviano. Busca rectángulos con bordes fuertes.
 * Es una ayuda visual: el operario siempre confirma/ajusta el vano.
 */
class OpeningDetector {
    data class Candidate(val rect: RectF, val confidence: Float)

    fun detect(source: Bitmap): List<Candidate> {
        val maxSide = 480
        val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
        val w = max(32, (source.width * scale).toInt())
        val h = max(32, (source.height * scale).toInt())
        val bmp = if (w == source.width && h == source.height) source else Bitmap.createScaledBitmap(source, w, h, true)
        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = c shr 16 and 0xff
            val g = c shr 8 and 0xff
            val b = c and 0xff
            gray[i] = (r * 30 + g * 59 + b * 11) / 100
        }
        val edge = IntArray(w * h)
        var sum = 0L
        var count = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = -gray[i-w-1] - 2*gray[i-1] - gray[i+w-1] + gray[i-w+1] + 2*gray[i+1] + gray[i+w+1]
            val gy = -gray[i-w-1] - 2*gray[i-w] - gray[i-w+1] + gray[i+w-1] + 2*gray[i+w] + gray[i+w+1]
            val mag = abs(gx) + abs(gy)
            edge[i] = mag
            sum += mag
            count++
        }
        val threshold = ((sum / max(1, count)) * 2.2).toInt().coerceIn(80, 420)
        val out = mutableListOf<Candidate>()
        val minW = w / 6
        val minH = h / 6
        val steps = 7
        for (left in 0 until w - minW step max(6, w / 28)) {
            for (right in left + minW until w step max(8, w / 22)) {
                for (top in 0 until h - minH step max(6, h / 28)) {
                    for (bottom in top + minH until h step max(8, h / 22)) {
                        val rw = right-left; val rh = bottom-top
                        if (rw * rh < w*h/14) continue
                        val border = borderScore(edge, w, h, left, top, right, bottom, threshold)
                        if (border > .58f) {
                            val area = (rw*rh).toFloat()/(w*h)
                            val conf = (border * .82f + min(.18f, area*.25f)).coerceAtMost(.98f)
                            val sx = source.width.toFloat()/w; val sy = source.height.toFloat()/h
                            out += Candidate(RectF(left*sx, top*sy, right*sx, bottom*sy), conf)
                        }
                    }
                }
            }
        }
        return out.sortedByDescending { it.confidence }.fold(mutableListOf()) { acc, c ->
            if (acc.none { iou(it.rect, c.rect) > .55f }) acc += c
            acc
        }.take(3)
    }

    private fun borderScore(e:IntArray,w:Int,h:Int,l:Int,t:Int,r:Int,b:Int,thr:Int):Float {
        var hit=0; var n=0
        fun p(x:Int,y:Int){ if(x in 0 until w && y in 0 until h){n++; if(e[y*w+x]>thr) hit++} }
        val sx=max(1,(r-l)/40); val sy=max(1,(b-t)/40)
        for(x in l..r step sx){ p(x,t); p(x,b) }
        for(y in t..b step sy){ p(l,y); p(r,y) }
        return if(n==0) 0f else hit.toFloat()/n
    }
    private fun iou(a:RectF,b:RectF):Float {
        val l=max(a.left,b.left); val t=max(a.top,b.top); val r=min(a.right,b.right); val bo=min(a.bottom,b.bottom)
        val inter=max(0f,r-l)*max(0f,bo-t); val union=a.width()*a.height()+b.width()*b.height()-inter
        return if(union<=0f) 0f else inter/union
    }
}
