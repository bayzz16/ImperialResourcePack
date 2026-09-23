package id.imperial.resourcepack.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;

public final class ResourcePackManager {
  private final Logger logger;
  private final ResourcePackValidator validator = new ResourcePackValidator();
  private volatile ActivePack active;
  private String lastScanNotice = "";

  public ResourcePackManager(Logger logger) { this.logger = logger; }
  public ActivePack active() { return active; }

  public synchronized ActivePack scan(Path directory, ResourcePackConfig config) {
    try {
      Files.createDirectories(directory);
      List<Path> zips = listZipFiles(directory);
      Path selected = null;
      if (!config.activePack().isBlank()) {
        selected = safeChild(directory, config.activePack());
        if (selected == null || !Files.isRegularFile(selected)) {
          selected = findUniquePack(directory, config.activePack());
        }
        if (selected == null || !Files.isRegularFile(selected)) {
          notifyScan("configured-missing",
              "[ImperialResourcePack] active-pack points to a missing file: " + config.activePack()
                  + ". Use the relative path from packs/ if the ZIP is inside a subfolder.");
          return active;
        }
      } else if (zips.size() == 1) {
        selected = zips.getFirst();
      } else if (zips.size() > 1) {
        notifyScan("multiple",
            "[ImperialResourcePack] Multiple ZIP files found; set active-pack explicitly. "
                + "Version routing can still select mapped packs automatically.");
        return active;
      } else {
        notifyScan("empty",
            "[ImperialResourcePack] No resource pack ZIP found. Resource-pack delivery is idle until a pack is added.");
        return active;
      }
      // Keep activation failures stateful too, so auto-reload cannot spam the console.
      ActivationResult result = activate(selected, directory, config);
      if (!result.success()) {
        notifyScan("activation-failed", "[ImperialResourcePack] ERROR: " + result.message());
      } else {
        lastScanNotice = "";
      }
      return active;
    } catch (IOException e) {
      logger.severe("[ImperialResourcePack] Cannot scan packs: " + e.getMessage());
      return active;
    }
  }

  public synchronized ActivationResult activate(Path selected, Path directory, ResourcePackConfig config) {
    ActivationResult result = prepare(selected, directory, config);
    if (!result.success()) return result;
    ActivePack next = result.pack();
    if (active != null && active.file().equals(next.file())
        && active.size() == next.size() && active.modified() == next.modified()
        && active.sha1Hex().equals(next.sha1Hex())) {
      return ActivationResult.success(active);
    }
    active = next;
    logger.info("[ImperialResourcePack] Active pack: " + next.file().getFileName()
        + " (SHA-1: " + next.sha1Hex() + ")");
    return ActivationResult.success(next);
  }

  public ActivationResult prepare(Path selected, Path directory, ResourcePackConfig config) {
    try {
      if (selected == null) return ActivationResult.failure("Resource pack file was not found in packs-directory.");
      // The resolver may return a real file discovered recursively inside packs/.
      // Re-resolving that absolute path through safeChild() can incorrectly reject
      // valid nested/normalized paths on some filesystems. Validate the canonical
      // normalized path directly instead.
      Path root = directory.toAbsolutePath().normalize();
      Path file = selected.toAbsolutePath().normalize();
      if (!file.startsWith(root) || !Files.isRegularFile(file)) {
        return ActivationResult.failure("Resource pack file was not found in packs-directory.");
      }
      ResourcePackValidator.ValidationResult validation =
          validator.validate(file, config.requireMcmeta(), config.maxSizeBytes());
      if (!validation.valid()) return ActivationResult.failure(validation.message());
      long size = Files.size(file);
      long modified = Files.getLastModifiedTime(file).toMillis();
      byte[] hash = sha1(file);
      String hex = HexFormat.of().formatHex(hash);
      ActivePack next = new ActivePack(
          file, size, modified, hash, hex,
          UUID.nameUUIDFromBytes(("ImperialResourcePack:" + hex).getBytes(StandardCharsets.UTF_8)),
          validation
      );
      return ActivationResult.success(next);
    } catch (IOException e) {
      return ActivationResult.failure("Unable to prepare resource pack: " + e.getMessage());
    }
  }

