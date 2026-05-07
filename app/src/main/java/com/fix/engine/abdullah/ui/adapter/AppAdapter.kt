package com.fix.engine.abdullah.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ItemAppBinding

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
            txtAppName.text = app.name
            txtDeveloper.text = app.developer
            txtVersion.text = "الإصدار: ${app.versionName}"

            Glide.with(root.context)
                .load(app.iconUrl)
                .placeholder(com.fix.engine.abdullah.R.drawable.ic_app_placeholder) // صورة مؤقتة
                .error(com.fix.engine.abdullah.R.drawable.ic_app_error) // صورة في حال الخطأ
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgAppIcon)

            // نجعل الضغط على العنصر كاملاً أو زر التحميل يؤدي لنفس النتيجة
            root.setOnClickListener { onAppClick(app) }
            btnDownload.setOnClickListener { onAppClick(app) }
        }
    }
}
    

    // لتحديث القائمة بذكاء وسرعة
    class AppDiffCallback : DiffUtil.ItemCallback<AppModel>() {
        override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel) = 
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel) = 
            oldItem == newItem
    }
}
