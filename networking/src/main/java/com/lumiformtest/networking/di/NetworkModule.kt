package com.lumiformtest.networking.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lumiformtest.networking.BuildConfig
import com.lumiformtest.networking.NetworkConnectivity
import dagger.Module
import dagger.Lazy
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import com.lumiformtest.networking.NetworkLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    private const val IO_TIMEOUT_SECONDS = 10L
    private const val CALL_TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun connectivity(@ApplicationContext context: Context): NetworkConnectivity {
        val manager by lazy { requireNotNull(context.getSystemService(ConnectivityManager::class.java)) }
        return NetworkConnectivity {
            val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

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
        .connectTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .build()
}
