package id.imperial.resourcepack.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class ResourcePackManager {
  private final Logger logger;
  private final ResourcePackValidator validator = new ResourcePackValidator();
  private final Map<Path, CacheEntry> cache = new ConcurrentHashMap<>();
  private volatile Map<String, Path> routeCache = Map.of();
  private volatile ActivePack active;
  private volatile String lastScanNotice = "";

  public ResourcePackManager(Logger logger) { this.logger = logger; }
  public ActivePack active() { return active; }

  private ResolutionResult resolvePath(Path directory, String requested, ResourcePackConfig config) {
    return resolveValidDetailed(directory, requested, config);
  }


  public synchronized ActivePack scan(Path directory, ResourcePackConfig config) {
    try {
      Files.createDirectories(directory);
      List<Path> zips = listZipFiles(directory);
      rebuildRouteCache(directory);
      Path selected = null;
      if (!config.activePack().isBlank()) {
        ResolutionResult resolution = resolvePath(directory, config.activePack(), config);
        if (!resolution.success()) {
          notifyScan("configured-missing:" + resolution.message(),
              "[ImperialResourcePack] active-pack could not be resolved: " + resolution.message());
          return active;
        }
        selected = resolution.file();
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

      ActivationResult result = activate(selected, directory, config);
      if (!result.success()) {
        notifyScan("activation-failed:" + result.message(), "[ImperialResourcePack] ERROR: " + result.message());
      } else {
        lastScanNotice = "";
      }
      return active;
    } catch (IOException e) {
      notifyScan("scan-io:" + e.getMessage(), "[ImperialResourcePack] Cannot scan packs: " + e.getMessage());
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
      Path file = secureFile(directory, selected);
      if (file == null) return ActivationResult.failure("Resource pack file was not found in packs-directory.");
      long size = Files.size(file);
      long modified = Files.getLastModifiedTime(file).toMillis();

      CacheEntry cached = cache.get(file);
      if (cached != null && cached.size == size && cached.modified == modified) {
        return cached.toResult();
      }

      ResourcePackValidator.ValidationResult validation =
          validator.validate(file, config.requireMcmeta(), config.maxSizeBytes());
      if (!validation.valid()) {
        CacheEntry entry = CacheEntry.invalid(size, modified, validation.message());
        cache.put(file, entry);
        return entry.toResult();
      }

      byte[] hash = sha1(file);
      String hex = HexFormat.of().formatHex(hash);
      ActivePack next = new ActivePack(
          file, size, modified, hash, hex,
          UUID.nameUUIDFromBytes(("ImperialResourcePack:" + hex).getBytes(StandardCharsets.UTF_8)),
          validation
      );
      CacheEntry entry = CacheEntry.valid(size, modified, next);
      cache.put(file, entry);
      return entry.toResult();
    } catch (IOException e) {
      return ActivationResult.failure("Unable to prepare resource pack: " + e.getMessage());
    }
  }

  public void prewarm(Path directory, ResourcePackConfig config) {
    for (Path file : listAll(directory)) {
      prepare(file, directory, config);
    }
  }

  public List<Path> listValid(Path directory, ResourcePackConfig config) {
    List<Path> result = new ArrayList<>();
    for (Path file : listAll(directory)) {
      if (prepare(file, directory, config).success()) result.add(file);
    }
    return result;
  }

  public List<Path> listAll(Path directory) {
    try {
      return listZipFiles(directory);
    } catch (IOException e) {
      return List.of();
    }
  }

  public ResolutionResult resolveValidDetailed(Path directory, String requested, ResourcePackConfig config) {
    if (requested == null || requested.isBlank()) return ResolutionResult.failure("requested filename is empty.");
    if (hasTraversal(requested)) return ResolutionResult.failure("path traversal is not allowed: " + requested);

    // 1. Exact relative path.
    Path exactRelative = safeChild(directory, requested);
    if (exactRelative != null && isZipFile(exactRelative)) {
      return validated(exactRelative, directory, config);
    }

    // 2. Exact filename in the root.
    String basename = basename(requested);
    Path rootFile = safeChild(directory, basename);
    if (rootFile != null && isZipFile(rootFile)) {
      return validated(rootFile, directory, config);
    }

    // 3. Recursive exact basename search.
    List<Path> matches = findPacksByBasename(directory, basename);
    if (matches.size() > 1) {
      return ResolutionResult.failure("duplicate basename '" + basename + "' found in "
          + matches.stream().map(Path::toString).sorted().toList());
    }
    if (matches.size() == 1) {
      return validated(matches.getFirst(), directory, config);
    }

    // 4. Version route lookup, if the input itself is a Minecraft version.
    if (looksLikeVersion(requested)) {
      Path routed = versionPack(directory, requested);
      if (routed != null) return validated(routed, directory, config);
    }

    return ResolutionResult.failure("no ZIP matched '" + requested + "'.");
  }

  public Path resolveValid(Path directory, String filename, ResourcePackConfig config) {
    ResolutionResult result = resolveValidDetailed(directory, filename, config);
    return result.success() ? result.file() : null;
  }

  public Path versionPack(Path directory, String version) {
    if (version == null || version.isBlank()) return null;

    // Route manifest is authoritative for the requested version.
    Optional<SorterResolver.Route> routed = SorterResolver.resolve(version);
    if (routed.isPresent()) {
      String routedFile = routed.get().file();
      Path indexed = routeCache.get(version);
      if (indexed != null && isZipFile(indexed)) return indexed;

      Path exact = safeChild(directory, routedFile);
      if (isZipFile(exact)) return exact;

      String basename = basename(routedFile);
      List<Path> byName = findPacksByBasename(directory, basename);
      if (byName.size() == 1) return byName.getFirst();
      if (byName.size() > 1) return null;
    }

    // Metadata is a fallback for renamed packs and future pack layouts.
    Path metadata = PackMetadataResolver.find(directory, version);
    if (metadata != null && isZipFile(metadata)) return metadata;

    // Legacy filename/range fallback.
    Path exact = safeChild(directory, "ResourcePack-" + version + ".zip");
    if (isZipFile(exact)) return exact;

    MinecraftVersion requested = MinecraftVersion.parse(version);
    if (requested == null) return null;

    List<VersionRangePack> matches = new ArrayList<>();
    for (Path file : listAll(directory)) {
      VersionRangePack range = VersionRangePack.parse(file);
      if (range != null && range.contains(requested)) matches.add(range);
    }
    if (matches.size() > 1) {
      matches.sort(Comparator.comparingLong(VersionRangePack::span)
          .thenComparing(r -> r.file().toString()));
      if (matches.getFirst().span() == matches.get(1).span()) return null;
    }
    return matches.stream()
        .sorted(Comparator.comparingLong(VersionRangePack::span)
            .thenComparing(r -> r.file().toString()))
        .map(VersionRangePack::file)
        .findFirst()
        .orElse(null);
  }

  public Optional<SorterResolver.Route> route(String version) {
    return SorterResolver.resolve(version);
  }

  public String expectedRouteFile(String version) {
    return SorterResolver.resolve(version).map(r -> basename(r.file())).orElse("");
  }

  /**
   * Rebuild the complete route index from one immutable inventory snapshot.
   * This avoids repeated Files.walk() calls returning different partial views
   * while ZIPs are being uploaded/extracted into the packs directory.
   */
  public synchronized void rebuildRouteCache(Path directory) {
    List<Path> zips = listAll(directory);
    Map<String, List<Path>> byBasename = new HashMap<>();
    for (Path file : zips) {
      byBasename.computeIfAbsent(normalizeBasename(file.getFileName().toString()),
          ignored -> new ArrayList<>()).add(file);
    }

    Map<String, Path> next = new HashMap<>();
    for (SorterResolver.Route route : SorterResolver.routes()) {
      String wanted = normalizeBasename(route.file());
      List<Path> matches = byBasename.getOrDefault(wanted, List.of());
      if (matches.size() == 1) {
        next.put(route.range(), matches.getFirst());
      }
    }
    routeCache = Map.copyOf(next);
  }

  public AuditReport audit(Path directory, ResourcePackConfig config) {
    List<Path> zips = listAll(directory);
    int invalid = 0, missingMcmeta = 0;
    List<String> failures = new ArrayList<>();
    for (Path file : zips) {
      ActivationResult prepared = prepare(file, directory, config);
      if (!prepared.success()) {
        invalid++;
        failures.add(file.getFileName() + ": " + prepared.message());
        if (prepared.message().toLowerCase(Locale.ROOT).contains("pack.mcmeta")) missingMcmeta++;
      }
    }

    int resolved = 0, missingRoutes = 0;
    List<String> routeFailures = new ArrayList<>();
    for (SorterResolver.Route route : SorterResolver.routes()) {
      Path file = versionPack(directory, route.range());
      if (file != null && prepare(file, directory, config).success()) {
        resolved++;
      } else {
        missingRoutes++;
        routeFailures.add(route.range() + " -> " + basename(route.file()));
      }
    }

    Map<String, List<Path>> byBasename = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (Path file : zips) byBasename.computeIfAbsent(basename(file.toString()), k -> new ArrayList<>()).add(file);
    int duplicates = 0;
    List<String> duplicateNames = new ArrayList<>();
    for (var e : byBasename.entrySet()) {
      if (e.getValue().size() > 1) {
        duplicates++;
        duplicateNames.add(e.getKey() + " -> " + e.getValue().stream().map(Path::toString).sorted().toList());
      }
    }

    return new AuditReport(zips.size(), SorterResolver.routes().size(), resolved, invalid,
        missingMcmeta, duplicates, missingRoutes, failures, routeFailures, duplicateNames);
  }

  public String inventoryFingerprint(Path directory) {
    StringBuilder out = new StringBuilder();
    for (Path file : listAll(directory)) {
      try {
        out.append(file.toAbsolutePath().normalize()).append('|')
            .append(Files.size(file)).append('|')
            .append(Files.getLastModifiedTime(file).toMillis()).append('\n');
      } catch (IOException ignored) { }
    }
    return out.toString();
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
        .sorted(Comparator.comparing(VersionMapping::label).thenComparing(v -> v.file().toString()))
        .toList();
  }

  public ResourcePackValidator.ValidationResult validate(Path file, ResourcePackConfig config) {
    return prepare(file, file.getParent(), config).success()
        ? ResourcePackValidator.ValidationResult.success()
        : prepare(file, file.getParent(), config).validation();
  }

  public ResourcePackValidator.ValidationResult validateActive(ResourcePackConfig config) {
    ActivePack p = active;
    return p == null
        ? ResourcePackValidator.ValidationResult.invalid("No active resource pack.")
        : validate(p.file(), config);
  }

  public int cacheSize() { return cache.size(); }

  private ResolutionResult validated(Path file, Path directory, ResourcePackConfig config) {
    ActivationResult result = prepare(file, directory, config);
    return result.success()
        ? ResolutionResult.success(result.pack())
        : ResolutionResult.failure(result.message());
  }

  private static boolean looksLikeVersion(String value) {
    return value.matches("\\d+\\.\\d+(?:\\.\\d+)?");
  }

  private static boolean hasTraversal(String value) {
    String normalized = value.replace('\\', '/');
    return normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
        || Arrays.asList(normalized.split("/")).contains("..");
  }

  private static String basename(String value) {
    String normalized = value.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
  }

  private static String normalizeBasename(String value) {
    return basename(value).trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isZipFile(Path file) {
    return file != null && Files.isRegularFile(file) && !Files.isSymbolicLink(file)
        && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
  }

  private static Path secureFile(Path directory, Path selected) {
    if (directory == null || selected == null) return null;
    try {
      Path root = directory.toAbsolutePath().normalize();
      if (Files.notExists(root)) Files.createDirectories(root);
      Path rootReal = root.toRealPath();
      Path file = selected.toAbsolutePath().normalize();
      if (!file.startsWith(root) || !isZipFile(file)) return null;
      Path real = file.toRealPath();
      if (!real.startsWith(rootReal) || !Files.isRegularFile(real)) return null;
      return real;
    } catch (IOException e) {
      return null;
    }
  }

  private static Path safeChild(Path directory, String name) {
    if (directory == null || name == null || name.isBlank() || hasTraversal(name)) return null;
    Path root = directory.toAbsolutePath().normalize();
    Path file = root.resolve(name.replace('\\', '/')).normalize();
    return file.startsWith(root) ? file : null;
  }

  private static List<Path> findPacksByBasename(Path directory, String wanted) {
    try (var stream = Files.walk(directory)) {
      return stream.filter(ResourcePackManager::isZipFile)
          .filter(p -> normalizeBasename(p.getFileName().toString()).equals(normalizeBasename(wanted)))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static List<Path> listZipFiles(Path directory) throws IOException {
    if (directory == null) return List.of();
    Files.createDirectories(directory);
    try (var stream = Files.walk(directory)) {
      return stream
          .filter(ResourcePackManager::isZipFile)
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  private static String exactVersion(Path file) {
    String name = file.getFileName().toString();
    if (!name.startsWith("ResourcePack-") || !name.endsWith(".zip")) return null;
    String version = name.substring("ResourcePack-".length(), name.length() - ".zip".length());
    return MinecraftVersion.parse(version) != null ? version : null;
  }

  private static byte[] sha1(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[64 * 1024];
      try (InputStream in = Files.newInputStream(file)) {
        for (int n; (n = in.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
      }
      return digest.digest();
    } catch (Exception e) {
      throw new IOException("Unable to calculate SHA-1", e);
    }
  }

  private void notifyScan(String state, String message) {
    if (!state.equals(lastScanNotice)) {
      logger.warning(message);
      lastScanNotice = state;
    }
  }

  private record CacheEntry(long size, long modified, boolean valid, String message, ActivePack pack) {
    static CacheEntry valid(long size, long modified, ActivePack pack) {
      return new CacheEntry(size, modified, true, "Valid resource pack.", pack);
    }
    static CacheEntry invalid(long size, long modified, String message) {
      return new CacheEntry(size, modified, false, message, null);
    }
    ActivationResult toResult() {
      return valid ? ActivationResult.success(pack) : ActivationResult.failure(message);
    }
  }

  public record ActivationResult(boolean success, String message, ActivePack pack) {
    static ActivationResult success(ActivePack pack) { return new ActivationResult(true, "Valid resource pack.", pack); }
    static ActivationResult failure(String message) { return new ActivationResult(false, message, null); }
    public ResourcePackValidator.ValidationResult validation() {
      return success
          ? ResourcePackValidator.ValidationResult.success()
          : ResourcePackValidator.ValidationResult.invalid(message);
    }
  }

  public record ResolutionResult(boolean success, String message, Path file) {
    static ResolutionResult success(ActivePack pack) { return new ResolutionResult(true, "Resolved.", pack.file()); }
    static ResolutionResult failure(String message) { return new ResolutionResult(false, message, null); }
  }

  public record AuditReport(int zipDetected, int routeCount, int resolvedRoutes, int invalidZip,
                            int missingMcmeta, int duplicateBasenames, int missingRoutes,
                            List<String> failures, List<String> routeFailures,
                            List<String> duplicates) {}

  public record VersionMapping(String label, Path file) {}

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
}
