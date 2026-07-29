package com.smsagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
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
        val badgeCapsule = findViewById<View>(R.id.splashBadgeCapsule)
        val progressBar = findViewById<View>(R.id.splashProgressBar)

        // 初始：缩小并隐藏
        logo.alpha = 0f
        logo.scaleX = 0.5f
        logo.scaleY = 0.5f

        wordmark.alpha = 0f
        wordmark.translationY = 16f

        tagline.alpha = 0f
        tagline.translationY = 12f

        badgeCapsule.alpha = 0f
        badgeCapsule.translationY = 8f

        progressBar.alpha = 0f

        val springInterp = OvershootInterpolator(1.15f)

        // 1. 图标带 iOS 弹簧缩放与高光淡入
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(480L)
            .setInterpolator(springInterp)
            .start()

        // 2. 字标淡入上浮
        wordmark.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(160L)
            .setDuration(360L)
            .start()

        // 3. 副标题淡入
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(240L)
            .setDuration(360L)
            .start()

        // 4. 磨砂胶囊淡入
        badgeCapsule.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(320L)
            .setDuration(360L)
            .start()

        // 5. Loading bar 淡入
        progressBar.animate()
            .alpha(1f)
            .setStartDelay(400L)
            .setDuration(300L)
            .start()

        handler.postDelayed(navigateRunnable, SPLASH_DURATION)
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
        private const val SPLASH_DURATION = 1200L
    }
}
