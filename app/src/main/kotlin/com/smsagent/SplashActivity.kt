package com.smsagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.smsagent.state.LaunchStateStore

class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable { navigateForward() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<View>(R.id.splashLogo)
        val wordmark = findViewById<View>(R.id.splashWordmark)
        val tagline = findViewById<View>(R.id.splashTagline)
        val ledRow = findViewById<View>(R.id.splashLedRow)
        val leds = listOf(
            findViewById<View>(R.id.splashLedOne),
            findViewById<View>(R.id.splashLedTwo),
            findViewById<View>(R.id.splashLedThree),
            findViewById<View>(R.id.splashLedFour),
            findViewById<View>(R.id.splashLedFive),
        )
        val bootLabel = findViewById<View>(R.id.splashBootLabel)
        val cursor = findViewById<View>(R.id.splashCursor)

        // 初始：全部隐身
        logo.alpha = 0f
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        wordmark.alpha = 0f
        wordmark.translationY = 10f
        tagline.alpha = 0f
        tagline.translationY = 8f
        leds.forEach { it.alpha = 0f; it.scaleX = 0.4f; it.scaleY = 0.4f }
        ledRow.alpha = 0f
        bootLabel.alpha = 0f
        bootLabel.translationX = -8f
        cursor.alpha = 0f

        val interp = DecelerateInterpolator(1.6f)

        // 1. 品牌标淡入并轻微回弹
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(LOGO_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 2. 字标淡入上浮
        wordmark.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(WORDMARK_DELAY)
            .setDuration(COPY_DURATION)
            .setInterpolator(interp)
            .start()

        // 3. 副标淡入上浮
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(TAGLINE_DELAY)
            .setDuration(COPY_DURATION)
            .setInterpolator(interp)
            .start()

        // 4. LED 点阵依次点亮（苹果式节奏：先缓慢淡入容器，再逐颗点亮）
        ledRow.alpha = 1f
        ledRow.animate()
            .alpha(1f)
            .setStartDelay(LED_ROW_DELAY)
            .setDuration(120L)
            .start()

        leds.forEachIndexed { index, led ->
            led.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(LED_ROW_DELAY + index * LED_STAGGER)
                .setDuration(LED_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // 5. 左下 BOOT 行滑入
        bootLabel.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(BOOT_DELAY)
            .setDuration(COPY_DURATION)
            .setInterpolator(interp)
            .start()

        // 6. 光标淡入，然后开始闪烁
        cursor.animate()
            .alpha(1f)
            .setStartDelay(CURSOR_DELAY)
            .setDuration(200L)
            .withEndAction { startCursorBlink(cursor) }
            .start()

        handler.postDelayed(navigateRunnable, SPLASH_DURATION)
    }

    private fun startCursorBlink(cursor: View) {
        cursor.animate()
            .alpha(0f)
            .setDuration(CURSOR_BLINK_DURATION)
            .withEndAction {
                cursor.animate()
                    .alpha(1f)
                    .setDuration(CURSOR_BLINK_DURATION)
                    .withEndAction { startCursorBlink(cursor) }
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    private fun navigateForward() {
        if (isFinishing || isDestroyed) {
            return
        }

        val destination = if (LaunchStateStore.hasCompletedOnboarding(this)) {
            MainActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(Intent(this, destination))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private companion object {
        private const val SPLASH_DURATION = 1100L
        private const val LOGO_DURATION = 420L
        private const val WORDMARK_DELAY = 180L
        private const val TAGLINE_DELAY = 260L
        private const val COPY_DURATION = 360L
        private const val LED_ROW_DELAY = 340L
        private const val LED_STAGGER = 90L
        private const val LED_DURATION = 240L
        private const val BOOT_DELAY = 520L
        private const val CURSOR_DELAY = 640L
        private const val CURSOR_BLINK_DURATION = 420L
    }
}
