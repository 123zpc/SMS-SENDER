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

        logo.alpha = 0f
        wordmark.alpha = 0f
        tagline.alpha = 0f

        // 简短干净的 Apple 极简淡入
        logo.animate().alpha(1f).setDuration(240L).start()
        wordmark.animate().alpha(1f).setStartDelay(80L).setDuration(240L).start()
        tagline.animate().alpha(1f).setStartDelay(120L).setDuration(240L).start()

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
        private const val SPLASH_DURATION = 800L
    }
}
