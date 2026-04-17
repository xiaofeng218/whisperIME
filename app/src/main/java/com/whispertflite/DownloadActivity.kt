package com.whispertflite

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.whispertflite.DeveloperSettingsActivity
import com.whispertflite.databinding.ActivityDownloadBinding
import com.whispertflite.utils.Downloader
import com.whispertflite.utils.PublishedModelSync
import com.whispertflite.utils.ThemeUtils

class DownloadActivity : AppCompatActivity() {
    private var binding: ActivityDownloadBinding? = null
    private var skipAutoAdvanceOnResume = false
    private var isPublishedModelMode = false
    private var publishedModelUsername = ""
    private var publishedModelVersionTag = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeUtils.setStatusBarAppearance(this)
        isPublishedModelMode =
            intent.getStringExtra(PublishedModelSync.EXTRA_DOWNLOAD_MODE) == PublishedModelSync.DOWNLOAD_MODE_PUBLISHED_MODEL
        publishedModelUsername =
            intent.getStringExtra(PublishedModelSync.EXTRA_PUBLISHED_MODEL_USERNAME)?.trim().orEmpty()
        publishedModelVersionTag =
            intent.getStringExtra(PublishedModelSync.EXTRA_PUBLISHED_MODEL_VERSION_TAG)?.trim().orEmpty()
        applyModeUi()
        binding?.buttonDeveloperSettings?.setOnClickListener {
            skipAutoAdvanceOnResume = true
            startActivity(Intent(this, DeveloperSettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (skipAutoAdvanceOnResume) {
            skipAutoAdvanceOnResume = false
            return
        }
        if (isPublishedModelMode) {
            if (isRequestedPublishedModelInstalled()) {
                showSuccessState()
            } else {
                showDownloadState()
            }
            return
        }
        if (Downloader.checkModels(this)) {
            showSuccessState()
            if (!Downloader.checkUpdate(this)) {
                val intent = Intent(this, AuthActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                binding?.buttonUpdate?.visibility = View.VISIBLE
            }
        } else {
            showDownloadState()
        }
    }

    fun download(@Suppress("UNUSED_PARAMETER") view: View) {
        if (isPublishedModelMode) {
            if (publishedModelUsername.isBlank()) {
                Toast.makeText(this, getString(R.string.collection_need_username), Toast.LENGTH_SHORT)
                    .show()
                return
            }
            binding?.downloadSize?.visibility = View.VISIBLE
            binding?.downloadProgress?.visibility = View.VISIBLE
            binding?.buttonStart?.visibility = View.INVISIBLE
            binding?.downloadButton?.visibility = View.GONE
            binding?.buttonUpdate?.visibility = View.GONE
            binding?.circularLoading?.visibility = View.VISIBLE

            Downloader.downloadPublishedModel(
                this,
                binding,
                publishedModelUsername,
                publishedModelVersionTag
            )
            return
        }
        binding?.downloadSize?.visibility = View.VISIBLE
        binding?.downloadProgress?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.INVISIBLE
        binding?.downloadButton?.visibility = View.GONE
        binding?.circularLoading?.visibility = View.VISIBLE
        
        Downloader.downloadModels(this, binding)
    }

    fun showSuccessState() {
        binding?.downloadProgress?.progress = 100
        binding?.downloadProgress?.isIndeterminate = false
        binding?.downloadSize?.visibility = View.VISIBLE
        binding?.downloadProgress?.visibility = View.VISIBLE
        binding?.circularLoading?.visibility = View.GONE
        binding?.downloadButton?.visibility = View.GONE
        binding?.buttonUpdate?.visibility = View.GONE
        binding?.successCheck?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.VISIBLE
    }

    fun showDownloadState() {
        binding?.downloadProgress?.progress = 0
        binding?.downloadProgress?.isIndeterminate = false
        binding?.downloadSize?.visibility = View.GONE
        binding?.downloadProgress?.visibility = View.GONE
        binding?.circularLoading?.visibility = View.GONE
        binding?.downloadButton?.visibility = View.VISIBLE
        binding?.downloadButton?.isEnabled = true
        binding?.downloadButton?.isClickable = true
        binding?.successCheck?.visibility = View.GONE
        binding?.buttonStart?.visibility = View.GONE
        binding?.buttonUpdate?.visibility = View.GONE
    }

    fun startMain(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = if (isPublishedModelMode) {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            Intent(this, AuthActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    fun updateModels(@Suppress("UNUSED_PARAMETER") view: View) {
        binding?.downloadSize?.visibility = View.VISIBLE
        binding?.downloadProgress?.visibility = View.VISIBLE
        binding?.buttonStart?.visibility = View.INVISIBLE
        binding?.buttonUpdate?.visibility = View.GONE
        
        binding?.downloadButton?.visibility = View.GONE
        binding?.circularLoading?.visibility = View.VISIBLE
        
        Downloader.deleteOldModels(this)
        Downloader.downloadModels(this, binding)
    }

    private fun applyModeUi() {
        if (!isPublishedModelMode) {
            return
        }
        binding?.downloadTitle?.setText(R.string.download_published_model)
        binding?.downloadSubtitle?.setText(R.string.download_published_model_text)
        binding?.buttonStart?.setText(R.string.auth_back_main)
        binding?.buttonUpdate?.visibility = View.GONE
    }

    private fun isRequestedPublishedModelInstalled(): Boolean {
        return PublishedModelSync.isPublishedModelVersionInstalled(
            this,
            PublishedModelSync.getPreferences(this),
            publishedModelUsername,
            publishedModelVersionTag
        )
    }
}
