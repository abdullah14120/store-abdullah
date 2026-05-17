package com.fix.engine.abdullah.ui.adapter

import android.content.pm.PackageManager
import android.graphics.Color
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
 * Feature: Shared Elements Support, Strict 4-State Button Logic & Elegant Color States
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
                txtAppName.text = app.name
                txtDeveloper.text = app.developer

                // 1. تحديد مسار الملف المحلي .apk
                val fileName = app.getUniqueFileName()
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val localFile = File(downloadsDir, fileName)
                val isDownloaded = localFile.exists()

                // 2. جلب حالة التثبيت والإصدار
                val pm = context.packageManager
                var isInstalled = false
                var isUpdateAvailable = false
                var installedVerName = ""

                try {
                    val pInfo = pm.getPackageInfo(app.packageName, 0)
                    installedVerName = pInfo.versionName ?: ""
                    isInstalled = true
                    isUpdateAvailable = app.versionName.trim() != installedVerName.trim()
                } catch (e: PackageManager.NameNotFoundException) {
                    isInstalled = false
                    isUpdateAvailable = false
                }

                // 3. منطق الألوان والحالات الأربع الذكي لمتجر عبدالله
                when {
                    // الحالة أ: الملف محمل مسبقاً وجاهز للتثبيت السريع (اللون الأخضر)
                    isDownloaded -> {
                        txtVersion.text = "ملف APK جاهز للتثبيت فوراً"
                        txtVersion.setTextColor(Color.parseColor("#4ADE80")) // أخضر زاهي وملفت للتثبيت
                        btnDownload.text = "تثبيت"
                        btnDownload.setTextColor(Color.parseColor("#0F172A"))
                        btnDownload.setBackgroundResource(R.drawable.bg_button_update) 
                    }
                    
                    // الحالة ب: التطبيق مثبت ولكن يوجد تحديث جديد بالسيرفر (اللون الأحمر الملفت للإنتباه)
                    isUpdateAvailable -> {
                        txtVersion.text = "تحديث متاح: ${app.versionName} (الحالي: $installedVerName)"
                        txtVersion.setTextColor(Color.parseColor("#EF4444")) // أحمر تنبيهي واضح لوجود تحديث
                        btnDownload.text = "تحديث"
                        btnDownload.setTextColor(Color.parseColor("#0F172A"))
                        btnDownload.setBackgroundResource(R.drawable.bg_button_update)
                    }

                    // الحالة ج: التطبيق مثبت ومحدث بالكامل (اللون الرمادي الهادئ المستقر)
                    isInstalled -> {
                        txtVersion.text = "مثبت ومحدث • الإصدار ${app.versionName}"
                        txtVersion.setTextColor(ContextCompat.getColor(context, R.color.gray_light)) 
                        btnDownload.text = "فتح"
                        btnDownload.setTextColor(Color.parseColor("#F8FAFC"))
                        btnDownload.setBackgroundResource(R.drawable.bg_button_open)
                    }

                    // الحالة د: التطبيق غير موجود نهائياً على الجهاز ولا في التنزيلات (اللون الـ Cyan المعتمد للمتجر)
                    else -> {
                        txtVersion.text = "الإصدار: ${app.versionName}"
                        txtVersion.setTextColor(Color.parseColor("#22D3EE")) // لون السايان المميز للتنزيلات الجديدة
                        btnDownload.text = "تنزيل"
                        btnDownload.setTextColor(Color.parseColor("#0F172A"))
                        btnDownload.setBackgroundResource(R.drawable.bg_button_update) 
                    }
                }

                // 4. تحميل الأيقونة
                imgAppIcon.transitionName = "transition_app_icon_${app.packageName}" 
                
                Glide.with(context)
                    .load(app.iconUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imgAppIcon)

                // 5. الأحداث
                root.setOnClickListener { onAppClick(app, root) }
                btnDownload.setOnClickListener { onAppClick(app, root) }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel) = oldItem.packageName == newItem.packageName
        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel) = oldItem == newItem
    }
}
