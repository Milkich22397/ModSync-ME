package com.bleudev.modsync.client.util;

import java.nio.file.*;
import java.util.*;

/** Standalone helper: no Minecraft, Fabric or Kotlin dependencies. */
public final class PendingModInstaller {
    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[1]).getParent().resolve("READY"), "Installer ready");
        ProcessHandle.of(Long.parseLong(args[0])).ifPresent(process -> process.onExit().join());
        install(Path.of(args[1]));
    }

    public static void install(Path manifestPath) throws Exception {
        Properties manifest = new Properties();
        try (var input = Files.newInputStream(manifestPath)) { manifest.load(input); }
        Path backupDir = Files.createDirectories(manifestPath.getParent().resolve("backup"));
        Map<Path, Path> backups = new LinkedHashMap<>();
        List<Path> installed = new ArrayList<>();
        try {
            int count = Integer.parseInt(manifest.getProperty("count"));
            for (int i = 0; i < count; i++) {
                if (!Files.isRegularFile(Path.of(manifest.getProperty("source." + i))))
                    throw new java.io.IOException("Missing staged mod " + i);
            }
            int oldCount = Integer.parseInt(manifest.getProperty("old.count"));
            for (int i = 0; i < oldCount; i++) {
                Path old = Path.of(manifest.getProperty("old." + i));
                Path backup = backupDir.resolve(i + ".jar");
                moveWithRetry(old, backup);
                backups.put(old, backup);
            }
            for (int i = 0; i < count; i++) {
                Path target = Path.of(manifest.getProperty("target." + i));
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) throw new java.io.IOException("Target appeared during update: " + target);
                installed.add(target);
                Files.copy(Path.of(manifest.getProperty("source." + i)), target);
            }
            Files.writeString(manifestPath.getParent().resolve("SUCCESS"), "Updates installed. You can start Minecraft.");
        } catch (Exception failure) {
            for (Path target : installed) {
                try { Files.deleteIfExists(target); } catch (Exception e) { failure.addSuppressed(e); }
            }
            for (var entry : backups.entrySet()) {
                try { moveWithRetry(entry.getValue(), entry.getKey()); } catch (Exception e) { failure.addSuppressed(e); }
            }
            throw failure;
        }
    }

    private static void moveWithRetry(Path source, Path target) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { Files.move(source, target); return; }
            catch (java.io.IOException failure) {
                if (attempt == 29) throw failure;
                Thread.sleep(1000);
            }
        }
    }
}
