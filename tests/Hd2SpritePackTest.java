package org.openbw.androidtest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Exercise the Java installer against a caller-owned extracted HD2 ZIP. */
public final class Hd2SpritePackTest {
    private static final String[] BODIES = {"000", "005", "013", "122", "228"};
    private static int checks;

    private static void require(boolean condition, String message) {
        ++checks;
        if (!condition) throw new AssertionError(message);
    }

    private static String name(String id) { return "main_" + id + ".anim"; }

    private static int verifyGeneration(File generation, ZipFile original, String... ids)
            throws IOException {
        Set<String> wanted = new HashSet<>();
        for (String id : ids) wanted.add(name(id));
        File[] installed = generation.listFiles((file, n) -> n.endsWith(".anim"));
        require(installed != null && installed.length == wanted.size(), "Wrong installed file count");
        for (File file : installed) {
            require(wanted.remove(file.getName()), "Unexpected installed image: " + file.getName());
            ZipEntry member = original.getEntry("HD2/anim/" + file.getName());
            require(member != null && member.getSize() == file.length(), "Wrong member length");
            byte[] actual = Files.readAllBytes(file.toPath());
            try (InputStream expected = original.getInputStream(member)) {
                byte[] block = new byte[64 * 1024];
                int offset = 0;
                for (int count; (count = expected.read(block)) != -1;) {
                    for (int i = 0; i < count; i++) {
                        if (actual[offset + i] != block[i]) throw new AssertionError("File bytes differ");
                    }
                    offset += count;
                }
                require(offset == actual.length, "Short member read");
            }
        }
        require(wanted.isEmpty(), "An expected image is missing");
        return installed.length;
    }

    private static int install(File root, File zip, Hd2SpritePack.Validator validator) throws IOException {
        try (InputStream bytes = Files.newInputStream(zip.toPath())) {
            return Hd2SpritePack.install(root, bytes, validator);
        }
    }

    private static void checkNextLaunch(File root, File expectedDirectory, boolean enabled) throws Exception {
        String java = new File(System.getProperty("java.home"), "bin/java").toString();
        Process fresh = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Hd2SpritePackTest.class.getName(), "--read", root.toString(), expectedDirectory.toString(),
                Boolean.toString(enabled)).inheritIO().start();
        require(fresh.waitFor() == 0, "Selection did not survive a new process");
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--read")) {
            Hd2SpritePack.Selection selection = Hd2SpritePack.selection(new File(args[1]));
            require(new File(args[2]).equals(selection.directory) && selection.enabled == Boolean.parseBoolean(args[3]),
                    "Fresh process read the wrong generation or mode");
            return;
        }
        File root = new File(args[0]);
        File originalZip = new File(args[1]);
        File fixtures = new File(args[2]);
        require(Hd2SpritePack.current(root) == null && Hd2SpritePack.installedCount(root) == 0,
                "A fresh install already has HD2 data");
        try {
            Hd2SpritePack.setEnabled(root, true);
            throw new AssertionError("Enabled sprites on an empty install");
        } catch (IOException expected) {
            require(!Hd2SpritePack.enabled(root), "Empty store retained an enabled flag");
        }
        try (ZipFile original = new ZipFile(originalZip)) {
            int installed = install(root, originalZip, stage -> {
                require(Hd2SpritePack.current(root) == null, "First import published before validation");
                return verifyGeneration(stage, original, BODIES);
            });
            File first = Hd2SpritePack.current(root);
            require(installed == 5 && first != null && Hd2SpritePack.installedCount(root) == 5,
                    "Real sample ZIP did not install the five supported bodies");
            verifyGeneration(first, original, BODIES);
            checkNextLaunch(root, first, true);
            Hd2SpritePack.setEnabled(root, false);
            checkNextLaunch(root, first, false);
            require(Hd2SpritePack.installedCount(root) == BODIES.length, "Disabling removed the pack");

            for (String rejected : Arrays.asList("unsupported", "traversal", "duplicate", "crc", "oversize", "truncated")) {
                try {
                    install(root, new File(fixtures, rejected + ".zip"), stage -> {
                        throw new AssertionError("Invalid " + rejected + " ZIP reached native validation");
                    });
                    throw new AssertionError("Accepted " + rejected + " ZIP");
                } catch (IOException expected) {
                    require(first.equals(Hd2SpritePack.current(root)) && !Hd2SpritePack.enabled(root),
                            "Changed the active pack or mode after " + rejected);
                }
            }

            File subset = new File(fixtures, "subset.zip");
            try {
                install(root, subset, stage -> { throw new IOException("Native decoder rejected image"); });
                throw new AssertionError("Committed a rejected generation");
            } catch (IOException expected) {
                require(first.equals(Hd2SpritePack.current(root)) && !Hd2SpritePack.enabled(root),
                        "Validation failure changed the installed pack or mode");
                verifyGeneration(first, original, BODIES);
            }
            // Simulate a filesystem failure during pointer commit, after successful validation.
            // A nonempty directory cannot be replaced by the marker's temporary output file.
            File obstruction = new File(root, "pending-pointer");
            Files.createDirectory(obstruction.toPath());
            File child = new File(obstruction, "in-use");
            Files.createFile(child.toPath());
            boolean[] validated = {false};
            try {
                install(root, subset, stage -> { validated[0] = true; return 1; });
                throw new AssertionError("Committed a pack after pointer write failure");
            } catch (IOException expected) {
                require(validated[0], "Simulated commit failure occurred at the wrong stage");
                checkNextLaunch(root, first, false);
            } finally {
                Files.delete(child.toPath());
                Files.delete(obstruction.toPath());
            }
            Hd2SpritePack.setEnabled(root, true);
            checkNextLaunch(root, first, true);
            Hd2SpritePack.setEnabled(root, false);
            int replacement = install(root, subset, stage -> {
                require(first.equals(Hd2SpritePack.current(root)), "Published replacement before validation");
                return verifyGeneration(stage, original, "122");
            });
            File current = Hd2SpritePack.current(root);
            require(replacement == 1 && current != null && !current.equals(first)
                    && Hd2SpritePack.installedCount(root) == 1, "Subset replacement did not commit");
            verifyGeneration(current, original, "122");
            checkNextLaunch(root, current, true);
        }
        System.out.println("PASS HD2 real ZIP, malformed inputs and replacement rollback (" + checks + " checks)");
    }
}
