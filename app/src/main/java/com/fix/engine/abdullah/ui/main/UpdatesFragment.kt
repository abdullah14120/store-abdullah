package com.fix.engine.abdullah.ui.main

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.databinding.FragmentUpdatesBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

class UpdatesFragment : Fragment() {
    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = AppAdapter { app -> 
            // فتح صفحة التفاصيل عند الضغط
        }

        binding.rvUpdates.layoutManager = LinearLayoutManager(context)
        binding.rvUpdates.adapter = adapter

        viewModel.appsList.observe(viewLifecycleOwner) { allApps ->
            val pm = requireContext().packageManager
            val updateList = allApps.filter { app ->
                try {
                    val installedVer = pm.getPackageInfo(app.packageName, 0).versionName
                    // المقارنة النصية لتجاوز مشكلة الـ Version Code الثابت
                    app.versionName.trim() != installedVer?.trim()
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
            
            if (updateList.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            } else {
                binding.tvEmpty.visibility = View.GONE
                adapter.submitList(updateList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
