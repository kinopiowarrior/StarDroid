package org.openbw.androidtest;

import java.nio.charset.Charset;
import java.io.File;
import java.util.Locale;

/** The archive's encoding and the player's UI language are independent. */
final class GameLanguage {
  static final Charset WESTERN = Charset.forName("windows-1252");
  static final Charset RUSSIAN = Charset.forName("windows-1251");
  static volatile boolean russianData;
  static Charset charset() { return russianData ? RUSSIAN : WESTERN; }
  static String decode(byte[] data) { return data == null ? "" : new String(data, charset()); }

  /** Detect the installed text, not the phone locale or the archive filename. */
  static String detect(byte[] table) {
    if (isRussian(table)) return "ru";
    if (table == null || table.length < 6 || table.length > 1024 * 1024) return "en";
    int count = (table[0] & 255) | (table[1] & 255) << 8;
    if (count < 100 || 2 + count * 2 > table.length) return "en";
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < Math.min(count, 228); i++) {
      int at = 2 + i * 2, p = (table[at] & 255) | (table[at + 1] & 255) << 8;
      if (p < 2 + count * 2 || p >= table.length) continue;
      int end = p; while (end < table.length && end - p < 200 && table[end] != 0) end++;
      names.append(new String(table, p, end - p, WESTERN)).append('\n');
    }
    String text = names.toString().toLowerCase(Locale.ROOT);
    String[][] markers = {
      {"es", "fantasma", "buitre", "goliat", "asedio", "crucero", "zángano", "hidralisco", "fanático", "sonda", "criadero", "superamo"},
      {"de", "feuerfresser", "belagerung", "kreuzer", "berserker", "goliath", "drohne", "brutstätte", "schleicher", "räuber"},
      {"fr", "fantôme", "vautour", "siège", "croiseur", "zélote", "dominant", "couveuse", "aiguille", "saboteur"}
    };
    String result = "en"; int best = 1;
    for (String[] language : markers) {
      int matches = 0;
      for (int i = 1; i < language.length; i++) if (text.contains(language[i])) matches++;
      if (matches > best) { best = matches; result = language[0]; }
    }
    return result;
  }

  static boolean isRussian(byte[] table) {
    if (table == null || table.length < 6 || table.length > 1024 * 1024) return false;
    int count = (table[0] & 255) | (table[1] & 255) << 8;
    if (count < 100 || 2 + count * 2 > table.length) return false;
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < Math.min(count, 228); i++) {
      int at = 2 + i * 2, p = (table[at] & 255) | (table[at + 1] & 255) << 8;
      if (p < 2 + count * 2 || p >= table.length) continue;
      int end = p; while (end < table.length && end - p < 200 && table[end] != 0) end++;
      names.append(new String(table, p, end - p, RUSSIAN)).append('\n');
    }
    String text = names.toString().toLowerCase(Locale.ROOT); int matches = 0;
    for (String word : new String[]{"морпех", "пехота", "призрак", "привидение", "трутень", "зерг", "терран", "зонд", "зилот", "гидралиск"})
      if (text.contains(word)) matches++;
    return matches >= 3;
  }

  static boolean installedRussian(File directory) {
    return installedLanguage(directory).equals("ru");
  }

  static String installedLanguage(File directory) {
    for (String name : new String[]{"patch_rt.mpq", "BROODAT.MPQ", "STARDAT.MPQ"}) {
      File archive = new File(directory, name);
      if (!archive.isFile()) continue;
      try {
        byte[] table = NativeBridge.archiveText(archive.getPath());
        if (table != null && table.length > 0) return detect(table);
      } catch (RuntimeException ignored) { }
    }
    return "en";
  }

  static int glyphCode(int code) {
    if (code < 128) return code;
    String letter = Character.toString((char) code);
    byte[] encoded = letter.getBytes(charset());
    return encoded.length == 1 && new String(encoded, charset()).equals(letter) ? encoded[0] & 255 : -1;
  }
}