  public List<Path> listValid(Path directory, ResourcePackConfig config) {
    try {
      List<Path> result = new ArrayList<>();
      for (Path file : listZipFiles(directory)) {
        if (validator.validate(file, config.requireMcmeta(), config.maxSizeBytes()).valid()) result.add(file);
      }
      return result;
    } catch (IOException e) {
      return List.of();
    }
  }

  public List<Path> listAll(Path directory) {
    try {
      return listZipFiles(directory);
    } catch (IOException e) {
      return List.of();
    }
  }

  private void notifyScan(String state, String message) {
    if (!state.equals(lastScanNotice)) {
      logger.warning(message);
      lastScanNotice = state;
    }
  }

  public Path resolveValid(Path directory, String filename, ResourcePackConfig config) {
    Path file = safeChild(directory, filename);
    if (file == null || !Files.isRegularFile(file)) return null;
    return validator.validate(file, config.requireMcmeta(), config.maxSizeBytes()).valid() ? file : null;
  }

  public String inventoryFingerprint(Path directory) {
    try {
      StringBuilder out = new StringBuilder();
      for (Path file : listZipFiles(directory)) {
        out.append(file.getFileName()).append('|')
            .append(Files.size(file)).append('|')
            .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
      }
      return out.toString();
    } catch (IOException e) {
      return "ERROR:" + e.getClass().getName();
    }
  }

  /**
   * Finds a pack for a client version.
   * Exact names use ResourcePack-<version>.zip.
   * Range names use ResourcePack-Java_<start>-<end>.zip and are inclusive.
   */
  public Path versionPack(Path directory, String version) {
    if (version == null || version.isBlank()) return null;

    // Prefer metadata inside the ZIP. This keeps routing independent from
    // filenames and allows future packs to use any filename.
    Path metadata = PackMetadataResolver.find(directory, version);
    if (metadata != null && Files.isRegularFile(metadata)) return metadata;

    Optional<SorterResolver.Route> routed = SorterResolver.resolve(version);
    if (routed.isPresent()) {
      String routedFile = routed.get().file();
      Path mapped = safeChild(directory, routedFile);
      if (mapped != null && Files.isRegularFile(mapped)) return mapped;

      // The sorter manifest may contain its original subfolder layout while
      // server owners commonly keep all ZIPs directly under packs/. If the
      // exact routed path is absent, safely resolve the unique matching ZIP
      // by basename. This keeps per-player routing automatic without forcing
      // a specific folder structure.
      Path flattened = findUniquePack(directory, routedFile);
      if (flattened != null && Files.isRegularFile(flattened)) return flattened;
    }

    Path exact = safeChild(directory, "ResourcePack-" + version + ".zip");
    if (exact != null && Files.isRegularFile(exact)) return exact;

    MinecraftVersion requested = MinecraftVersion.parse(version);
    if (requested == null) return null;

    try {
      List<VersionRangePack> matches = new ArrayList<>();
      for (Path file : listZipFiles(directory)) {
        VersionRangePack range = VersionRangePack.parse(file);
        if (range != null && range.contains(requested)) matches.add(range);
      }
      return matches.stream()
          .sorted(Comparator.comparingLong(VersionRangePack::span)
              .thenComparing(r -> r.file().getFileName().toString()))
          .map(VersionRangePack::file)
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      logger.warning("[ImperialResourcePack] Cannot scan version-mapped packs: " + e.getMessage());
      return null;
    }
  }

  public List<VersionMapping> versionMappings(Path directory) {
    List<VersionMapping> result = new ArrayList<>();
    for (Path file : listAll(directory)) {
      VersionRangePack range = VersionRangePack.parse(file);
      if (range != null) result.add(new VersionMapping(range.label(), file));
      else {
        String exact = exactVersion(file);
        if (exact != null) result.add(new VersionMapping(exact, file));
      }
    }
    return result.stream()
        .sorted(Comparator.comparing(VersionMapping::label).thenComparing(v -> v.file().getFileName().toString()))
        .toList();
  }

