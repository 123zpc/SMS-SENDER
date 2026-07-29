package com.smsagent.ui

import android.animation.ValueAnimator
import android.content.Context
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

/**
 * Apple iOS 26 Liquid Glass Tab Bar — 1:1 还原
 *
 * 核心视觉规则（来自苹果 WWDC25 Liquid Glass HIG）：
 * 1. 水滴本体 = 半透明磨砂玻璃 (frosted glass)，NOT 实色
 * 2. 顶部极薄微弧光 = 0.8dp 半透明白色，仅贴顶部内边缘
 * 3. 选中文字 = 纯白 #FFFFFF，未选中 = 中性灰
 * 4. 拖拽时水滴随速度拉伸变形 + 质量守恒高度压缩
 * 5. 释放时 Spring Overshoot 弹簧吸附最近 Tab
 * 6. 无任何底部亮灯 / 彩色圆点
 */
class LiquidGlassNavView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var onTabSelectedListener: ((index: Int) -> Unit)? = null
    var selectedIndex = 0
        private set

    private val density = resources.displayMetrics.density

    // 磨砂玻璃水滴本体 (Frosted Glass Fill)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38FFFFFF")   // 22% 不透明白 = 磨砂感
    }

    // 玻璃边框极细高光 (Glass Rim)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
        color = Color.parseColor("#30FFFFFF")   // 19% 白 极淡边框
    }

    // 顶部弧形微光 (Top Edge Specular — 仅绘制顶部 1/5 高度的薄弧)
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f * density
        strokeCap = Paint.Cap.ROUND
    }

    // 文本
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

    // 液态变形
    private var currentStretchFactor = 1.0f
    private var lastTouchX = 0f

    private var positionAnimator: ValueAnimator? = null
    private var stretchAnimator: ValueAnimator? = null

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

        val cellWidth = w / tabCount
        val baseTop = 6f * density
        val baseBottom = h - 6f * density
        val baseH = baseBottom - baseTop

        // 水滴质量守恒形变
        val pillW = basePillWidth * currentStretchFactor
        val pillH = baseH / sqrt(currentStretchFactor)
        val pillTop = (h - pillH) / 2f
        val pillBottom = pillTop + pillH
        val rx = pillH / 2f

        val pillRect = RectF(pillX, pillTop, pillX + pillW, pillBottom)

        // 1. 磨砂玻璃水滴
        canvas.drawRoundRect(pillRect, rx, rx, glassPaint)

        // 2. 极细玻璃边框
        canvas.drawRoundRect(pillRect, rx, rx, rimPaint)

        // 3. 顶部薄弧微光 (仅顶部边缘内侧 — 不穿越中心)
        val inset = 1.2f * density
        val specTop = pillTop + inset
        val specH = pillH * 0.15f  // 仅顶部 15% 高度
        val specRect = RectF(
            pillX + rx * 0.5f,
            specTop,
            pillX + pillW - rx * 0.5f,
            specTop + specH
        )
        specularPaint.shader = LinearGradient(
            specRect.left, specTop, specRect.right, specTop,
            intArrayOf(
                Color.parseColor("#00FFFFFF"),
                Color.parseColor("#50FFFFFF"),
                Color.parseColor("#00FFFFFF")
            ),
            floatArrayOf(0.15f, 0.5f, 0.85f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(specRect.left, specTop, specRect.right, specTop, specularPaint)

        // 4. Tab 文字 (选中 = 纯白, 未选中 = 中灰)
        for (i in 0 until tabCount) {
            val centerX = cellWidth * i + cellWidth / 2f
            val centerY = h / 2f
            val isSelected = (i == selectedIndex)

            textPaint.color = if (isSelected) Color.WHITE else Color.parseColor("#94A3B8")
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
