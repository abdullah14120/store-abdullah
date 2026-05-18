package com.fix.engine.abdullah.ui.adapter

import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
 * Feature: Shared Elements Support, Strict 4-State Button Logic & Material 3 Colors
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

                // سحب درجات الألوان القياسية لثيم الماتيريال 3 الزيتي ديناميكياً
                val colorPrimary = ContextCompat.getColor(context, R.color.md_theme_d_primary)
                val colorOnPrimary = ContextCompat.getColor(context, R.color.md_theme_d_onPrimary)
                val colorOnSurface = ContextCompat.getColor(context, R.color.md_theme_d_onSurface)
                val colorOnSurfaceVariant = ContextCompat.getColor(context, R.color.md_theme_d_onSurfaceVariant)
                val colorSurfaceVariant = ContextCompat.getColor(context, R.color.md_theme_d_surfaceVariant)
                val colorError = ContextCompat.getColor(context, R.color.md_theme_d_error)

                // 3. منطق الألوان والحالات الأربع الذكي المتطابق مع لغة تصاميم جوجل الحديثة
                when {
                    // الحالة أ: الملف محمل مسبقاً وجاهز للتثبيت السريع (يأخذ اللون الأساسي النشط للثيم لسرعة لفت الانتباه)
                    isDownloaded -> {
                        txtVersion.text = "ملف APK جاهز للتثبيت فوراً"
                        txtVersion.setTextColor(colorPrimary)
                        btnDownload.text = "تثبيت"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    }
                    
                    // الحالة ب: التطبيق مثبت ولكن يوجد تحديث جديد بالسيرفر (يأخذ لون الـ Error النيوني لتأكيد التنبيه)
                    isUpdateAvailable -> {
                        txtVersion.text = "تحديث متاح: ${app.versionName} (الحالي: $installedVerName)"
                        txtVersion.setTextColor(colorError)
                        btnDownload.text = "تحديث"
                        btnDownload.setTextColor(ContextCompat.getColor(context, R.color.md_theme_d_onError))
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorError)
                    }

                    // الحالة ج: التطبيق مثبت ومحدث بالكامل (يأخذ لون الحاوية والمظهر الهادئ المستقر كمتجر Droid-ify)
                    isInstalled -> {
                        txtVersion.text = "مثبت ومحدث • الإصدار ${app.versionName}"
                        txtVersion.setTextColor(colorOnSurfaceVariant)
                        btnDownload.text = "فتح"
                        btnDownload.setTextColor(colorOnSurface)
                        // جعل خلفية زر الفتح مفرغة وبخلفية رمادية زيتية هادئة لتبدو أقل بريقاً من أزرار التحديث
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorSurfaceVariant)
                    }

                    // الحالة د: التطبيق غير موجود نهائياً على الجهاز ولا في التنزيلات (حالة التنزيل الصافي الأولي)
                    else -> {
                        txtVersion.text = "الإصدار: ${app.versionName}"
                        txtVersion.setTextColor(colorPrimary)
                        btnDownload.text = "تنزيل"
                        btnDownload.setTextColor(colorOnPrimary)
                        btnDownload.backgroundTintList = ColorStateList.valueOf(colorPrimary)
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
