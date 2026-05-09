package com.fix.engine.abdullah.ui.adapter

import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
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
 * Project: Abdullah Store - FIX ENGINE Edition
 * Feature: Real-time Update Detection & Identity Sync
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
                
                // فحص حالة التحديث برمجياً
                val isUpdateAvailable = checkUpdateStatus(app)
                
                if (isUpdateAvailable) {
                    txtVersion.text = "تحديث متاح: ${app.versionName}"
                    // استخدام اللون الجديد من ملف colors.xml الذي أنشأناه
                    txtVersion.setTextColor(ContextCompat.getColor(context, R.color.secondary_cyan))
                    btnDownload.text = "تحديث"
                    btnDownload.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_tech_blue))
                } else {
                    txtVersion.text = "الإصدار: ${app.versionName}"
                    txtVersion.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    btnDownload.text = "فتح"
                    // لون افتراضي للأزرار العادية
                    btnDownload.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
                }

                // تحميل أيقونة التطبيق باستخدام Glide
                Glide.with(context)
                    .load(app.iconUrl)
                    .placeholder(R.drawable.ic_launcher_foreground) // استخدام أيقونتك الجديدة كـ Placeholder
                    .error(R.drawable.ic_launcher_foreground)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imgAppIcon)

                root.setOnClickListener { onAppClick(app) }
                btnDownload.setOnClickListener { onAppClick(app) }
            }
        }

        /**
         * دالة متطورة لمقارنة إصدار التطبيق المثبت مع بيانات المستودع (JSON)
         */
        private fun checkUpdateStatus(app: AppModel): Boolean {
            return try {
                val packageManager = binding.root.context.packageManager
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(app.packageName, 0)
                }

                val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }

                // مقارنة VersionCode الخاص بالـ JSON مع المثبت فعلياً على الجهاز
                app.versionCode > installedVersionCode
            } catch (e: PackageManager.NameNotFoundException) {
                // إذا لم يتم العثور على التطبيق، نعتبره غير مثبت (وليس تحديثاً)
                false 
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
