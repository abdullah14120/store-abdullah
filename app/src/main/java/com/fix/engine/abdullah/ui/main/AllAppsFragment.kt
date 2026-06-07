package com.fix.engine.abdullah.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.FragmentAllAppsBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Global UI Edition (Material 3 Dynamic Style)
 * Feature: Shared Element Transitions & Flicker-Free Realtime Search Distribution
 */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter
    
    // متغير للتحكم بالحركة وتطبيقها لمرة واحدة فقط عند فتح المتجر أول مرة منعاً للوميض
    private var isFirstLoad = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObserver()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app, itemView ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            
            val iconView = itemView.findViewById<View>(R.id.imgAppIcon)
            
            // 🟢 التعديل الحرج: استخدام الاسم الديناميكي المتطابق مع الـ Adapter لمنع تعارض الحركات
            val uniqueTransitionName = "transition_app_icon_${app.packageName}"
            ViewCompat.setTransitionName(iconView, uniqueTransitionName)
            
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                iconView,
                uniqueTransitionName
            )

            startActivity(intent, options.toBundle())
        }
        
        binding.rvAllApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20) // كاش مسبق لـ 20 عنصر لضمان التمرير الصاروخي للأيقونات الزيتية الدائرية
        }
    }

    private fun setupObserver() {
        // مراقبة مسار appsList المخصص لعرض كافة تطبيقات المستودع
        viewModel.appsList.observe(viewLifecycleOwner) { apps ->
            appAdapter.submitList(apps)
            
            if (!apps.isNullOrEmpty()) {
                binding.tvNoResults.visibility = View.GONE
                
                // 🛠️ حل مشكلة الـ Flicker: الأنيميشن يظهر فقط عند أول إقلاع للمتجر لحماية تجربة البحث المباشر
                if (isFirstLoad) {
                    binding.rvAllApps.alpha = 0f
                    binding.rvAllApps.animate().alpha(1f).setDuration(350).start()
                    isFirstLoad = false
                } else {
                    binding.rvAllApps.alpha = 1f
                }
            } else {
                binding.tvNoResults.visibility = View.VISIBLE
            }
        }
    }

    // 🟢 إضافة ذكية: تحديث مرئي سريع للقائمة عند عودة المستخدم من شاشة التفاصيل
    // لضمان تحول الزر من "تنزيل" إلى "تثبيت" أو "فتح" إذا قام بالتحميل هناك
    override fun onResume() {
        super.onResume()
        if (::appAdapter.isInitialized && !isFirstLoad) {
            appAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
