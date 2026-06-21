package com.fix.engine.abdullah.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fix.engine.abdullah.ui.main.AllAppsFragment
import com.fix.engine.abdullah.ui.main.UpdatesFragment

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Strict Index Mapping & Exception Handling for ViewPager2
 */
class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // Core Logic: تعريف ثوابت التوجيه الهيكلية
    companion object {
        private const val TABS_COUNT = 2
        private const val INDEX_ALL_APPS = 0
        private const val INDEX_UPDATES = 1
    }

    override fun getItemCount(): Int = TABS_COUNT

    // UI Instantiation: بناء الواجهات بناءً على التوجيه المعماري
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            INDEX_ALL_APPS -> AllAppsFragment()
            INDEX_UPDATES -> UpdatesFragment()
            else -> throw IllegalArgumentException("Invalid ViewPager position: $position. Expected 0 or 1.")
        }
    }
}
