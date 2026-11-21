package com.fix.engine.abdullah.ui.widget

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.databinding.ViewFloatingNavBarBinding
import com.google.android.material.color.MaterialColors

class FloatingNavBarController(
    private val binding: ViewFloatingNavBarBinding,
    private val viewPager: ViewPager2,
    private val onTabReselected: (Int) -> Unit
) {

    private data class NavTabItem(
        val container: LinearLayout,
        val iconView: ImageView,
        val labelView: TextView,
        val filledIconRes: Int,
        val outlinedIconRes: Int
    )

    private val tabs: List<NavTabItem> = listOf(
        NavTabItem(
            container = binding.tabApps,
            iconView = binding.imgNavApps,
            labelView = binding.txtNavApps,
            filledIconRes = R.drawable.ic_nav_apps_filled,
            outlinedIconRes = R.drawable.ic_nav_apps_outlined
        ),
        NavTabItem(
            container = binding.tabUpdates,
            iconView = binding.imgNavUpdates,
            labelView = binding.txtNavUpdates,
            filledIconRes = R.drawable.ic_nav_updates_filled,
            outlinedIconRes = R.drawable.ic_nav_updates_outlined
        ),
        NavTabItem(
            container = binding.tabInstalled,
            iconView = binding.imgNavInstalled,
            labelView = binding.txtNavInstalled,
            filledIconRes = R.drawable.ic_nav_installed_filled,
            outlinedIconRes = R.drawable.ic_nav_installed_outlined
        )
    )

    private val context = binding.root.context
    private val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
    private val colorOnSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val colorSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
    private val colorSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant)
    private val colorOutlineVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
    private val colorError = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)

    private var currentPosition = viewPager.currentItem.coerceIn(0, tabs.lastIndex)
    private var isIndicatorReady = false

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            if (!isIndicatorReady) return
            val nextPosition = (position + 1).coerceAtMost(tabs.lastIndex)
            val startX = tabs[position].container.x
            val endX = tabs[nextPosition].container.x
            binding.navPillIndicator.x = startX + (endX - startX) * positionOffset
        }

        override fun onPageSelected(position: Int) {
            currentPosition = position.coerceIn(0, tabs.lastIndex)
            updateTabVisuals(currentPosition, animate = true)
            if (isIndicatorReady) {
                binding.navPillIndicator.animate()
                    .x(tabs[currentPosition].container.x)
                    .setDuration(220)
                    .start()
            }
        }
    }

    init {
        applyAdaptiveSurfaceStyling()
        setupClicks()
        setupIndicatorLayout()
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        updateTabVisuals(currentPosition, animate = false)
    }

    private fun applyAdaptiveSurfaceStyling() {
        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            // الوضع الليلي: كبسولة خارجية داكنة أنيقة ومؤشر داخلي مرتفع بتوهج خفيف للون الأساسي
            binding.floatingPillCard.setCardBackgroundColor(ColorUtils.blendARGB(colorSurface, Color.BLACK, 0.12f))
            binding.floatingPillCard.strokeColor = ColorUtils.setAlphaComponent(colorOutlineVariant, 110)

            val darkIndicatorBg = ColorUtils.blendARGB(colorSurfaceVariant, colorPrimary, 0.14f)
            binding.navPillIndicator.setCardBackgroundColor(darkIndicatorBg)
            binding.navPillIndicator.strokeColor = ColorUtils.setAlphaComponent(colorPrimary, 95)
        } else {
            // الوضع النهاري (مطابق لفخامة الصورة الأولى): كبسولة خارجية لؤلؤية ناعمة ومؤشر داخلي أبيض مرتفع
            val pearlOuterBg = ColorUtils.blendARGB(colorSurface, colorSurfaceVariant, 0.45f)
            binding.floatingPillCard.setCardBackgroundColor(pearlOuterBg)
            binding.floatingPillCard.strokeColor = ColorUtils.setAlphaComponent(colorOutlineVariant, 120)

            binding.navPillIndicator.setCardBackgroundColor(Color.WHITE)
            binding.navPillIndicator.strokeColor = ColorUtils.setAlphaComponent(colorPrimary, 35)
        }

        // ألوان شارات الأعداد
        binding.badgeNavUpdates.backgroundTintList = ColorStateList.valueOf(colorError)
        binding.badgeNavInstalled.backgroundTintList = ColorStateList.valueOf(colorPrimary)
    }

    private fun setupClicks() {
        tabs.forEachIndexed { index, item ->
            item.container.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (currentPosition == index) {
                    onTabReselected(index)
                } else {
                    viewPager.setCurrentItem(index, true)
                }
            }
        }
    }

    private fun setupIndicatorLayout() {
        binding.floatingPillInnerFrame.doOnLayout {
            syncIndicatorSizeAndPosition()
        }
        binding.floatingPillInnerFrame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if ((right - left) != (oldRight - oldLeft)) {
                syncIndicatorSizeAndPosition()
            }
        }
    }

    private fun syncIndicatorSizeAndPosition() {
        val targetTab = tabs[currentPosition].container
        val tabWidth = targetTab.width
        if (tabWidth <= 0) return

        val params = binding.navPillIndicator.layoutParams
        if (params.width != tabWidth) {
            params.width = tabWidth
            binding.navPillIndicator.layoutParams = params
        }

        binding.navPillIndicator.x = targetTab.x
        isIndicatorReady = true
    }

    private fun updateTabVisuals(selectedIndex: Int, animate: Boolean) {
        tabs.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex

            item.iconView.setImageResource(if (isSelected) item.filledIconRes else item.outlinedIconRes)
            item.iconView.imageTintList = ColorStateList.valueOf(if (isSelected) colorPrimary else colorOnSurfaceVariant)

            item.labelView.setTextColor(if (isSelected) colorPrimary else colorOnSurfaceVariant)
            item.labelView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

            if (animate && isSelected) {
                item.iconView.animate().cancel()
                item.iconView.scaleX = 0.84f
                item.iconView.scaleY = 0.84f
                item.iconView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator(2.2f))
                    .start()
            } else {
                item.iconView.scaleX = 1f
                item.iconView.scaleY = 1f
            }
        }
    }

    fun setUpdatesBadge(count: Int) {
        updateBadgeView(binding.badgeNavUpdates, count)
    }

    fun setInstalledBadge(count: Int) {
        updateBadgeView(binding.badgeNavInstalled, count)
    }

    private fun updateBadgeView(badgeView: TextView, count: Int) {
        if (count > 0) {
            val wasHidden = !badgeView.isVisible
            badgeView.text = if (count > 99) "99+" else count.toString()
            badgeView.isVisible = true
            if (wasHidden) {
                badgeView.scaleX = 0.4f
                badgeView.scaleY = 0.4f
                badgeView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
        } else {
            badgeView.isVisible = false
        }
    }

    fun detach() {
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }
}
