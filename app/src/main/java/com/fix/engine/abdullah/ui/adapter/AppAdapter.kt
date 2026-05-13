package com.fix.engine.abdullah.ui.adapter

import android.content.pm.PackageManager
import android.os.Environment
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
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Global UI Edition
 * Feature: Shared Elements Support & Name-Based Update Logic
 */
class AppAdapter(private val onAppClick: (AppModel, View) -> Unit) :
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
                // تعيين اسم التطبيق والمطور
                txtAppName.text = app.name
                txtDeveloper.text = app.developer

                // 1. تحديد مسار الملف المحلي (الـ APK الحقيقي فقط بدون .tmp)
                val fileName = app.getUniqueFileName()
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val localFile = File(downloadsDir, fileName)

                // 2. منطق عبدالله المحدث: فحص التحديث بناءً على الاسم (Version Name)
                val isUpdateAvailable = checkUpdateStatusByName(app)
                val isDownloaded = localFile.exists()

                // 3. تنسيق الواجهة الاحترافية بناءً على الحالة
                when {
                    isUpdateAvailable -> {
                        txtVersion.text = "تحديث متاح: ${app.versionName}"
                        txtVersion.setTextColor(ContextCompat.getColor(context, R.color.secondary_cyan))
                        btnDownload.text = if (isDownloaded) "تثبيت" else "تحديث"
                        btnDownload.setBackgroundResource(R.drawable.bg_button_update) // ستايل مخصص
                    }
                    else -> {
                        txtVersion.text = "الإصدار الحالي: ${app.versionName}"
                        txtVersion.setTextColor(ContextCompat.getColor(context, R.color.gray_light))
                        btnDownload.text = if (isDownloaded) "تثبيت" else "فتح"
                        btnDownload.setBackgroundResource(R.drawable.bg_button_open)
                    }
                }

                // 4. تحميل الأيقونة مع دعم الـ Transition Name للانتقال العالمي
                imgAppIcon.transitionName = "transition_app_icon_${app.packageName}" 
                
                Glide.with(context)
                    .load(app.iconUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imgAppIcon)

                // 5. تمرير الـ itemView بالكامل لتمكين حركة العناصر المشتركة
                root.setOnClickListener { onAppClick(app, root) }
                btnDownload.setOnClickListener { onAppClick(app, root) }
            }
        }

        /**
         * فحص التحديث بناءً على مقارنة نصية للـ Version Name لتجاوز قيود الـ Version Code
         */
        private fun checkUpdateStatusByName(app: AppModel): Boolean {
            return try {
                val pm = binding.root.context.packageManager
                val pInfo = pm.getPackageInfo(app.packageName, 0)
                val installedVerName = pInfo.versionName ?: ""
                
                // مقارنة دقيقة للنصوص لإظهار التحديث
                app.versionName.trim() != installedVerName.trim()
            } catch (e: PackageManager.NameNotFoundException) {
                false // التطبيق غير مثبت أصلاً
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel) = oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel) = oldItem == newItem
    }
}
