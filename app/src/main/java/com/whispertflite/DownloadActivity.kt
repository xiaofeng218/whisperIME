package com.whispertflite

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.whispertflite.databinding.ActivityDownloadBinding
import com.whispertflite.utils.Downloader
import com.whispertflite.utils.ThemeUtils

class DownloadActivity : AppCompatActivity() {
    private var binding: ActivityDownloadBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeUtils.setStatusBarAppearance(this)
    }

    override fun onResume() {
        super.onResume()
        if (Downloader.checkModels(this)) {
            showSuccessState()
            if (!Downloader.checkUpdate(this)) {
                val intent = Intent(this, AuthActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                binding?.buttonUpdate?.visibility = View.VISIBLE
            }
        }
    }

    fun download(view: View) {
        binding?.downloadSize?.visibility = View.VISIBLE
        binding?.downloadProgress?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.INVISIBLE
        binding?.downloadButton?.visibility = View.GONE
        binding?.circularLoading?.visibility = View.VISIBLE
        
        Downloader.downloadModels(this, binding)
    }

    private fun showSuccessState() {
        binding?.downloadProgress?.progress = 100
        binding?.circularLoading?.visibility = View.GONE
        binding?.downloadButton?.visibility = View.GONE
        binding?.successCheck?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.VISIBLE
    }

    fun startMain(view: View) {
        val intent = Intent(this, AuthActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun updateModels(view: View) {
        binding?.downloadSize?.visibility = View.VISIBLE
        binding?.downloadProgress?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.INVISIBLE
        binding?.buttonUpdate?.visibility = View.GONE
        
        binding?.downloadButton?.visibility = View.GONE
        binding?.circularLoading?.visibility = View.VISIBLE
        
        Downloader.deleteOldModels(this)
        Downloader.downloadModels(this, binding)
    }
}
