package com.fix.engine.abdullah.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppInstallStatus
import com.fix.engine.abdullah.data.model.AppItemUiState
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ItemAppBinding
import com.google.android.material.color.MaterialColors

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Dynamic Theme-Aware Adapter (Supports Day/Night Mode) & Clean Metadata Layout
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

        fun bind(uiState: AppItemUiState) {
            val app = uiState.app
            val rootView = binding.root

            // قراءة ألوان الثيم الحالية ديناميكياً لدعم الوضع النهاري والليلي
            val colorPrimary = MaterialColors.getColor(rootView, androidx.appcompat.R.attr.colorPrimary)
            val colorOnPrimary = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorOnPrimary)
            val colorOnSurface = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorOnSurface)
            val colorOnSurfaceVariant = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val colorSurfaceVariant = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorSurfaceVariant)
            val colorError = MaterialColors.getColor(rootView, androidx.appcompat.R.attr.colorError)
            val colorOnError = MaterialColors.getColor(rootView, com.google.android.material.R.attr.colorOnError)

            binding.apply {
                txtAppName.text = app.name
                txtDeveloper.text = app.developer

                val formattedSize = app.getFormattedSize()

                when (uiState.status) {
                    AppInstallStatus.DOWNLOADED -> {
                        txtVersion.text = "جاهز للتثبيت ✨"
                        txtVersion.setTextColor(colorPrimary)
                        txtAppMeta.isVisible = formattedSize.isNotBlank()
                        txtAppMeta.text = formattedSize

                        btnDownload.text = "تثبيت"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    }
                    AppInstallStatus.UPDATE_AVAILABLE -> {
                        txtVersion.text = "تحديث v${app.versionName}"
                        txtVersion.setTextColor(colorError)
                        txtAppMeta.isVisible = true
                        txtAppMeta.text = "الحالي: v${uiState.installedVersion}"

                        btnDownload.text = "تحديث"
                        btnDownload.setTextColor(colorOnError)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorError)
                    }
                    AppInstallStatus.INSTALLED -> {
                        txtVersion.text = "مثبت ✓"
                        txtVersion.setTextColor(colorOnSurfaceVariant)
                        txtAppMeta.isVisible = true
                        txtAppMeta.text = "v${app.versionName} • $formattedSize"

                        btnDownload.text = "فتح"
                        btnDownload.setTextColor(colorOnSurface)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorSurfaceVariant)
                    }
                    AppInstallStatus.NOT_INSTALLED -> {
                        txtVersion.text = "v${app.versionName}"
                        txtVersion.setTextColor(colorPrimary)
                        txtAppMeta.isVisible = formattedSize.isNotBlank()
                        txtAppMeta.text = formattedSize

                        btnDownload.text = "تنزيل"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    }
                }

                imgAppIcon.transitionName = "transition_app_icon_${app.packageName}"

                Glide.with(rootView.context)
                    .load(app.iconUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .dontAnimate()
                    .into(imgAppIcon)

                root.setOnClickListener { onAppClick(app, root) }
                btnDownload.setOnClickListener { onAppClick(app, root) }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppItemUiState>() {
        override fun areItemsTheSame(oldItem: AppItemUiState, newItem: AppItemUiState): Boolean {
            return oldItem.app.id == newItem.app.id
        }

        override fun areContentsTheSame(oldItem: AppItemUiState, newItem: AppItemUiState): Boolean {
            return oldItem == newItem
        }
    }
}
