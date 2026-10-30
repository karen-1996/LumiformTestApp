package com.lumiformtest.networking.di

import com.lumiformtest.networking.BuildConfig
import dagger.Module
import dagger.Lazy
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import com.lumiformtest.networking.NetworkLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun loggingInterceptor(): NetworkLoggingInterceptor = NetworkLoggingInterceptor()

    @Provides
    @Singleton
    fun client(loggingInterceptor: Lazy<NetworkLoggingInterceptor>): OkHttpClient = OkHttpClient.Builder()
        .apply {
            // Debug logs form bodies and image headers. Release creates no logger.
            if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor.get())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .build()
}
