package com.fix.engine.abdullah.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fix.engine.abdullah.ui.main.AllAppsFragment
import com.fix.engine.abdullah.ui.main.UpdatesFragment

class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 2 // عدد التبويبات

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AllAppsFragment() // قائمة كل التطبيقات (يمين)
            else -> UpdatesFragment() // قائمة التحديثات (يسار)
        }
    }
}
