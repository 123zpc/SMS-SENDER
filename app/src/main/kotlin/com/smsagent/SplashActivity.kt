package com.smsagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
        val progress = findViewById<View>(R.id.splashProgress)

        logo.alpha = 0f
        logo.scaleX = 0.84f
        logo.scaleY = 0.84f
        wordmark.alpha = 0f
        wordmark.translationY = 12f
        tagline.alpha = 0f
        tagline.translationY = 10f
        progress.pivotX = 0f
        progress.scaleX = 0f

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(LOGO_ANIMATION_DURATION)
            .start()
        wordmark.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(120L)
            .setDuration(COPY_ANIMATION_DURATION)
            .start()
        tagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(180L)
            .setDuration(COPY_ANIMATION_DURATION)
            .start()
        progress.animate()
            .scaleX(1f)
            .setStartDelay(100L)
            .setDuration(PROGRESS_ANIMATION_DURATION)
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
        private const val SPLASH_DURATION = 950L
        private const val LOGO_ANIMATION_DURATION = 420L
        private const val COPY_ANIMATION_DURATION = 360L
        private const val PROGRESS_ANIMATION_DURATION = 760L
    }
}
