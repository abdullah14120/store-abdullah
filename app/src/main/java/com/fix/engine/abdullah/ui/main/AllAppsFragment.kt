package com.fix.engine.abdullah.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.FragmentAllAppsBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    
    // استخدام activityViewModels لمشاركة البيانات مع MainActivity
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
        appAdapter = AppAdapter { app ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            startActivity(intent)
        }
        
        binding.rvAllApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }
    }

    private fun setupObserver() {
        viewModel.appsList.observe(viewLifecycleOwner) { apps ->
            appAdapter.submitList(apps)
            // إظهار رسالة إذا كانت قائمة البحث فارغة
            binding.tvNoResults.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
