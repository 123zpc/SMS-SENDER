package com.smsagent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 点阵纹理 View：在深色面板上绘制 2dp 微点网格（18dp 间距）。
 * 替代无法在 XML 中平铺的 bitmap 方案。
 */
class DotGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1FFFFFFF
        style = Paint.Style.FILL
    }

    private val spacing = 18f * resources.displayMetrics.density
    private val radius = 1f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
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
