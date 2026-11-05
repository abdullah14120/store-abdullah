package com.fix.engine.abdullah.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.databinding.FragmentUpdatesBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Refactored: UI State Observation, Interactive Empty State & Janky-Free Scrolling
 */
class UpdatesFragment : Fragment() {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter

    private var isFirstLoad = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUpdatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupEmptyStateButton()
        setupObserver()
    }

    private fun setupEmptyStateButton() {
        binding.btnCheckUpdatesAgain.setOnClickListener {
            Toast.makeText(requireContext(), "جاري فحص التحديثات... 🔄", Toast.LENGTH_SHORT).show()
            viewModel.refreshAppStates()
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

        binding.rvUpdates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupObserver() {
        viewModel.updatesUiStateList.observe(viewLifecycleOwner) { uiStates ->
            appAdapter.submitList(uiStates)

            if (!uiStates.isNullOrEmpty()) {
                binding.layoutAllUpdated.visibility = View.GONE

                if (isFirstLoad) {
                    binding.rvUpdates.alpha = 0f
                    binding.rvUpdates.animate().alpha(1f).setDuration(350).start()
                    isFirstLoad = false
                } else {
                    binding.rvUpdates.alpha = 1f
                }
            } else {
                binding.layoutAllUpdated.visibility = View.VISIBLE
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
