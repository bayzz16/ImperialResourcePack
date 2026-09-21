package id.imperial.resourcepack.common;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public record ResourcePackConfig(
    boolean enabled,
    String packsDirectory,
    String activePack,
    boolean deliveryEnabled,
    boolean required,
    String prompt,
    long sendDelayMs,
    boolean resendOnServerSwitch,
    boolean hostingEnabled,
    String bind,
    int port,
    String hostPath,
    String publicUrl,
    boolean requireMcmeta,
    long maxSizeBytes,
    boolean versionMapping,
    boolean autoReload,
    long autoReloadIntervalMs,
    boolean fallbackToActive,
    boolean statsEnabled,
    Map<String, String> profiles
) {
  public static ResourcePackConfig load(Path file) throws IOException {
    Map<String, String> v = new LinkedHashMap<>();
    String section = "";
    for (String raw : Files.readAllLines(file)) {
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) continue;
      if (!raw.startsWith(" ") && line.endsWith(":")) {
        section = line.substring(0, line.length() - 1);
        continue;
      }
      int colon = line.indexOf(':');
      if (colon < 1) continue;
      String key = (raw.startsWith(" ") ? section + "." : "") + line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();
      if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
        value = value.substring(1, value.length() - 1);
      }
      v.put(key, value);
    }

    Map<String, String> profiles = new LinkedHashMap<>();
    for (var entry : v.entrySet()) {
      if (entry.getKey().startsWith("profiles.") && !entry.getValue().isBlank()) {
        profiles.put(entry.getKey().substring("profiles.".length()), entry.getValue());
      }
    }

    return new ResourcePackConfig(
        bool(v, "enabled", true),
        get(v, "packs-directory", "packs"),
        get(v, "active-pack", ""),
        bool(v, "delivery.enabled", true),
        bool(v, "delivery.required", false),
        get(v, "delivery.prompt", "<gold>Imperial X SOL</gold> <gray>Resource Pack</gray>"),
        positive(v, "delivery.send-delay-ms", 1500),
        bool(v, "delivery.resend-on-server-switch", false),
        bool(v, "hosting.enabled", true),
        get(v, "hosting.bind", "0.0.0.0"),
        port(v),
        path(get(v, "hosting.path", "/pack")),
        get(v, "hosting.public-url", ""),
        bool(v, "validation.require-pack-mcmeta", true),
        positive(v, "validation.max-size-mb", 256) * 1024L * 1024L,
        bool(v, "version-mapping", false),
        bool(v, "auto-reload.enabled", true),
        positive(v, "auto-reload.interval-ms", 2000),
        bool(v, "version-mapping-fallback-to-active", true),
        bool(v, "stats.enabled", true),
        Map.copyOf(profiles)
    );
  }

  private static String get(Map<String, String> v, String k, String d) { return v.getOrDefault(k, d); }

  private static boolean bool(Map<String, String> v, String k, boolean d) {
    return Boolean.parseBoolean(get(v, k, String.valueOf(d)));
  }

  private static long positive(Map<String, String> v, String k, long d) {
    try {
      long n = Long.parseLong(get(v, k, ""));
      return n >= 0 ? n : d;
    } catch (NumberFormatException e) {
      return d;
    }
  }

  private static int port(Map<String, String> v) {
    long n = positive(v, "hosting.port", 8199);
    return n > 0 && n < 65536 ? (int) n : 8199;
  }

  private static String path(String p) { return p.startsWith("/") ? p : "/" + p; }
}
