package com.bleudev.modsync.client.util;

import java.nio.file.*;
import java.util.Properties;

/** Run with assertions enabled; needs only the JDK. */
public final class PendingModInstallerTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            while (!Files.exists(Path.of(args[0]))) Thread.sleep(50);
            return;
        }
        Path root = Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")), "modsync-test-");
        Path success = Files.createDirectory(root.resolve("success"));
        Path old = Files.writeString(success.resolve("old.jar"), "old mod");
        Path source = Files.writeString(success.resolve("download.jar"), "new mod");
        Path target = success.resolve("new.jar");
        Path manifest = manifest(success, source, target, old);
        PendingModInstaller.install(manifest);
        check(!Files.exists(old) && Files.readString(target).equals("new mod"), "Update installed");
        check(Files.readString(success.resolve("backup/0.jar")).equals("old mod"), "Backup preserved");
        check(Files.exists(success.resolve("SUCCESS")), "Success marker written");

        Path failed = Files.createDirectory(root.resolve("rollback"));
        Path failedOld = Files.writeString(failed.resolve("old.jar"), "original");
        Path staged = Files.writeString(failed.resolve("download.jar"), "replacement");
        Path blocker = Files.writeString(failed.resolve("blocked"), "file, not directory");
        expectFailure(manifest(failed, staged, blocker.resolve("new.jar"), failedOld));
        check(Files.readString(failedOld).equals("original"), "Original restored after install failure");
        check(!Files.exists(failed.resolve("SUCCESS")), "Failure never marked successful");

        Path missing = Files.createDirectory(root.resolve("missing"));
        Path missingOld = Files.writeString(missing.resolve("old.jar"), "original");
        expectFailure(manifest(missing, missing.resolve("absent.jar"), missing.resolve("new.jar"), missingOld));
        check(Files.readString(missingOld).equals("original"), "Missing download leaves original untouched");

        Path waiting = Files.createDirectory(root.resolve("waiting"));
        Path waitOld = Files.writeString(waiting.resolve("old.jar"), "original");
        Path waitSource = Files.writeString(waiting.resolve("download.jar"), "replacement");
        Path waitTarget = waiting.resolve("new.jar");
        Path waitManifest = manifest(waiting, waitSource, waitTarget, waitOld);
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        Process parent = new ProcessBuilder(javaExecutable, "-cp", cp, PendingModInstallerTest.class.getName(), waiting.resolve("exit").toString()).start();
        Process installer = null;
        try {
            installer = new ProcessBuilder(javaExecutable, "-cp", cp, PendingModInstaller.class.getName(), Long.toString(parent.pid()), waitManifest.toString())
                .redirectErrorStream(true).redirectOutput(waiting.resolve("test.log").toFile()).start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(waiting.resolve("READY")) && System.nanoTime() < deadline) Thread.sleep(50);
            check(Files.exists(waiting.resolve("READY")), "Helper starts independently");
            check(Files.exists(waitOld) && !Files.exists(waitTarget), "Loaded mods unchanged while game runs");
            Files.createFile(waiting.resolve("exit"));
            check(installer.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && installer.exitValue() == 0, "Helper finishes after game exits");
            check(Files.readString(waitTarget).equals("replacement"), "Helper installs after parent exit");
        } finally {
            parent.destroyForcibly();
            if (installer != null && installer.isAlive()) installer.destroyForcibly();
        }
        System.out.println("All installer checks passed: " + root);
    }

    private static Path manifest(Path dir, Path source, Path target, Path old) throws Exception {
        Properties props = new Properties();
        props.setProperty("count", "1");
        props.setProperty("source.0", source.toString());
        props.setProperty("target.0", target.toString());
        props.setProperty("old.count", "1");
        props.setProperty("old.0", old.toString());
        Path file = dir.resolve("install.properties");
        try (var output = Files.newOutputStream(file)) { props.store(output, "test"); }
        return file;
    }

    private static void expectFailure(Path manifest) throws Exception {
        try { PendingModInstaller.install(manifest); }
        catch (java.io.IOException expected) { return; }
        throw new AssertionError("Expected install failure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
