package com.lumiformtest.form.data.remote

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.lumiformtest.form.data.local.ImageFileStore
import okio.FileSystem
import okio.Path.Companion.toOkioPath

// Coil uses the same retained original for a thumbnail and the full viewer.
internal class StoredImageFetcher(
    private val url: String,
    private val store: ImageFileStore
) : Fetcher {
    override suspend fun fetch(): SourceFetchResult = SourceFetchResult(
        source = ImageSource(store.original(url).toOkioPath(), FileSystem.SYSTEM),
        mimeType = null,
        dataSource = DataSource.DISK
    )

    class Factory(private val store: ImageFileStore) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "https" || data.scheme == "http") StoredImageFetcher(data.toString(), store) else null
    }
}
