package com.fix.engine.abdullah.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.fix.engine.abdullah.databinding.FragmentUpdatesBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Global UI Edition (Material 3 Dynamic Style)
 * Feature: Shared Element Transitions & Isolated Async Update Stream with Anti-Hijack Filter
 */
class UpdatesFragment : Fragment() {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var appAdapter: AppAdapter
    
    // متغير للتحكم بحركة التدرج وتطبيقها لمرة واحدة فقط عند الفتح منعا للوميض المزعج (Flicker)
    private var isFirstLoad = true

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
        appAdapter = AppAdapter { app, itemView ->
            val intent = Intent(requireContext(), AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            
            // 🟢 التعديل الحرج: جعل المعرف ديناميكياً ليتطابق مع الـ Adapter ويمنع تعارض الحركات
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
        
        binding.rvUpdates.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20) // كاش سريع لضمان تمرير فائق النعومة والاستقرار للأيقونات المحدثة
        }
    }

    private fun setupObserver() {
        // 🚨 تصحيح المسار: مراقبة الـ updatesList المفرزة والمجهزة مسبقاً في الخلفية بدلاً من appsList القديمة
        viewModel.updatesList.observe(viewLifecycleOwner) { updatesOnly ->
            
            // 🛡️ الفحص الأمني الحاسم (Anti-Update Hijacking):
            // نقوم بتصفية القائمة بحيث نبقي فقط على التطبيقات التي تم تثبيتها بواسطة متجرنا
            val securedUpdatesList = updatesOnly.filter { app ->
                isAppInstalledByOurStore(requireContext(), app.packageName)
            }

            // إرسال القائمة المصفاة والآمنة للـ Adapter
            appAdapter.submitList(securedUpdatesList)
            
            if (securedUpdatesList.isNotEmpty()) {
                binding.layoutAllUpdated.visibility = View.GONE
                
                // تطبيق حركة الظهور الانسيابي لمرة واحدة فقط لمنع الوميض عند استخدام صندوق البحث المباشر
                if (isFirstLoad) {
                    binding.rvUpdates.alpha = 0f
                    binding.rvUpdates.animate().alpha(1f).setDuration(350).start()
                    isFirstLoad = false
                } else {
                    binding.rvUpdates.alpha = 1f
                }
            } else {
                // إذا كانت القائمة فارغة (أو كانت تحتوي على تحديثات لتطبيقات خارجية وتم تصفيتها)
                binding.layoutAllUpdated.visibility = View.VISIBLE
            }
        }
    }

    // 🟢 التعديل الهام: تحديث الواجهة عند العودة من شاشة التفاصيل لضمان تغيير حالة الأزرار
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

/**
 * 🛡️ دالة ذكية تفحص هل التطبيق المثبت تم تنزيله عبر متجرنا أم من مصدر خارجي (مثل جوجل بلاي)
 */
private fun isAppInstalledByOurStore(context: Context, targetPackageName: String): Boolean {
    val pm = context.packageManager
    val ourStorePackage = context.packageName 

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sourceInfo = pm.getInstallSourceInfo(targetPackageName)
            sourceInfo.initiatingPackageName == ourStorePackage || 
            sourceInfo.installingPackageName == ourStorePackage
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(targetPackageName) == ourStorePackage
        }
    } catch (e: Exception) {
        false
    }
}
