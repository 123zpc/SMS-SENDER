package com.smsagent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.sqrt

class LiquidGlassNavView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var onTabSelectedListener: ((index: Int) -> Unit)? = null
    var selectedIndex = 0
        private set

    private val density = resources.displayMetrics.density

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.5f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val tabTitles = listOf("转发", "记录", "状态", "日志")
    private val tabCount = 4

    private var pillX = 0f
    private var basePillWidth = 0f
    private var isDragging = false

    private var currentStretchFactor = 1.0f
    private var lastTouchX = 0f

    private var positionAnimator: ValueAnimator? = null
    private var stretchAnimator: ValueAnimator? = null

    private val isDarkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            basePillWidth = (w.toFloat() / tabCount) - (8f * density)
            pillX = getTabTargetX(selectedIndex)
        }
    }

    private fun getTabTargetX(index: Int): Float {
        val cellWidth = width.toFloat() / tabCount
        return (index * cellWidth) + (cellWidth - basePillWidth) / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cellWidth = width.toFloat() / tabCount
        val touchX = event.x.coerceIn(0f, width.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                positionAnimator?.cancel()
                stretchAnimator?.cancel()
                lastTouchX = touchX
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = touchX - lastTouchX
                    lastTouchX = touchX

                    val speed = abs(deltaX)
                    val targetStretch = 1.0f + (speed / (14f * density)).coerceAtMost(0.30f)
                    currentStretchFactor += (targetStretch - currentStretchFactor) * 0.35f

                    val currentWidth = basePillWidth * currentStretchFactor
                    val targetX = touchX - currentWidth / 2f
                    pillX = targetX.coerceIn(4f * density, width - currentWidth - 4f * density)

                    val nearestTab = (touchX / cellWidth).toInt().coerceIn(0, tabCount - 1)
                    if (nearestTab != selectedIndex) {
                        selectedIndex = nearestTab
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val finalTab = (touchX / cellWidth).toInt().coerceIn(0, tabCount - 1)
                    animateToTab(finalTab)
                }
            }
        }
        return true
    }

    fun selectTab(index: Int, animate: Boolean = true) {
        if (index !in 0 until tabCount) return
        selectedIndex = index
        if (animate && width > 0) {
            animateToTab(index)
        } else {
            pillX = getTabTargetX(index)
            currentStretchFactor = 1.0f
            invalidate()
        }
    }

    private fun animateToTab(index: Int) {
        selectedIndex = index
        val targetX = getTabTargetX(index)
        positionAnimator?.cancel()
        stretchAnimator?.cancel()

        positionAnimator = ValueAnimator.ofFloat(pillX, targetX).apply {
            duration = 340L
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                pillX = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        stretchAnimator = ValueAnimator.ofFloat(currentStretchFactor, 1.0f).apply {
            duration = 380L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { anim ->
                currentStretchFactor = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        onTabSelectedListener?.invoke(index)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val darkMode = isDarkMode
        val cellWidth = w / tabCount
        val baseTop = 6f * density
        val baseBottom = h - 6f * density
        val baseH = baseBottom - baseTop

        val pillW = basePillWidth * currentStretchFactor
        val pillH = baseH / sqrt(currentStretchFactor)
        val pillTop = (h - pillH) / 2f
        val pillBottom = pillTop + pillH
        val rx = pillH / 2f

        val pillRect = RectF(pillX, pillTop, pillX + pillW, pillBottom)

        glassPaint.color = if (darkMode) Color.parseColor("#25FFFFFF") else Color.parseColor("#15000000")
        canvas.drawRoundRect(pillRect, rx, rx, glassPaint)

        rimPaint.color = if (darkMode) Color.parseColor("#20FFFFFF") else Color.parseColor("#12000000")
        canvas.drawRoundRect(pillRect, rx, rx, rimPaint)

        val specTop = pillTop + 0.8f * density
        val specRect = RectF(
            pillX + rx * 0.5f,
            specTop,
            pillX + pillW - rx * 0.5f,
            specTop
        )
        
        specularPaint.shader = LinearGradient(
            specRect.left, specTop, specRect.right, specTop,
            intArrayOf(
                Color.parseColor("#00FFFFFF"),
                Color.parseColor("#40FFFFFF"),
                Color.parseColor("#00FFFFFF")
            ),
            floatArrayOf(0.15f, 0.5f, 0.85f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(specRect.left, specTop, specRect.right, specTop, specularPaint)

        val selectedTextColor = if (darkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#111827")
        val unselectedTextColor = if (darkMode) Color.parseColor("#6B7280") else Color.parseColor("#9CA3AF")

        for (i in 0 until tabCount) {
            val centerX = cellWidth * i + cellWidth / 2f
            val centerY = h / 2f
            val isSelected = (i == selectedIndex)

            textPaint.color = if (isSelected) selectedTextColor else unselectedTextColor
            textPaint.typeface = if (isSelected) {
                Typeface.create("sans-serif-medium", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val fm = textPaint.fontMetrics
            val baseline = centerY - (fm.descent + fm.ascent) / 2f
            canvas.drawText(tabTitles[i], centerX, baseline, textPaint)
        }
    }
}
