package com.solosu.mtforum.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.widget.TextView







class UrlDrawable(targetView: View, sizePx: Int) : ColorDrawable(0x00000000), Drawable.Callback {

    private var real: Drawable? = null
    private var target: View? = null

    init {
        this.target = targetView
        setBounds(0, 0, sizePx, sizePx)
    }

    
    fun setReal(drawable: Drawable?, targetView: View?) {
        this.real = drawable
        this.target = targetView
        val drawableLocal = drawable
        if (drawableLocal != null) {
            drawableLocal.callback = this
            val db = drawableLocal.bounds
            if (db.width() > 0 && db.height() > 0) {
                setBounds(db)
            } else {
                drawableLocal.bounds = getBounds()
            }
            if (drawableLocal is Animatable) {
                drawableLocal.start()
            }
        }
        if (targetView is TextView) {
            
            val tv: TextView = targetView
            tv.post(Runnable {
                try {
                    val cs = tv.text
                    tv.text = cs
                } catch (ignore: Exception) {
                    tv.postInvalidate()
                }
            })
        } else if (targetView != null) {
            targetView.postInvalidate()
        }
    }

    




    fun setRealNoRelayout(drawable: Drawable?) {
        this.real = drawable
        val drawableLocal = drawable ?: return
        drawableLocal.callback = this
        val db = drawableLocal.bounds
        if (db.width() > 0 && db.height() > 0) {
            setBounds(db)
        } else {
            drawableLocal.bounds = getBounds()
        }
        if (drawableLocal is Animatable) {
            drawableLocal.start()
        }
        val t = target
        if (t is TextView) {
            t.post(Runnable {
                try {
                    val cs = t.text
                    t.text = cs
                } catch (ignore: Exception) {
                    t.postInvalidate()
                }
            })
        } else {
            t?.postInvalidate()
        }
    }

    override fun invalidateDrawable(who: Drawable) {
        target?.postInvalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        val delay = `when` - SystemClock.uptimeMillis()
        target?.postDelayed(what, maxOf(0L, delay))
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        target?.removeCallbacks(what)
    }

    override fun draw(canvas: Canvas) {
        val realLocal = real
        if (realLocal != null) {
            realLocal.bounds = bounds
            realLocal.draw(canvas)
        } else {
            super.draw(canvas)
        }
    }

    override fun getIntrinsicWidth(): Int {
        val realLocal = real
        return if (realLocal != null) realLocal.getIntrinsicWidth() else getBounds().width()
    }

    override fun getIntrinsicHeight(): Int {
        val realLocal = real
        return if (realLocal != null) realLocal.getIntrinsicHeight() else getBounds().height()
    }

    override fun setAlpha(alpha: Int) {
        val realLocal = real
        if (realLocal != null) {
            realLocal.setAlpha(alpha)
        }
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        val realLocal = real
        if (realLocal != null) {
            realLocal.setColorFilter(colorFilter)
        }
    }

    override fun getOpacity(): Int {
        val realLocal = real
        return if (realLocal != null) realLocal.getOpacity() else PixelFormat.TRANSPARENT
    }
}





class RoundedImageDrawable(
    private val content: Drawable,
    private val radiusPx: Float,
    private val strokeWidthPx: Float = 0f,
    private val strokeColor: Int = 0
) : Drawable(), Drawable.Callback {

    private val path = Path()
    private val rect = RectF()
    private val strokePaint = if (strokeWidthPx > 0f && strokeColor != 0) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = strokeColor
        }
    } else null

    init {
        content.callback = this
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        rect.set(b)
        path.reset()
        path.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)
        content.bounds = b
        content.draw(canvas)
        canvas.restoreToCount(save)

        
        if (strokePaint != null) {
            val inset = strokeWidthPx / 2f
            val strokeRect = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            val strokeRadius = maxOf(0f, radiusPx - inset)
            canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint)
        }
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    override fun setAlpha(alpha: Int) {
        content.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        content.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = content.intrinsicWidth

    override fun getIntrinsicHeight(): Int = content.intrinsicHeight
}
