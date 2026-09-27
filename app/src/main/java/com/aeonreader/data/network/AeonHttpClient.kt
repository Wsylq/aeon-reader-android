package com.aeonreader.data.network

import com.aeonreader.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AeonHttpClient {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                // Only fill in headers the caller has not set, so per-request
                // values (e.g. the RSS Accept below) survive.
                val original = chain.request()
                val builder = original.newBuilder()
                if (original.header("User-Agent") == null) {
                    builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.83 Mobile Safari/537.36")
                }
                if (original.header("Accept") == null) {
                    builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                }
                if (original.header("Accept-Language") == null) {
                    builder.header("Accept-Language", "en-US,en;q=0.9")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()
    }
}
