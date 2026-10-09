package com.solosu.mtforum.ui.detail

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.view.animation.DecelerateInterpolator

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.widget.AppCompatImageView




















class ZoomableImageView @JvmOverloads constructor(
        @NonNull context: Context,
        @Nullable attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val baseMatrix = Matrix()  
    private val suppMatrix = Matrix()  
    private val drawMatrix = Matrix()
    private val savedMatrix = Matrix()

    private val scaleDetector: ScaleGestureDetector
    private val tapDetector: GestureDetector

    private var fitScale = 1.0f
    private var baseReady = false

    private var mode = NONE

    private val lastFinger = PointF()

    
    private var matrixAnimator: ValueAnimator? = null

    init {
        super.setScaleType(ImageView.ScaleType.MATRIX)
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        tapDetector = GestureDetector(context, TapListener())
        
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                setupBase()
            }
        })
    }

    
    private fun setupBase() {
        val d: Drawable? = drawable
        if (d == null) {
            return
        }
        val vw = width - paddingLeft - paddingRight
        val vh = height - paddingTop - paddingBottom
        if (vw <= 0 || vh <= 0) {
            return
        }
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0) {
            return
        }
        if (baseReady) {
            return 
        }
        baseReady = true
        var scale = Math.min(vw / dw, vh / dh)
        if (scale <= 0) {
            scale = 1.0f
        }
        fitScale = scale
        baseMatrix.reset()
        baseMatrix.postScale(scale, scale)
        
        val tx = (vw - dw * scale) / 2.0f
        val ty = (vh - dh * scale) / 2.0f
        baseMatrix.postTranslate(tx + paddingLeft, ty + paddingTop)
        suppMatrix.reset()
        applyMatrix()
    }

    override fun setImageDrawable(@Nullable drawable: Drawable?) {
        super.setImageDrawable(drawable)
        baseReady = false 
        if (width > 0 && height > 0 && drawable != null) {
            setupBase()
        }
    }

    private fun applyMatrix() {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        setImageMatrix(drawMatrix)
        invalidate()
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        drawMatrix.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    
    private fun isZoomedIn(): Boolean = currentScale() > fitScale * 1.01f

    




    private fun clampTranslation(supp: Matrix) {
        val d: Drawable = drawable ?: return
        val dw0 = d.intrinsicWidth.toFloat()
        val dh0 = d.intrinsicHeight.toFloat()
        if (dw0 <= 0 || dh0 <= 0) return

        val m = Matrix()
        m.set(baseMatrix)
        m.postConcat(supp)
        val v = FloatArray(9)
        m.getValues(v)
        val scale = v[Matrix.MSCALE_X]
        if (scale <= 0f) return

        val vw = (width - paddingLeft - paddingRight).toFloat()
        val vh = (height - paddingTop - paddingBottom).toFloat()
        if (vw <= 0f || vh <= 0f) return

        val dw = dw0 * scale
        val dh = dh0 * scale
        val left = v[Matrix.MTRANS_X]
        val top = v[Matrix.MTRANS_Y]

        val corrX = if (dw <= vw) {
            (paddingLeft + (vw - dw) / 2f) - left
        } else {
            val minL = paddingLeft + vw - dw 
            val maxL = paddingLeft.toFloat() 
            when {
                left > maxL -> maxL - left
                left < minL -> minL - left
                else -> 0f
            }
        }
        val corrY = if (dh <= vh) {
            (paddingTop + (vh - dh) / 2f) - top
        } else {
            val minT = paddingTop + vh - dh
            val maxT = paddingTop.toFloat()
            when {
                top > maxT -> maxT - top
                top < minT -> minT - top
                else -> 0f
            }
        }
        if (corrX != 0f || corrY != 0f) {
            supp.postTranslate(corrX, corrY)
        }
    }

    
    private fun disallowParentIntercept(disallow: Boolean) {
        var p: ViewParent? = parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = p.parent
        }
    }

    private fun cancelMatrixAnimator() {
        matrixAnimator?.let {
            it.cancel()
            matrixAnimator = null
        }
    }

    
    private fun animateSuppMatrix(from: Matrix, to: Matrix, duration: Long) {
        cancelMatrixAnimator()
        val a = FloatArray(9)
        val b = FloatArray(9)
        from.getValues(a)
        to.getValues(b)
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = duration
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { va ->
            val t = va.animatedValue as Float
            val c = FloatArray(9)
            for (i in 0 until 9) {
                c[i] = a[i] + (b[i] - a[i]) * t
            }
            val mm = Matrix()
            mm.setValues(c)
            suppMatrix.set(mm)
            applyMatrix()
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (matrixAnimator === anim) matrixAnimator = null
                
                clampTranslation(suppMatrix)
                applyMatrix()
                disallowParentIntercept(isZoomedIn())
            }
        })
        matrixAnimator = anim
        anim.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelMatrixAnimator()
        }
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(suppMatrix)
                lastFinger.set(event.x, event.y)
                mode = DRAG
                
                disallowParentIntercept(isZoomedIn())
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                savedMatrix.set(suppMatrix)
                mode = ZOOM
                
                disallowParentIntercept(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && event.pointerCount == 1 && isZoomedIn()) {
                    disallowParentIntercept(true)
                    val dx = event.x - lastFinger.x
                    val dy = event.y - lastFinger.y
                    suppMatrix.set(savedMatrix)
                    suppMatrix.postTranslate(dx, dy)
                    clampTranslation(suppMatrix)
                    applyMatrix()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                
                
                val remainIndex = if (event.actionIndex == 0) 1 else 0
                if (event.pointerCount - 1 >= 1 && remainIndex < event.pointerCount) {
                    savedMatrix.set(suppMatrix)
                    lastFinger.set(event.getX(remainIndex), event.getY(remainIndex))
                    mode = DRAG
                } else {
                    mode = NONE
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                val cur = Matrix()
                cur.set(suppMatrix)
                val target = Matrix()
                target.set(suppMatrix)
                
                val scale = currentScale()
                if (scale < fitScale * 0.999f) {
                    target.reset()
                }
                clampTranslation(target)
                if (!matricesClose(cur, target)) {
                    animateSuppMatrix(cur, target, 180L)
                } else {
                    suppMatrix.set(target)
                    applyMatrix()
                    disallowParentIntercept(isZoomedIn())
                }
            }
        }
        return true
    }

    private fun matricesClose(a: Matrix, b: Matrix): Boolean {
        val va = FloatArray(9)
        val vb = FloatArray(9)
        a.getValues(va)
        b.getValues(vb)
        for (i in 0 until 9) {
            if (Math.abs(va[i] - vb[i]) > 0.5f) return false
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var factor = detector.scaleFactor
            val target = currentScale() * factor
            val max = fitScale * MAX_ZOOM_FACTOR
            val min = fitScale * 0.5f
            if (target > max) {
                factor = max / currentScale()
            } else if (target < min) {
                factor = min / currentScale()
            }
            suppMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation(suppMatrix)
            applyMatrix()
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            
            disallowParentIntercept(true)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            clampTranslation(suppMatrix)
            applyMatrix()
        }
    }

    
    fun interface OnViewTapListener {
        fun onViewTap()
    }

    private var tapCallback: OnViewTapListener? = null

    fun setOnViewTapListener(l: OnViewTapListener) {
        this.tapCallback = l
    }

    private inner class TapListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cur = Matrix()
            cur.set(suppMatrix)
            val target = Matrix()
            if (currentScale() > fitScale * 1.01f) {
                target.reset() 
            } else {
                target.postScale(DOUBLE_TAP_FACTOR, DOUBLE_TAP_FACTOR, e.x, e.y)
                clampTranslation(target)
            }
            animateSuppMatrix(cur, target, 220L)
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (tapCallback != null) {
                tapCallback!!.onViewTap()
            }
            return true
        }
    }

    override fun onDetachedFromWindow() {
        cancelMatrixAnimator()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MAX_ZOOM_FACTOR = 4.0f   
        private const val DOUBLE_TAP_FACTOR = 2.5f 

        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
