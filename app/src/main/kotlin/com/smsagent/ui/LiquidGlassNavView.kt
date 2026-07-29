package com.smsagent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * 苹果 iOS 26.6 极致空间液态水滴 glass 导航栏 (Spatial Liquid Glass Dock)
 * 特效要点：
 * 1. 彻底移除底部的绿色指示点（亮灯）
 * 2. 真实水滴流动拉伸 (Liquid Surface Tension & Velocity Elastic Deformation)
 * 3. 顶部高亮白光玻璃弧边折射 (3D Specular Refraction Highlight)
 * 4. 动态高斯感液态发光与高品质字标缩放
 */
class LiquidGlassNavView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var onTabSelectedListener: ((index: Int) -> Unit)? = null
    var selectedIndex = 0
        private set

    private val density = resources.displayMetrics.density

    // 水滴本体 gradient paint
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 玻璃高光内描边 (Glass Specular Rim Light)
    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    // 顶部弧形白光高光折射 (Top Arc Specular Highlight)
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    // 文本 Paint
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

    // 液态变形参数
    private var currentStretchFactor = 1.0f
    private var lastTouchX = 0f
    private var touchVelocity = 0f

    private var positionAnimator: ValueAnimator? = null
    private var stretchAnimator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            basePillWidth = (w.toFloat() / tabCount) - (8f * density)
            pillX = getTabTargetX(selectedIndex)
            updateShaders()
        }
    }

    private fun updateShaders() {
        if (width > 0 && height > 0) {
            // 液态折射微光渐变 (Electric Indigo to Violet)
            liquidPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.parseColor("#4F46E5"),
                    Color.parseColor("#6366F1"),
                    Color.parseColor("#4338CA")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            // 玻璃外框高光边 Line
            glassRimPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.parseColor("#A0FFFFFF"),
                    Color.parseColor("#30FFFFFF"),
                    Color.parseColor("#10FFFFFF")
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
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
                touchVelocity = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = touchX - lastTouchX
                    touchVelocity = deltaX
                    lastTouchX = touchX

                    // 计算液态拉伸比例 (根据移动速度产生水滴拉伸与高度压缩)
                    val speed = abs(deltaX)
                    val targetStretch = (1.0f + (speed / (12f * density)).coerceAtMost(0.35f))
                    currentStretchFactor += (targetStretch - currentStretchFactor) * 0.4f

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

        // 1. 位置 Spring Overshoot 弹簧动画
        positionAnimator = ValueAnimator.ofFloat(pillX, targetX).apply {
            duration = 340L
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                pillX = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        // 2. 水滴拉伸恢复回弹动画
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

        // 根据拉伸系数形变 (质量守恒：宽度变大，高度收缩)
        val pillW = basePillWidth * currentStretchFactor
        val pillH = baseH / sqrt(currentStretchFactor)
        val pillTop = (h - pillH) / 2f
        val pillBottom = pillTop + pillH
        val rx = pillH / 2f // 完美圆角水滴

        // 1. 绘制液态折射水滴包络
        val pillRect = RectF(pillX, pillTop, pillX + pillW, pillBottom)
        canvas.drawRoundRect(pillRect, rx, rx, liquidPaint)

        // 2. 绘制 3D 玻璃边框高光
        canvas.drawRoundRect(pillRect, rx, rx, glassRimPaint)

        // 3. 绘制顶部 1/3 弧形白光折射 (Top Specular Highlight Light Path)
        val topHighlightPath = Path()
        val highlightOffset = 2f * density
        val specRect = RectF(
            pillX + highlightOffset,
            pillTop + highlightOffset,
            pillX + pillW - highlightOffset,
            pillTop + pillH * 0.5f
        )
        specularPaint.shader = LinearGradient(
            pillX, pillTop, pillX + pillW, pillTop,
            intArrayOf(
                Color.parseColor("#00FFFFFF"),
                Color.parseColor("#C0FFFFFF"),
                Color.parseColor("#00FFFFFF")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        topHighlightPath.addRoundRect(specRect, rx * 0.8f, rx * 0.8f, Path.Direction.CW)
        canvas.drawPath(topHighlightPath, specularPaint)

        // 4. 绘制 Tab 标题 (移除任何底部亮灯点，纯净高保真苹果 Typography)
        for (i in 0 until tabCount) {
            val centerX = cellWidth * i + cellWidth / 2f
            val centerY = h / 2f
            val isSelected = (i == selectedIndex)

            textPaint.color = if (isSelected) {
                Color.parseColor("#FFFFFF")
            } else {
                Color.parseColor("#94A3B8")
            }

            textPaint.typeface = if (isSelected) {
                Typeface.create("sans-serif-medium", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val fontMetrics = textPaint.fontMetrics
            val baseline = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f
            canvas.drawText(tabTitles[i], centerX, baseline, textPaint)
        }
    }
}
