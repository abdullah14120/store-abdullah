package com.fix.engine.abdullah.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fix.engine.abdullah.ui.main.AllAppsFragment
import com.fix.engine.abdullah.ui.main.UpdatesFragment

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: 3-Tab ViewPager2 Adapter (All Apps, Updates, Installed Apps)
 */
class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        private const val TABS_COUNT = 3
        private const val INDEX_ALL_APPS = 0
        private const val INDEX_UPDATES = 1
        private const val INDEX_INSTALLED = 2
    }

    override fun getItemCount(): Int = TABS_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            INDEX_ALL_APPS -> AllAppsFragment.newInstance(onlyInstalled = false)
            INDEX_UPDATES -> UpdatesFragment()
            INDEX_INSTALLED -> AllAppsFragment.newInstance(onlyInstalled = true)
            else -> throw IllegalArgumentException("Invalid ViewPager position: $position. Expected 0, 1, or 2.")
        }
    }
}
