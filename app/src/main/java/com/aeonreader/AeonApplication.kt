package com.aeonreader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aeonreader.data.repository.CacheEvictionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AeonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleCacheEviction()
    }

    private fun scheduleCacheEviction() {
        val evictionRequest = OneTimeWorkRequestBuilder<CacheEvictionWorker>()
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "cache_eviction",
            ExistingWorkPolicy.REPLACE,
            evictionRequest
        )
    }
}
