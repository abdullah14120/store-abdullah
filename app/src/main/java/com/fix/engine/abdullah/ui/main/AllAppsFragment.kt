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
 * Project: FIX ENGINE - Global UI Edition
 * Feature: Shared Element Transitions for a premium feel
 */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter

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
        // تعديل كود الضغط لدعم الانتقال الاحترافي للأيقونة
        appAdapter = AppAdapter { app, itemView ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            
            // تحديد الأيقونة كعنصر مشترك للانتقال
            val iconView = itemView.findViewById<View>(R.id.imgAppIcon)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                iconView,
                "transition_app_icon" // المعرف المشترك الموجود في الـ XML
            )

            startActivity(intent, options.toBundle())
        }
        
        binding.rvAllApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            // إضافة تحسينات لسرعة التمرير (Scrolling)
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupObserver() {
        viewModel.appsList.observe(viewLifecycleOwner) { apps ->
            appAdapter.submitList(apps)
            
            // إضافة حركة انسيابية (Fade in) عند ظهور النتائج
            if (apps.isNotEmpty()) {
                binding.rvAllApps.alpha = 0f
                binding.rvAllApps.animate().alpha(1f).setDuration(400).start()
                binding.tvNoResults.visibility = View.GONE
            } else {
                binding.tvNoResults.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
