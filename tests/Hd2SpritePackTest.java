package org.openbw.androidtest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Exercise the Java installer against a caller-owned extracted HD2 ZIP. */
public final class Hd2SpritePackTest {
    private static final String[] PREVIEW2_IDS = {"000", "005", "013", "122", "228"};
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

    private static int install(File root, File zip, Set<String> supported, Hd2SpritePack.Validator validator) throws IOException {
        try (InputStream bytes = Files.newInputStream(zip.toPath())) {
            return Hd2SpritePack.install(root, bytes, supported, validator);
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
        String[] ids = args[3].split(",");
        int[] catalogIds = Arrays.stream(ids).mapToInt(Integer::parseInt).toArray();
        Set<String> supported = Hd2SpritePack.supportedFiles(catalogIds);
        File root = new File(args[0]);
        File originalZip = new File(args[1]);
        File fixtures = new File(args[2]);
        require(Hd2SpritePack.current(root) == null && Hd2SpritePack.installedCount(root, supported) == 0,
                "A fresh install already has HD2 data");
        try {
            Hd2SpritePack.setEnabled(root, true);
            throw new AssertionError("Enabled sprites on an empty install");
        } catch (IOException expected) {
            require(!Hd2SpritePack.enabled(root), "Empty store retained an enabled flag");
        }
        try (ZipFile original = new ZipFile(originalZip)) {
            // An on-disk preview 2 generation, using its exact pointer format and five files.
            File legacy = new File(root, "pack-preview2");
            Files.createDirectory(legacy.toPath());
            for (String id : PREVIEW2_IDS) {
                String file = name(id);
                try (InputStream input = original.getInputStream(original.getEntry("HD2/anim/" + file))) {
                    Files.copy(input, new File(legacy, file).toPath());
                }
            }
            Files.write(new File(root, "current").toPath(), "hd2\npack-preview2".getBytes(StandardCharsets.US_ASCII));
            require(Hd2SpritePack.installedCount(root, supported) == PREVIEW2_IDS.length,
                    "Preview 2 pack count changed on upgrade");
            checkNextLaunch(root, legacy, true);
            verifyGeneration(legacy, original, PREVIEW2_IDS);
            try {
                install(root, new File(fixtures, "truncated.zip"), supported, stage -> 0);
                throw new AssertionError("Premature release of old pack");
            } catch (IOException expected) {
                checkNextLaunch(root, legacy, true);
                verifyGeneration(legacy, original, PREVIEW2_IDS);
            }
            int installed = install(root, originalZip, supported, stage -> {
                require(legacy.equals(Hd2SpritePack.current(root)), "Published replacement before validation");
                return verifyGeneration(stage, original, ids);
            });
            File first = Hd2SpritePack.current(root);
            require(installed == ids.length && first != null && Hd2SpritePack.installedCount(root, supported) == installed,
                    "Full sample ZIP did not install the expected catalog");
            require(!legacy.exists(), "Old validated generation was not retired after replacement");
            verifyGeneration(first, original, ids);
            checkNextLaunch(root, first, true);
            Hd2SpritePack.setEnabled(root, false);
            checkNextLaunch(root, first, false);
            require(Hd2SpritePack.installedCount(root, supported) == ids.length, "Disabling removed the pack");

            for (String rejected : Arrays.asList("unsupported", "traversal", "duplicate", "crc", "oversize", "truncated", "aggregate", "unmapped")) {
                try {
                    install(root, new File(fixtures, rejected + ".zip"), supported, stage -> {
                        throw new AssertionError("Invalid " + rejected + " ZIP reached native validation");
                    });
                    throw new AssertionError("Accepted " + rejected + " ZIP");
                } catch (IOException expected) {
                    if (rejected.equals("aggregate")) require(expected.getMessage().contains("pack size limit"),
                            "Did not reject uncompressed total before extraction");
                    require(first.equals(Hd2SpritePack.current(root)) && !Hd2SpritePack.enabled(root),
                            "Changed the active pack or mode after " + rejected);
                }
            }

            File subset = new File(fixtures, "subset.zip");
            try {
                install(root, subset, supported, stage -> { throw new IOException("Native decoder rejected image"); });
                throw new AssertionError("Committed a rejected generation");
            } catch (IOException expected) {
                require(first.equals(Hd2SpritePack.current(root)) && !Hd2SpritePack.enabled(root),
                        "Validation failure changed the installed pack or mode");
                verifyGeneration(first, original, ids);
            }
            // Simulate a filesystem failure during pointer commit, after successful validation.
            // A nonempty directory cannot be replaced by the marker's temporary output file.
            File obstruction = new File(root, "pending-pointer");
            Files.createDirectory(obstruction.toPath());
            File child = new File(obstruction, "in-use");
            Files.createFile(child.toPath());
            boolean[] validated = {false};
            try {
                install(root, subset, supported, stage -> { validated[0] = true; return 1; });
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
            int replacement = install(root, subset, supported, stage -> {
                require(first.equals(Hd2SpritePack.current(root)), "Published replacement before validation");
                return verifyGeneration(stage, original, "122");
            });
            File current = Hd2SpritePack.current(root);
            require(replacement == 1 && current != null && !current.equals(first)
                    && Hd2SpritePack.installedCount(root, supported) == 1, "Subset replacement did not commit");
            verifyGeneration(current, original, "122");
            checkNextLaunch(root, current, true);
            require(!first.exists(), "Full pack files leaked after importing partial replacement");
            File freshRoot = new File(fixtures, "empty-store");
            int freshCount = install(freshRoot, subset, supported, stage -> verifyGeneration(stage, original, "122"));
            require(freshCount == 1 && Hd2SpritePack.enabled(freshRoot), "Fresh installation failed");
        }
        System.out.println("PASS HD2 native catalog, preview 2 migration, real ZIP and replacement rollback (" + checks + " checks)");
    }
}
