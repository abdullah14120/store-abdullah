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
 * Refactored: Dual-Mode Fragment (All Apps Tab & Installed Apps Tab), Smart Empty State & Janky-Free Scrolling
 */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter

    private var isFirstLoad = true
    private var isInstalledOnlyTab = false

    companion object {
        private const val ARG_ONLY_INSTALLED = "arg_only_installed"

        fun newInstance(onlyInstalled: Boolean = false): AllAppsFragment {
            return AllAppsFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_ONLY_INSTALLED, onlyInstalled)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isInstalledOnlyTab = arguments?.getBoolean(ARG_ONLY_INSTALLED, false) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureEmptyStateTexts()
        setupRecyclerView()
        setupObserver()
    }

    private fun configureEmptyStateTexts() {
        if (isInstalledOnlyTab) {
            binding.tvEmptyIcon.text = "📱"
            binding.tvNoResults.text = "لا توجد تطبيقات مثبتة من المتجر"
            binding.tvEmptySubtitle.text = "عندما تقوم بتنزيل وتثبيت أي تطبيق من المتجر سيظهر هنا تلقائياً لإدارته بسهولة."
        } else {
            binding.tvEmptyIcon.text = "🔍"
            binding.tvNoResults.text = "لا توجد نتائج مطابقة"
            binding.tvEmptySubtitle.text = "جرّب البحث بكلمة أخرى أو اختر تصنيفاً مختلفاً من الأعلى."
        }
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app, itemView ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }

            val iconView = itemView.findViewById<View>(R.id.imgAppIcon)
            if (iconView != null) {
                val uniqueTransitionName = "transition_app_icon_${app.packageName}"
                ViewCompat.setTransitionName(iconView, uniqueTransitionName)

                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    iconView,
                    uniqueTransitionName
                )
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
        }

        binding.rvAllApps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupObserver() {
        val targetLiveData = if (isInstalledOnlyTab) {
            viewModel.installedUiStateList
        } else {
            viewModel.appsUiStateList
        }

        targetLiveData.observe(viewLifecycleOwner) { uiStates ->
            appAdapter.submitList(uiStates)

            if (!uiStates.isNullOrEmpty()) {
                binding.layoutEmptyState.visibility = View.GONE
                binding.tvNoResults.visibility = View.GONE

                if (isFirstLoad) {
                    binding.rvAllApps.alpha = 0f
                    binding.rvAllApps.animate().alpha(1f).setDuration(350).start()
                    isFirstLoad = false
                } else {
                    binding.rvAllApps.alpha = 1f
                }
            } else {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.tvNoResults.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            viewModel.refreshAppStates()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
