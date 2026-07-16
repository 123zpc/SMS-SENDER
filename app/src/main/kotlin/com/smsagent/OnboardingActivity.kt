package com.smsagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.smsagent.state.LaunchStateStore

class OnboardingActivity : Activity() {

    private lateinit var skipButton: TextView
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var artwork: View
    private lateinit var artworkIcon: ImageView
    private lateinit var artworkBadge: TextView
    private lateinit var copyGroup: View
    private lateinit var stepText: TextView
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var featureIcon: ImageView
    private lateinit var featureText: TextView
    private lateinit var indicators: List<View>

    private var currentPageIndex = 0

    private val pages = listOf(
        OnboardingPage(
            eyebrowRes = R.string.onboarding_page_one_eyebrow,
            titleRes = R.string.onboarding_page_one_title,
            descriptionRes = R.string.onboarding_page_one_description,
            featureRes = R.string.onboarding_page_one_feature,
            badgeRes = R.string.onboarding_page_one_badge,
            iconRes = R.drawable.ic_send_24,
            artworkBackgroundRes = R.drawable.onboarding_hero_primary,
            iconTintRes = R.color.seed,
        ),
        OnboardingPage(
            eyebrowRes = R.string.onboarding_page_two_eyebrow,
            titleRes = R.string.onboarding_page_two_title,
            descriptionRes = R.string.onboarding_page_two_description,
            featureRes = R.string.onboarding_page_two_feature,
            badgeRes = R.string.onboarding_page_two_badge,
            iconRes = R.drawable.ic_notifications_24,
            artworkBackgroundRes = R.drawable.onboarding_hero_guard,
            iconTintRes = R.color.brand_emerald_dark,
        ),
        OnboardingPage(
            eyebrowRes = R.string.onboarding_page_three_eyebrow,
            titleRes = R.string.onboarding_page_three_title,
            descriptionRes = R.string.onboarding_page_three_description,
            featureRes = R.string.onboarding_page_three_feature,
            badgeRes = R.string.onboarding_page_three_badge,
            iconRes = R.drawable.ic_settings_24,
            artworkBackgroundRes = R.drawable.onboarding_hero_ready,
            iconTintRes = R.color.accent_orange,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LaunchStateStore.hasCompletedOnboarding(this)) {
            openMainActivity()
            return
        }

        setContentView(R.layout.activity_onboarding)
        bindViews()
        bindActions()
        renderPage(animate = false)
    }

    override fun onBackPressed() {
        if (!nextButton.isEnabled) {
            return
        }

        if (currentPageIndex == 0) {
            super.onBackPressed()
        } else {
            currentPageIndex -= 1
            renderPage(animate = true)
        }
    }

    private fun bindViews() {
        skipButton = findViewById(R.id.onboardingSkipButton)
        backButton = findViewById(R.id.onboardingBackButton)
        nextButton = findViewById(R.id.onboardingNextButton)
        artwork = findViewById(R.id.onboardingArtwork)
        artworkIcon = findViewById(R.id.onboardingArtworkIcon)
        artworkBadge = findViewById(R.id.onboardingArtworkBadge)
        copyGroup = findViewById(R.id.onboardingCopyGroup)
        stepText = findViewById(R.id.onboardingStepText)
        eyebrow = findViewById(R.id.onboardingEyebrow)
        title = findViewById(R.id.onboardingTitle)
        description = findViewById(R.id.onboardingDescription)
        featureIcon = findViewById(R.id.onboardingFeatureIcon)
        featureText = findViewById(R.id.onboardingFeatureText)
        indicators = listOf(
            findViewById(R.id.onboardingIndicatorOne),
            findViewById(R.id.onboardingIndicatorTwo),
            findViewById(R.id.onboardingIndicatorThree),
        )
    }

    private fun bindActions() {
        skipButton.setOnClickListener { completeOnboarding() }
        backButton.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex -= 1
                renderPage(animate = true)
            }
        }
        nextButton.setOnClickListener {
            if (currentPageIndex == pages.lastIndex) {
                completeOnboarding()
            } else {
                currentPageIndex += 1
                renderPage(animate = true)
            }
        }
    }

    private fun renderPage(animate: Boolean) {
        if (!animate) {
            bindPageContent()
            setControlsEnabled(true)
            animateInitialContent()
            return
        }

        setControlsEnabled(false)
        copyGroup.animate().cancel()
        artwork.animate().cancel()

        copyGroup.animate()
            .alpha(0f)
            .translationX(-18f)
            .setDuration(140L)
            .withEndAction {
                bindPageContent()
                copyGroup.translationX = 18f
                copyGroup.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(240L)
                    .withEndAction { setControlsEnabled(true) }
                    .start()
            }
            .start()

        artwork.animate()
            .alpha(0.45f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(120L)
            .withEndAction {
                artwork.translationY = 12f
                artwork.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(280L)
                    .start()
            }
            .start()
    }

    private fun bindPageContent() {
        val page = pages[currentPageIndex]
        stepText.text = getString(R.string.onboarding_step, currentPageIndex + 1)
        eyebrow.setText(page.eyebrowRes)
        title.setText(page.titleRes)
        description.setText(page.descriptionRes)
        featureText.setText(page.featureRes)
        artworkBadge.setText(page.badgeRes)
        artwork.setBackgroundResource(page.artworkBackgroundRes)
        artworkIcon.setImageResource(page.iconRes)
        artworkIcon.setColorFilter(getColor(page.iconTintRes))
        featureIcon.setImageResource(page.iconRes)
        featureIcon.setColorFilter(getColor(page.iconTintRes))

        indicators.forEachIndexed { index, indicator ->
            indicator.setBackgroundResource(
                if (index == currentPageIndex) {
                    R.drawable.onboarding_indicator_active
                } else {
                    R.drawable.onboarding_indicator_inactive
                },
            )
            val layoutParams = indicator.layoutParams
            layoutParams.width = dpToPixels(if (index == currentPageIndex) 24 else 8)
            indicator.layoutParams = layoutParams
        }

        backButton.visibility = if (currentPageIndex == 0) View.INVISIBLE else View.VISIBLE
        skipButton.visibility = if (currentPageIndex == pages.lastIndex) View.INVISIBLE else View.VISIBLE
        nextButton.setText(
            if (currentPageIndex == pages.lastIndex) {
                R.string.onboarding_finish
            } else {
                R.string.onboarding_next
            },
        )
    }

    private fun animateInitialContent() {
        artwork.alpha = 0f
        artwork.scaleX = 0.94f
        artwork.scaleY = 0.94f
        copyGroup.alpha = 0f
        copyGroup.translationY = 18f
        artwork.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .start()
        copyGroup.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(100L)
            .setDuration(320L)
            .start()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        skipButton.isEnabled = enabled
        backButton.isEnabled = enabled && currentPageIndex > 0
        nextButton.isEnabled = enabled
    }

    private fun completeOnboarding() {
        LaunchStateStore.markOnboardingCompleted(this)
        openMainActivity()
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun dpToPixels(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class OnboardingPage(
        val eyebrowRes: Int,
        val titleRes: Int,
        val descriptionRes: Int,
        val featureRes: Int,
        val badgeRes: Int,
        val iconRes: Int,
        val artworkBackgroundRes: Int,
        val iconTintRes: Int,
    )
}
