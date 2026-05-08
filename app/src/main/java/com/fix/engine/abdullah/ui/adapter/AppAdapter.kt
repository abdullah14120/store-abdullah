package com.fix.engine.abdullah.ui.adapter

import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ItemAppBinding

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Update Engine Edition
 * Feature: Real-time Update Detection in List
 */
class AppAdapter(private val onAppClick: (AppModel) -> Unit) :
    ListAdapter<AppModel, AppAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppModel) {
            val context = binding.root.context
            binding.apply {
                txtAppName.text = app.name
                txtDeveloper.text = app.developer
                
                // فحص حالة التحديث
                val isUpdateAvailable = checkUpdateStatus(app)
                
                if (isUpdateAvailable) {
                    txtVersion.text = "تحديث متاح: ${app.versionName}"
                    txtVersion.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
                    btnDownload.text = "تحديث"
                    // يمكنك تغيير خلفية الزر هنا لتمييزه
                } else {
                    txtVersion.text = "الإصدار: ${app.versionName}"
                    txtVersion.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    btnDownload.text = "فتح"
                }

                Glide.with(context)
                    .load(app.iconUrl)
                    .placeholder(R.drawable.ic_app_placeholder)
                    .error(R.drawable.ic_app_error)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imgAppIcon)

                root.setOnClickListener { onAppClick(app) }
                btnDownload.setOnClickListener { onAppClick(app) }
            }
        }

        /**
         * دالة داخلية لمقارنة نسخة التطبيق المثبتة مع المستودع
         */
        private fun checkUpdateStatus(app: AppModel): Boolean {
            return try {
                val pInfo = binding.root.context.packageManager.getPackageInfo(app.packageName, 0)
                val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
                // إذا كان إصدار المستودع (JSON) أكبر من المثبت
                app.versionCode > installedVersionCode
            } catch (e: PackageManager.NameNotFoundException) {
                false // التطبيق غير مثبت، لا نعتبره تحديثاً بل تثبيت جديد
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}
