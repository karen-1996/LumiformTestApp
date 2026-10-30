package com.lumiformtest.form.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.lumiformtest.form.data.local.BitmapImageFileValidator
import com.lumiformtest.form.data.local.ImageFileStore
import com.lumiformtest.form.data.remote.ImageApi
import com.lumiformtest.form.data.remote.StoredImageFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object FormImageModule {
    @Provides
    @Singleton
    fun api(retrofit: Retrofit): ImageApi = retrofit.create(ImageApi::class.java)

    @Provides
    @Singleton
    fun store(
        @ApplicationContext context: Context,
        api: ImageApi,
        validator: BitmapImageFileValidator,
        @FormIoDispatcher ioDispatcher: CoroutineDispatcher
    ): ImageFileStore = ImageFileStore(File(context.filesDir, "form-images"), api, validator, ioDispatcher)

    @Provides
    @Singleton
    fun imageLoader(@ApplicationContext context: Context, store: ImageFileStore): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(StoredImageFetcher.Factory(store)) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            // Originals are the disk layer. I keep them outside Coil's evictable cache.
            .diskCache(null)
            .build()
}
