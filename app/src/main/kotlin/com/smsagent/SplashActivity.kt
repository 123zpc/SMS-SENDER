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

        val flightScene = findViewById<View>(R.id.splashFlightScene)
        val plane = findViewById<View>(R.id.splashPlane)
        val signalOrigin = findViewById<View>(R.id.splashSignalOrigin)
        val messageCard = findViewById<View>(R.id.splashMessageCard)
        val wordmark = findViewById<View>(R.id.splashWordmark)
        val tagline = findViewById<View>(R.id.splashTagline)
        val progress = findViewById<View>(R.id.splashProgress)

        flightScene.alpha = 0f
        plane.alpha = 0f
        plane.scaleX = 0.84f
        plane.scaleY = 0.84f
        plane.translationX = -68f
        plane.translationY = 20f
        plane.rotation = -10f
        signalOrigin.alpha = 0f
        signalOrigin.scaleX = 0.55f
        signalOrigin.scaleY = 0.55f
        messageCard.alpha = 0f
        messageCard.scaleX = 0.86f
        messageCard.scaleY = 0.86f
        messageCard.translationX = 12f
        wordmark.alpha = 0f
        wordmark.translationY = 12f
        tagline.alpha = 0f
        tagline.translationY = 10f
        progress.alpha = 0f
        progress.scaleX = 0.35f
        progress.scaleY = 0.35f
        progress.rotation = -90f

        flightScene.animate()
            .alpha(1f)
            .setDuration(SCENE_FADE_DURATION)
            .start()
        signalOrigin.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SIGNAL_ANIMATION_DURATION)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
        plane.post {
            val arrivalX = messageCard.x - plane.width * 0.42f
            plane.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(arrivalX)
                .translationY(-4f)
                .rotation(0f)
                .setStartDelay(FLIGHT_START_DELAY)
                .setDuration(FLIGHT_DURATION)
                .setInterpolator(OvershootInterpolator(0.72f))
                .withEndAction {
                    messageCard.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .setDuration(MESSAGE_ARRIVAL_DURATION)
                        .setInterpolator(OvershootInterpolator(0.9f))
                        .start()
                    signalOrigin.animate()
                        .alpha(0.5f)
                        .scaleX(0.78f)
                        .scaleY(0.78f)
                        .setDuration(MESSAGE_ARRIVAL_DURATION)
                        .start()
                }
                .start()
        }
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
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setStartDelay(100L)
            .setDuration(PROGRESS_ANIMATION_DURATION)
            .setInterpolator(OvershootInterpolator(0.7f))
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
        private const val SCENE_FADE_DURATION = 180L
        private const val SIGNAL_ANIMATION_DURATION = 260L
        private const val FLIGHT_START_DELAY = 40L
        private const val FLIGHT_DURATION = 460L
        private const val MESSAGE_ARRIVAL_DURATION = 200L
        private const val COPY_ANIMATION_DURATION = 360L
        private const val PROGRESS_ANIMATION_DURATION = 760L
    }
}
