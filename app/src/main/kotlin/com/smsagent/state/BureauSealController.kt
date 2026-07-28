package com.smsagent.state

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import com.smsagent.R
import java.util.Random

/**
 * 信号局印 · 彩蛋控制器
 *
 * 召出方式：
 * 1. 长按"关于"卡片中的版本号 5 次
 * 2. 在电报台输入 SEAL 指令
 *
 * 盖印时随机展示一条格言，并将封缄记录写入本地电报流。
 */
object BureauSealController {

    private val mottos = listOf(
        R.string.easter_seal_motto_1,
        R.string.easter_seal_motto_2,
        R.string.easter_seal_motto_3,
        R.string.easter_seal_motto_4,
    )

    private val random = Random()
    private var overlay: View? = null
    private var versionTapCount = 0
    private var lastTapTime = 0L

    private const val REQUIRED_TAPS = 5
    private const val TAP_RESET_INTERVAL_MS = 1800L

    /**
     * 记录一次版本号长按；连续 5 次即召出局印。
     * 应在版本号的 OnLongClickListener 中调用。
     */
    fun recordVersionLongPress(activity: Activity) {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > TAP_RESET_INTERVAL_MS) {
            versionTapCount = 0
        }
        lastTapTime = now
        versionTapCount++

        if (versionTapCount >= REQUIRED_TAPS) {
            versionTapCount = 0
            showSeal(activity)
        } else {
            val remaining = REQUIRED_TAPS - versionTapCount
            Toast.makeText(
                activity,
                "再按 $remaining 次召出局印",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * 直接召出局印（供控制台 SEAL 指令调用）。
     */
    fun showSeal(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        // 若已有浮层在显示，先移除
        dismiss(activity)

        val rootView = activity.window.decorView as? ViewGroup ?: return
        val overlay = android.view.LayoutInflater.from(activity)
            .inflate(R.layout.bureau_seal_overlay, rootView, false)

        val mottoRes = mottos[random.nextInt(mottos.size)]
        val mottoText = overlay.findViewById<TextView>(R.id.sealMottoText)
        mottoText.setText(mottoRes)

        val container = overlay.findViewById<View>(R.id.sealContainer)
        val stampAnim = AnimationUtils.loadAnimation(activity, R.anim.seal_stamp)
        val fadeAnim = AnimationUtils.loadAnimation(activity, R.anim.seal_fade_in)

        // 标题与格言先淡入，方印以盖章动效落下
        overlay.setOnClickListener { dismiss(activity) }

        rootView.addView(overlay)
        overlay.visibility = View.VISIBLE
        this.overlay = overlay

        mottoText.startAnimation(fadeAnim)
        overlay.findViewById<View>(R.id.sealTitleText).startAnimation(fadeAnim)
        overlay.findViewById<View>(R.id.sealDismissHint).startAnimation(fadeAnim)
        container.startAnimation(stampAnim)

        // 写入电报流
        val motto = activity.getString(mottoRes)
        EventLogStore.append(activity, "SEAL", activity.getString(R.string.easter_seal_log, motto))

        Toast.makeText(
            activity,
            activity.getString(R.string.easter_seal_toast, motto),
            Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * 消散浮层。带淡出效果。
     */
    private fun dismiss(activity: Activity) {
        val current = overlay ?: return
        overlay = null

        current.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                val parent = current.parent as? ViewGroup
                parent?.removeView(current)
            }
            .start()
    }
}
