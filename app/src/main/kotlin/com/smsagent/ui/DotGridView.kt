package com.smsagent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * 灵动背景纹理 View：在 Header 深色面板上绘制柔和微光流彩与极细微粒，
 * 替换硬核工业点阵，提供高端大厂质感。
 */
class DotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0D818CF8 // 柔和微亮 Indigo 沉淀
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val spacing = 24f * resources.displayMetrics.density
    private val radius = 1.2f * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            glowPaint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0x1F4F46E5, 0x000F172A, 0x1F10B981),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 绘制柔光弥散背景
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // 绘制柔和星光微粒
        var y = spacing / 2f
        while (y < h) {
            var x = spacing / 2f
            while (x < w) {
                canvas.drawCircle(x, y, radius, dotPaint)
                x += spacing
            }
            y += spacing
        }
    }
}
