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

  public ResourcePackManager(Logger logger) { this.logger = logger; }
  public ActivePack active() { return active; }

  public synchronized ActivePack scan(Path directory, ResourcePackConfig config) {
    try { Files.createDirectories(directory); }
    catch (IOException e) { logger.severe("[ImperialResourcePack] Cannot create packs directory: " + e.getMessage()); return active; }
    try {
      List<Path> zips = listZipFiles(directory);
      Path selected = null;
      if (!config.activePack().isBlank()) selected = safeChild(directory, config.activePack());
      else if (zips.size() == 1) selected = zips.getFirst();
      else if (zips.size() > 1) { logger.warning("[ImperialResourcePack] Multiple ZIP files found; set active-pack explicitly."); return active; }
      else { logger.warning("[ImperialResourcePack] No resource pack ZIP found in " + directory); return active; }
      ActivationResult result = activate(selected, directory, config);
      if (!result.success()) logger.severe("[ImperialResourcePack] ERROR: " + result.message());
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
    if (active != null && active.file().equals(next.file()) && active.size() == next.size() && active.modified() == next.modified()) {
      return ActivationResult.success(active);
    }
    active = next;
    logger.info("[ImperialResourcePack] Active pack: " + next.file().getFileName() + " (SHA-1: " + next.sha1Hex() + ")");
    return ActivationResult.success(next);
  }

  /** Validates and prepares a pack without changing the currently active pack. */
  public ActivationResult prepare(Path selected, Path directory, ResourcePackConfig config) {
    try {
      if (selected == null) return ActivationResult.failure("Resource pack file was not found in packs-directory.");
      Path file = safeChild(directory, selected.getFileName().toString());
      if (file == null || !Files.isRegularFile(file)) return ActivationResult.failure("Resource pack file was not found in packs-directory.");
      ResourcePackValidator.ValidationResult validation = validator.validate(file, config.requireMcmeta(), config.maxSizeBytes());
      if (!validation.valid()) return ActivationResult.failure(validation.message());
      long size = Files.size(file), modified = Files.getLastModifiedTime(file).toMillis();
      byte[] hash = sha1(file);
      String hex = HexFormat.of().formatHex(hash);
      ActivePack next = new ActivePack(file, size, modified, hash, hex,
          UUID.nameUUIDFromBytes(("ImperialResourcePack:" + hex).getBytes(StandardCharsets.UTF_8)), validation);
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
    } catch (IOException e) { return List.of(); }
  }

  public Path versionPack(Path directory, String version) {
    if (version == null || version.isBlank()) return null;
    Path root = directory.toAbsolutePath().normalize();
    Path candidate = directory.resolve("ResourcePack-" + version + ".zip").normalize();
    return candidate.getParent() != null && candidate.getParent().toAbsolutePath().normalize().equals(root)
        && Files.isRegularFile(candidate) ? candidate : null;
  }

  public ResourcePackValidator.ValidationResult validate(Path file, ResourcePackConfig config) {
    return validator.validate(file, config.requireMcmeta(), config.maxSizeBytes());
  }

  public ResourcePackValidator.ValidationResult validateActive(ResourcePackConfig config) {
    ActivePack p = active;
    return p == null ? ResourcePackValidator.ValidationResult.invalid("No active resource pack.") : validate(p.file(), config);
  }

  private static List<Path> listZipFiles(Path directory) throws IOException {
    try (var stream = Files.list(directory)) {
      return stream.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
    }
  }

  private static Path safeChild(Path directory, String name) {
    Path root = directory.toAbsolutePath().normalize(), file = directory.resolve(name).normalize();
    return file.getParent() != null && file.getParent().toAbsolutePath().normalize().equals(root) ? file : null;
  }

  private static byte[] sha1(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[8192];
      try (InputStream in = Files.newInputStream(file)) {
        for (int n; (n = in.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
      }
      return digest.digest();
    } catch (Exception e) { throw new IOException("Unable to calculate SHA-1", e); }
  }

  public record ActivationResult(boolean success, String message, ActivePack pack) {
    static ActivationResult success(ActivePack p) { return new ActivationResult(true, "Activated.", p); }
    static ActivationResult failure(String m) { return new ActivationResult(false, m, null); }
  }
}
