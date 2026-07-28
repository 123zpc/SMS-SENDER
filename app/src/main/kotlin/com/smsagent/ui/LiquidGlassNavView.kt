package com.smsagent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * iOS 26.6 空间液态水滴滑动导航 View
 * 支持：
 * 1. 直接点击 Tab 切换
 * 2. 拖拽水滴 (Drag Liquid Drop) 跨 Tab 流畅平移与拉伸变幻
 * 3. 释放时带 Spring Physics 弹簧回弹并自动吸附到最近 Tab
 */
class LiquidGlassNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onTabSelectedListener: ((index: Int) -> Unit)? = null
    var selectedIndex = 0
        private set

    private val density = resources.displayMetrics.density
    private val cornerRadius = 24f * density

    // 水滴 Paint & 绘制
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0x40C7D2FE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        fontFamily = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tabTitles = listOf("转发", "记录", "状态", "日志")
    private val tabCount = 4

    private var pillX = 0f
    private var pillWidth = 0f
    private var isDragging = false
    private var lastTouchX = 0f

    private var currentAnimator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            pillWidth = (w.toFloat() / tabCount) - (8f * density)
            pillX = getTabTargetX(selectedIndex)
            updateGradientShader()
        }
    }

    private fun updateGradientShader() {
        if (width > 0 && height > 0) {
            pillPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0x334F46E5, 0x406366F1, 0x3310B981),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun getTabTargetX(index: Int): Float {
        val cellWidth = width.toFloat() / tabCount
        return (index * cellWidth) + (cellWidth - pillWidth) / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cellWidth = width.toFloat() / tabCount
        val touchX = event.x.coerceIn(0f, width.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchX = touchX
                currentAnimator?.cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // 水滴跟随手指位置平移
                    val targetX = touchX - pillWidth / 2f
                    pillX = targetX.coerceIn(4f * density, width - pillWidth - 4f * density)

                    // 实时计算当前悬停的 Tab 索引
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
            invalidate()
        }
    }

    private fun animateToTab(index: Int) {
        selectedIndex = index
        val targetX = getTabTargetX(index)
        currentAnimator?.cancel()

        currentAnimator = ValueAnimator.ofFloat(pillX, targetX).apply {
            duration = 320L
            interpolator = OvershootInterpolator(1.25f)
            addUpdateListener { anim ->
                pillX = anim.animatedValue as Float
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
        val topPadding = 6f * density
        val bottomPadding = 6f * density

        // 1. 绘制拖拽 / 活动中的液态玻璃水滴 Capsule
        val pillRect = RectF(
            pillX,
            topPadding,
            pillX + pillWidth,
            h - bottomPadding
        )
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillStrokePaint)

        // 2. 绘制 4 个 Tab 的文本与灵动 Accent 点
        for (i in 0 until tabCount) {
            val centerX = cellWidth * i + cellWidth / 2f
            val centerY = h / 2f
            val isSelected = (i == selectedIndex)

            // 文字颜色与 Alpha
            textPaint.color = if (isSelected) 0xFF4F46E5.toInt() else 0xFF64748B.toInt()
            textPaint.alpha = if (isSelected) 255 else 160

            // 绘制 Tab 标题
            val fontMetrics = textPaint.fontMetrics
            val baseline = centerY - (fontMetrics.descent + fontMetrics.ascent) / 2f
            canvas.drawText(tabTitles[i], centerX, baseline, textPaint)

            // 选中项绘制顶部 / 底部小灵动点
            if (isSelected) {
                dotPaint.color = 0xFF10B981.toInt()
                canvas.drawCircle(centerX, h - 8f * density, 2.5f * density, dotPaint)
            }
        }
    }
}
