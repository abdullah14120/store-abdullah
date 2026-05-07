package com.fix.engine.abdullah.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
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
 * Project: FIX ENGINE
 * Design: Material Design 3 ListAdapter
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
            binding.apply {
                // تعيين النصوص
                txtAppName.text = app.name
                txtDeveloper.text = app.developer
                txtVersion.text = "الإصدار: ${app.versionName}"

                // تحميل الأيقونة باستخدام Glide مع الربط بالموارد الجديدة
                Glide.with(root.context)
                    .load(app.iconUrl)
                    .placeholder(R.drawable.ic_app_placeholder) // المورد الذي أنشأناه
                    .error(R.drawable.ic_app_error)             // المورد الذي أنشأناه
                    .transition(DrawableTransitionOptions.withCrossFade()) // تأثير تلاشي ناعم
                    .into(imgAppIcon)

                // تفعيل التفاعل مع العنصر
                root.setOnClickListener { onAppClick(app) }
                
                // زر التحميل السريع يفتح شاشة التفاصيل أيضاً لضمان استمرارية العملية
                btnDownload.setOnClickListener { onAppClick(app) }
                
                // إضافة خلفية الـ Ripple (تأثير الضغط) التي أنشأناها
                root.setBackgroundResource(R.drawable.bg_item_app)
            }
        }
    }

    /**
     * حساب الاختلافات بذكاء لتحديث العناصر المتغيرة فقط
     */
    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
            return oldItem == newItem
        }
    }
}
