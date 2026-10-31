package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.remote.ImageApi
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DEFAULT_MAX_IMAGE_BYTES = 20L * 1024 * 1024

internal class ImageFileStore(
    private val directory: File,
    private val api: ImageApi,
    private val validator: ImageFileValidator,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxBytes: Long = DEFAULT_MAX_IMAGE_BYTES
) {
    private val locks by lazy { ConcurrentHashMap<String, Mutex>() }
    private val validatedFiles by lazy { ConcurrentHashMap<String, FileStamp>() }

    suspend fun original(url: String): File = withContext(ioDispatcher) {
        locks.getOrPut(url) { Mutex() }.withLock {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create image storage")
            val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val original = File(directory, name)
            if (original.isFile) {
                val stamp = stamp(original)
                // Scrolling should not decode the same unchanged file again for validation.
                if (validatedFiles[name] == stamp || validator.isValid(original)) {
                    validatedFiles[name] = stamp
                    return@withLock original
                }
            }
            validatedFiles.remove(name)
            // Partial files never become originals. A new attempt also removes an interrupted download.
            val partial = File(directory, "$name.part")
            partial.delete()
            try {
                val response = api.download(url)
                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    throw IOException("Image request failed: ${response.code()}")
                }
                val body = response.body() ?: throw IOException("Image response has no body")
                body.use {
                    if (it.contentLength() > maxBytes) throw IOException("Image exceeds the size limit")
                    it.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > maxBytes) throw IOException("Image exceeds the size limit")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!validator.isValid(partial)) throw IOException("Invalid image content")
                currentCoroutineContext().ensureActive()
                // Both files share a directory, so readers only see a complete original.
                java.nio.file.Files.move(partial.toPath(), original.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                validatedFiles[name] = stamp(original)
                original
            } finally {
                partial.delete()
            }
        }
    }

    private fun stamp(file: File) = FileStamp(file.length(), file.lastModified())
    private data class FileStamp(val length: Long, val modified: Long)
}
