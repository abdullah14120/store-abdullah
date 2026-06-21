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
import com.fix.engine.abdullah.databinding.FragmentAllAppsBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Refactored: UI State Observation, Clean Architecture Navigation & Janky-Free Scrolling
 */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter
    
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
                // الكائن الأصلي app متاح بفضل تمريره من الـ AppItemUiState داخل الـ Adapter
                putExtra("APP_DATA", app)
            }
            
            val iconView = itemView.findViewById<View>(R.id.imgAppIcon)
            
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
            setItemViewCacheSize(20) 
        }
    }

    private fun setupObserver() {
        // 🚨 التعديل الجوهري: مراقبة حالة الواجهة المعالجة في الخلفية بدلاً من الموديل الخام
        viewModel.appsUiStateList.observe(viewLifecycleOwner) { uiStates ->
            appAdapter.submitList(uiStates)
            
            if (!uiStates.isNullOrEmpty()) {
                binding.tvNoResults.visibility = View.GONE
                
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

    override fun onResume() {
        super.onResume()
        // 🟢 التعديل الهندسي: بدلاً من notifyDataSetChanged، نطلب من الـ ViewModel 
        // إجراء مسح سريع لحالة الملفات في الخلفية وتحديث الواجهة بسلاسة
        if (!isFirstLoad) {
            viewModel.refreshAppStates()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
