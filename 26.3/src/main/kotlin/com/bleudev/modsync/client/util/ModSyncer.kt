package com.bleudev.modsync.client.util

import com.bleudev.modsync.ModSync.Companion.JSON
import com.bleudev.modsync.client.ClientTempStorageManager
import com.bleudev.modsync.http.clientbound.ClientHttpHelper
import com.bleudev.modsync.http.serverbound.serialization.ModSyncMetadata
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.Version
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile

class ModSyncer private constructor(private val modsDir: Path) {
    fun fetch(address: String): List<ModSyncMetadata.ModMetadata> = ClientHttpHelper(address).metadata().mods.filter { data ->
        val current = FabricLoader.getInstance().getModContainer(data.id)
        current.isEmpty || current.get().metadata.version < Version.parse(data.version)
    }

    // Loaded jars stay untouched until the game process has released them.
    fun syncAll(address: String, mods: List<ModSyncMetadata.ModMetadata>) {
        val updatesDir = Files.createDirectories(modsDir.parent.resolve("modsync-updates"))
        val stage = Files.createTempDirectory(updatesDir, "update-")
        val manifest = Properties()
        val oldPaths = linkedSetOf<Path>()
        val targets = hashSetOf<Path>()
        for ((index, data) in mods.withIndex()) {
            val folder = Files.createDirectory(stage.resolve(index.toString()))
            ClientHttpHelper(address).mod(folder, data) { downloaded, total ->
                ClientTempStorageManager.getInstance().downloadReadBytes = downloaded
                ClientTempStorageManager.getInstance().downloadTotalBytes = total
            }
            val downloaded = folder.resolve(data.fileName).normalize()
            ZipFile(downloaded.toFile()).use { zip ->
                val entry = requireNotNull(zip.getEntry("fabric.mod.json")) { "Not a Fabric mod: ${data.id}" }
                val metadata = zip.getInputStream(entry).bufferedReader().use { JSON.parseToJsonElement(it.readText()).jsonObject }
                require(metadata["id"]?.jsonPrimitive?.content == data.id && metadata["version"]?.jsonPrimitive?.content == data.version) {
                    "Downloaded mod does not match ${data.id} ${data.version}"
                }
                for (item in zip.entries()) if (!item.isDirectory) {
                    val crc = java.util.zip.CRC32()
                    val size = zip.getInputStream(item).use { input ->
                        java.util.zip.CheckedInputStream(input, crc).transferTo(java.io.OutputStream.nullOutputStream())
                    }
                    require(size == item.size && crc.value == item.crc) { "Corrupt archive: ${data.id}" }
                }
            }
            val target = modsDir.resolve(data.fileName).normalize()
            require(targets.add(target)) { "Duplicate target: $target" }
            if (Files.exists(target)) oldPaths.add(target)
            FabricLoader.getInstance().getModContainer(data.id).ifPresent { container ->
                for (path in container.origin.paths) {
                    val old = path.toAbsolutePath().normalize()
                    require(old.parent == modsDir && old.fileName.toString().endsWith(".jar")) {
                        "Cannot automatically replace nested or external mod: ${data.id}"
                    }
                    oldPaths.add(old)
                }
            }
            manifest.setProperty("source.$index", downloaded.toString())
            manifest.setProperty("target.$index", target.toString())
        }
        manifest.setProperty("count", mods.size.toString())
        manifest.setProperty("old.count", oldPaths.size.toString())
        oldPaths.forEachIndexed { index, path -> manifest.setProperty("old.$index", path.toString()) }
        val manifestPath = stage.resolve("install.properties")
        Files.newOutputStream(manifestPath).use { manifest.store(it, "ModSync pending update") }
        val resource = "com/bleudev/modsync/client/util/PendingModInstaller.class"
        val helper = stage.resolve(resource)
        Files.createDirectories(helper.parent)
        PendingModInstaller::class.java.classLoader.getResourceAsStream(resource).use { input ->
            requireNotNull(input) { "Missing update installer" }
            Files.copy(input, helper)
        }
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val installer = ProcessBuilder(javaExecutable.toString(), "-cp", stage.toString(), PendingModInstaller::class.java.name,
            ProcessHandle.current().pid().toString(), manifestPath.toString())
            .redirectErrorStream(true).redirectOutput(stage.resolve("install.log").toFile()).start()
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
        while (!Files.exists(stage.resolve("READY"))) {
            if (!installer.isAlive || System.nanoTime() >= deadline) {
                installer.destroyForcibly().waitFor()
                error("Could not start installer. See $stage/install.log")
            }
            Thread.sleep(50)
        }
    }

    companion object {
        fun getInstance(): ModSyncer = ModSyncer(FabricLoader.getInstance().gameDir.resolve("mods").toAbsolutePath().normalize())
    }
}
