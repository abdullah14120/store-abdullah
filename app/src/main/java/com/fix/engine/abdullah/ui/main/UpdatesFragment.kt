package com.fix.engine.abdullah.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.databinding.FragmentUpdatesBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

class UpdatesFragment : Fragment() {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObserver()
    }

    private fun setupRecyclerView() {
        // تم التعديل هنا لاستقبال متغيرين (app, view) لحل خطأ الـ Compilation
        appAdapter = AppAdapter { app, sharedView ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            
            // إضافة دعم لحركة الانتقال الانسيابية (Shared Element Transition) للاحترافية
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                sharedView,
                "transition_app_icon"
            )
            
            startActivity(intent, options.toBundle())
        }
        
        binding.rvUpdates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }
    }

    private fun setupObserver() {
        viewModel.appsList.observe(viewLifecycleOwner) { allApps ->
            val pm = requireContext().packageManager
            
            // تصفية التطبيقات: فقط المثبتة والتي تملك versionName مختلف
            val updatesOnly = allApps.filter { app ->
                try {
                    val pInfo = pm.getPackageInfo(app.packageName, 0)
                    val installedVersion = pInfo.versionName ?: ""
                    // منطق عبدالله: المقارنة بالاسم لتجاوز سقف الـ VersionCode
                    app.versionName.trim() != installedVersion.trim()
                } catch (e: PackageManager.NameNotFoundException) {
                    false 
                }
            }

            appAdapter.submitList(updatesOnly)
            
            // إظهار واجهة "كل تطبيقاتك محدثة" إذا كانت القائمة فارغة
            binding.layoutAllUpdated.visibility = if (updatesOnly.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
