package com.bleudev.modsync.http.clientbound

import com.bleudev.modsync.ModSync.Companion.JSON
import com.bleudev.modsync.http.serverbound.serialization.ModSyncMetadata
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class ClientHttpHelper(val fullAddress: String) {
    typealias ProgressFunction = (downloaded: Long, total: Long?) -> Unit

    private fun <T> get(endpoint: String, action: (HttpURLConnection) -> T): T {
        val connection = URI.create("$fullAddress$endpoint").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("ModSync HTTP ${connection.responseCode}: $fullAddress$endpoint")
            }
            return action(connection)
        } finally {
            connection.disconnect()
        }
    }

    internal fun metadata(): ModSyncMetadata = get("/") { connection ->
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            JSON.decodeFromString<ModSyncMetadata>(reader.readText())
        }
    }

    internal fun mod(modsDir: Path, data: ModSyncMetadata.ModMetadata, onProgress: ProgressFunction = { _, _ -> }) {
        require(data.id.matches(Regex("[a-z][a-z0-9_-]{1,63}"))) { "Invalid mod ID: ${data.id}" }
        val path = modsDir.resolve(data.fileName).normalize()
        require(path.parent == modsDir.normalize() && data.fileName.endsWith(".jar")) { "Invalid mod filename" }
        get("/${data.id}") { connection ->
            val total = connection.contentLengthLong.takeIf { it >= 0 }
            onProgress(0, total)
            connection.inputStream.use { input ->
                Files.createDirectories(path.parent)
                Files.newOutputStream(path).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                    if (total != null && downloaded != total) throw IOException("Incomplete download: ${data.id}")
                }
            }
        }
    }
}