  private static String exactVersion(Path file) {
    String name = file.getFileName().toString();
    if (!name.startsWith("ResourcePack-") || !name.endsWith(".zip")) return null;
    String version = name.substring("ResourcePack-".length(), name.length() - ".zip".length());
    return MinecraftVersion.parse(version) != null ? version : null;
  }

  private record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
    static MinecraftVersion parse(String value) {
      String[] parts = value.trim().split("\\.");
      if (parts.length < 2 || parts.length > 3) return null;
      try {
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        if (major < 0 || minor < 0 || patch < 0) return null;
        return new MinecraftVersion(major, minor, patch);
      } catch (NumberFormatException e) {
        return null;
      }
    }

    @Override public int compareTo(MinecraftVersion other) {
      int c = Integer.compare(major, other.major);
      if (c != 0) return c;
      c = Integer.compare(minor, other.minor);
      return c != 0 ? c : Integer.compare(patch, other.patch);
    }

    @Override public String toString() {
      return major + "." + minor + (patch == 0 ? "" : "." + patch);
    }
  }

  private record VersionRangePack(Path file, MinecraftVersion start, MinecraftVersion end) {
    private static final java.util.regex.Pattern RANGE = java.util.regex.Pattern.compile(
        "^ResourcePack(?:-[^_]+)?_(\\d+\\.\\d+(?:\\.\\d+)?)-(\\d+\\.\\d+(?:\\.\\d+)?)\\.zip$",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    static VersionRangePack parse(Path file) {
      var matcher = RANGE.matcher(file.getFileName().toString());
      if (!matcher.matches()) return null;
      MinecraftVersion start = MinecraftVersion.parse(matcher.group(1));
      MinecraftVersion end = MinecraftVersion.parse(matcher.group(2));
      if (start == null || end == null || start.compareTo(end) > 0) return null;
      return new VersionRangePack(file, start, end);
    }

    boolean contains(MinecraftVersion version) {
      return start.compareTo(version) <= 0 && end.compareTo(version) >= 0;
    }

    long span() {
      return ((long) end.major() - start.major()) * 1_000_000L
          + ((long) end.minor() - start.minor()) * 1_000L
          + end.patch() - start.patch();
    }

    String label() { return start + "-" + end; }
  }

  public record VersionMapping(String label, Path file) {}

  public ResourcePackValidator.ValidationResult validate(Path file, ResourcePackConfig config) {
    return validator.validate(file, config.requireMcmeta(), config.maxSizeBytes());
  }

  public ResourcePackValidator.ValidationResult validateActive(ResourcePackConfig config) {
    ActivePack p = active;
    return p == null
        ? ResourcePackValidator.ValidationResult.invalid("No active resource pack.")
        : validate(p.file(), config);
  }

  private static List<Path> listZipFiles(Path directory) throws IOException {
    Files.createDirectories(directory);
    try (var stream = Files.walk(directory)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
          .sorted(Comparator.comparing(p -> directory.toAbsolutePath().normalize()
              .relativize(p.toAbsolutePath().normalize()).toString()))
          .toList();
    }
  }

  private static Path findUniquePack(Path directory, String configured) {
    String normalized = configured.replace('\\', '/');
    String wanted = Path.of(normalized).getFileName().toString();
    try (var stream = Files.walk(directory)) {
      List<Path> matches = stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().equals(wanted))
          .toList();
      return matches.size() == 1 ? matches.getFirst() : null;
    } catch (IOException e) {
      return null;
    }
  }

  private static Path safeChild(Path directory, String name) {
    if (name == null || name.isBlank()) return null;
    Path root = directory.toAbsolutePath().normalize();
    Path file = directory.resolve(name.replace('\\', '/')).normalize();
    return file.startsWith(root) ? file : null;
  }

  private static byte[] sha1(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[8192];
      try (InputStream in = Files.newInputStream(file)) {
        for (int n; (n = in.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
      }
      return digest.digest();
    } catch (Exception e) {
      throw new IOException("Unable to calculate SHA-1", e);
    }
  }

  public record ActivationResult(boolean success, String message, ActivePack pack) {
    static ActivationResult success(ActivePack p) { return new ActivationResult(true, "Activated.", p); }
    static ActivationResult failure(String m) { return new ActivationResult(false, m, null); }
  }
}
