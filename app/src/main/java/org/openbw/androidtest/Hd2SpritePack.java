package org.openbw.androidtest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Private, extracted HD2 battlefield sprites. A single atomic pointer selects a complete generation. */
final class Hd2SpritePack {
  private static final long MAX_ZIP = 128L << 20;
  private static final long MAX_EXTRACTED = 128L << 20;
  private static final long MAX_FILE = 16L << 20;
  private static final int MAX_ENTRIES = 4096;

  interface Validator {
    int validate(File directory) throws IOException;
  }

  static final class Selection {
    final File directory;
    final boolean enabled;

    private Selection(File directory, boolean enabled) {
      this.directory = directory;
      this.enabled = enabled;
    }
  }

  static Selection selection(File root) {
    File pointer = new File(root, "current");
    if (pointer.isFile() && pointer.length() <= 80) {
      try {
        String[] lines = new String(Files.readAllBytes(pointer.toPath()), StandardCharsets.US_ASCII).split("\n", -1);
        if (lines.length == 2 && (lines[0].equals("classic") || lines[0].equals("hd2"))
            && lines[1].matches("pack-[A-Za-z0-9_-]{1,64}")) {
          File directory = new File(root, lines[1]);
          if (directory.isDirectory()) return new Selection(directory, lines[0].equals("hd2"));
        }
      } catch (IOException unavailable) {
        // A missing or unreadable selection starts the game with classic sprites.
      }
    }
    return new Selection(null, false);
  }

  static File current(File root) { return selection(root).directory; }

  static boolean enabled(File root) { return selection(root).enabled; }

