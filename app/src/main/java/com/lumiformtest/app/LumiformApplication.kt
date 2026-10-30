package com.lumiformtest.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.Lazy
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LumiformApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: Context): ImageLoader = imageLoader.get()
}
