package com.fix.engine.abdullah.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppInstallStatus
import com.fix.engine.abdullah.data.model.AppItemUiState
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ItemAppBinding

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Pure Presentation Adapter, Pre-calculated States & Cached Resources
 */
class AppAdapter(private val onAppClick: (AppModel, View) -> Unit) :
    ListAdapter<AppItemUiState, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // 🟢 التخزين المؤقت للألوان (Caching) لمنع استدعائها مع كل عنصر أثناء التمرير السريع
        private val colorPrimary = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_primary)
        private val colorOnPrimary = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_onPrimary)
        private val colorOnSurface = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_onSurface)
        private val colorOnSurfaceVariant = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_onSurfaceVariant)
        private val colorSurfaceVariant = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_surfaceVariant)
        private val colorError = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_error)
        private val colorOnError = ContextCompat.getColor(binding.root.context, R.color.md_theme_d_onError)

        fun bind(uiState: AppItemUiState) {
            // استخراج كائن التطبيق الأصلي
            val app = uiState.app
            
            binding.apply {
                txtAppName.text = app.name
                txtDeveloper.text = app.developer

                // 🟢 منطق العرض النظيف (Pure UI Logic): نعتمد على الحالة المعالجة مسبقاً في الخلفية
                when (uiState.status) {
                    AppInstallStatus.DOWNLOADED -> {
                        txtVersion.text = "ملف APK جاهز للتثبيت فوراً"
                        txtVersion.setTextColor(colorPrimary)
                        btnDownload.text = "تثبيت"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    }
                    AppInstallStatus.UPDATE_AVAILABLE -> {
                        txtVersion.text = "تحديث متاح: ${app.versionName} (الحالي: ${uiState.installedVersion})"
                        txtVersion.setTextColor(colorError)
                        btnDownload.text = "تحديث"
                        btnDownload.setTextColor(colorOnError)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorError)
                    }
                    AppInstallStatus.INSTALLED -> {
                        txtVersion.text = "مثبت ومحدث • الإصدار ${app.versionName}"
                        txtVersion.setTextColor(colorOnSurfaceVariant)
                        btnDownload.text = "فتح"
                        btnDownload.setTextColor(colorOnSurface)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorSurfaceVariant)
                    }
                    AppInstallStatus.NOT_INSTALLED -> {
                        txtVersion.text = "الإصدار: ${app.versionName}"
                        txtVersion.setTextColor(colorPrimary)
                        btnDownload.text = "تنزيل"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    }
                }

                // 4. هندسة الانتقال المشترك (Shared Element Transition)
                imgAppIcon.transitionName = "transition_app_icon_${app.packageName}" 
                
                Glide.with(binding.root.context)
                    .load(app.iconUrl)
                    .placeholder(R.mipmap.ic_launcher) 
                    .error(R.mipmap.ic_launcher)       
                    .dontAnimate() 
                    .into(imgAppIcon)

                // 5. الأحداث: نقوم بتمرير الموديل الأصلي (app) لكي تفتحه شاشة التفاصيل
                root.setOnClickListener { onAppClick(app, root) }
                btnDownload.setOnClickListener { onAppClick(app, root) }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppItemUiState>() {
        override fun areItemsTheSame(oldItem: AppItemUiState, newItem: AppItemUiState): Boolean {
            return oldItem.app.packageName == newItem.app.packageName
        }

        override fun areContentsTheSame(oldItem: AppItemUiState, newItem: AppItemUiState): Boolean {
            return oldItem == newItem
        }
    }
}
