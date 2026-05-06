package com.fix.engine.abdullah.ui.details

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.util.ViewUtil // اختياري للتعامل مع الأحجام

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDetails)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarDetails.setNavigationOnClickListener { finish() }

        // استقبال البيانات (سنقوم بتمريرها كـ JSON أو Serializable)
        val appData = intent.getSerializableExtra("APP_DATA") as? AppModel
        
        appData?.let { displayDetails(it) }
    }

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            // تحديث البطاقات الصغيرة (الإصدار والحجم)
            // badgeVersion.txtTitle.text = "الإصدار"
            // badgeVersion.txtValue.text = app.versionName

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .into(imgDetailsIcon)

            btnInstallLarge.setOnClickListener {
                // هنا سيتم استدعاء محرك التحميل لاحقاً
            }
        }
    }
}