  private static void select(File root, File directory, boolean enabled) throws IOException {
    File pending = new File(root, "pending-pointer");
    try {
      try (FileOutputStream out = new FileOutputStream(pending)) {
        String value = (enabled ? "hd2\n" : "classic\n") + directory.getName();
        out.write(value.getBytes(StandardCharsets.US_ASCII));
        out.getFD().sync();
      }
      Files.move(pending.toPath(), new File(root, "current").toPath(),
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      pending.delete();
    }
  }

  static synchronized void setEnabled(File root, boolean enabled) throws IOException {
    File directory = current(root);
    if (directory == null) throw new IOException("Import an HD2 sprite ZIP first.");
    select(root, directory, enabled);
  }

  // The renderer owns the supported image IDs. Import and UI use its same catalog.
  static Set<String> supportedFiles(int[] imageIds) {
    Set<String> files = new HashSet<>();
    for (int id : imageIds) {
      if (id < 0 || id > 999 || !files.add(String.format(Locale.ROOT, "main_%03d.anim", id)))
        throw new IllegalArgumentException("Invalid HD2 image catalog");
    }
    return Collections.unmodifiableSet(files);
  }

  static int installedCount(File root, Set<String> supported) {
    File directory = current(root);
    int count = 0;
    if (directory != null) for (String name : supported) if (new File(directory, name).isFile()) count++;
    return count;
  }

  private static String supportedName(String path, Set<String> supported) {
    final String prefix = "hd2/anim/";
    String normalized = path.toLowerCase(Locale.ROOT);
    if (!normalized.startsWith(prefix)) return null;
    String name = normalized.substring(prefix.length());
    return supported.contains(name) ? name : null;
  }

  private static void checkPath(String name) throws IOException {
    if (name.isEmpty() || name.length() > 512 || name.startsWith("/") || name.indexOf('\\') >= 0
        || name.indexOf(':') >= 0 || name.indexOf('\0') >= 0) throw new IOException("Unsafe ZIP path.");
    for (String component : name.split("/")) {
      if (component.equals("..") || component.equals(".")) throw new IOException("Unsafe ZIP path.");
    }
  }

  private static long copy(InputStream input, File target, long limit, CRC32 crc) throws IOException {
    byte[] buffer = new byte[65536];
    long size = 0;
    try (FileOutputStream output = new FileOutputStream(target)) {
      for (int n; (n = input.read(buffer)) != -1;) {
        size += n;
        if (size > limit) throw new IOException("HD2 ZIP or sprite exceeds its size limit.");
        output.write(buffer, 0, n);
        if (crc != null) crc.update(buffer, 0, n);
      }
      if (crc != null) output.getFD().sync();
    }
    return size;
  }

  private static int u16(byte[] b, int at) {
    return (b[at] & 255) | ((b[at + 1] & 255) << 8);
  }

  private static long u32(byte[] b, int at) {
    return u16(b, at) | ((long)u16(b, at + 2) << 16);
  }

  // Bound the central directory before ZipFile allocates its per-entry name/index tables.
  private static void checkDirectory(File archive) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(archive, "r")) {
      byte[] tail = new byte[(int)Math.min(65557L, f.length())];
      long start = f.length() - tail.length;
      f.seek(start);
      f.readFully(tail);
      for (int at = tail.length - 22; at >= 0; at--) {
        if (u32(tail, at) != 0x06054b50L || at + 22 + u16(tail, at + 20) != tail.length) continue;
        int entries = u16(tail, at + 10);
        long directorySize = u32(tail, at + 12), directoryOffset = u32(tail, at + 16);
        if (u32(tail, at + 4) != 0 || entries > MAX_ENTRIES || entries != u16(tail, at + 8)
            || directorySize > (2L << 20) || directoryOffset + directorySize != start + at)
          throw new IOException("Unsupported or oversized ZIP directory.");
        return;
      }
    }
    throw new IOException("Incomplete or invalid ZIP file.");
  }

  static synchronized int install(File root, InputStream input, Set<String> supported, Validator validator) throws IOException {
    Files.createDirectories(root.toPath());
    File previous = current(root);
    // Clean up any inactive generation left by interruption. Only importer-owned names are touched.
    File[] stale = root.listFiles();
    if (stale == null) throw new IOException("Cannot open sprite storage.");
    for (File file : stale) {
      if (file.getName().startsWith("pack-") && !file.equals(previous)) removeGeneration(file);
    }
    File archive = new File(root, "pending.zip");
    File stage = Files.createTempDirectory(root.toPath(), "pack-").toFile();
    boolean committed = false;
    try {
      copy(input, archive, MAX_ZIP, null);
      checkDirectory(archive);
      Map<String, ZipEntry> accepted = new LinkedHashMap<>();
      long extractedSize = 0;
      try (ZipFile zip = new ZipFile(archive)) {
        if (zip.size() > MAX_ENTRIES) throw new IOException("Too many files in HD2 ZIP.");
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          checkPath(entry.getName());
          String name = supportedName(entry.getName(), supported);
          if (name == null || entry.isDirectory()) continue;
          if (accepted.put(name, entry) != null) throw new IOException("Duplicate sprite: " + name);
          if (entry.getSize() <= 0 || entry.getSize() > MAX_FILE)
            throw new IOException("Empty or oversized sprite: " + name);
          extractedSize += entry.getSize();
          if (extractedSize > MAX_EXTRACTED) throw new IOException("Extracted HD2 sprites exceed the pack size limit.");
        }
        for (Map.Entry<String, ZipEntry> item : accepted.entrySet()) {
          String name = item.getKey();
          ZipEntry entry = item.getValue();
          CRC32 crc = new CRC32();
          try (InputStream in = zip.getInputStream(entry)) {
            long actual = copy(in, new File(stage, name), entry.getSize(), crc);
            if (actual != entry.getSize() || crc.getValue() != entry.getCrc())
              throw new IOException("Damaged sprite: " + name);
          }
        }
      }
      if (accepted.isEmpty()) throw new IOException("No supported sprites found under HD2/anim/.");
      if (validator.validate(stage) != accepted.size()) throw new IOException("Invalid or unsupported HD2 sprite data.");
      select(root, stage, true);
      committed = true;
      if (previous != null) removeGeneration(previous);
      return accepted.size();
    } finally {
      archive.delete();
      if (!committed) removeGeneration(stage);
    }
  }

  private static void removeGeneration(File directory) {
    // Children are generated flat filenames; never traverse an archive-controlled tree.
    if (!Files.isSymbolicLink(directory.toPath())) {
      File[] members = directory.listFiles((parent, name) -> name.matches("main_[0-9]{3}\\.anim"));
      if (members != null) for (File member : members) member.delete();
    }
    directory.delete();
  }

  private Hd2SpritePack() {}
}
