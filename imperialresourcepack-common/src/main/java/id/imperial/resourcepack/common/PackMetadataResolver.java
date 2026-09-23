package id.imperial.resourcepack.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Resolves a resource pack from metadata inside the ZIP before relying on its
 * filename. Existing packs already expose their supported Minecraft versions
 * in pack.mcmeta descriptions, so no rename is required.
 *
 * Optional future metadata is also supported through imperial-pack.json:
 * {
 *   "minecraft": ["1.21.9", "1.21.10"]
 * }
 */
public final class PackMetadataResolver {
  private static final long MAX_METADATA_BYTES = 128 * 1024L;
  private static final Pattern DESCRIPTION =
      Pattern.compile("\\\"description\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern VERSION_RANGE =
      Pattern.compile("(?<![0-9])((?:\\d+\\.\\d+(?:\\.\\d+)?))\\s*[-–—]\\s*((?:\\d+\\.\\d+(?:\\.\\d+)?))(?![0-9])");
  private static final Pattern VERSION_EXACT =
      Pattern.compile("(?<![0-9])((?:\\d+\\.\\d+(?:\\.\\d+)?))(?![0-9])");

  private PackMetadataResolver() {}

  public static Path find(Path directory, String requestedVersion) {
    MinecraftVersion requested = MinecraftVersion.parse(requestedVersion);
    if (requested == null) return null;

    try {
      List<Candidate> matches = new ArrayList<>();
      for (Path file : listZipFiles(directory)) {
        Metadata metadata = read(file);
        if (metadata == null) continue;
        Match match = metadata.match(requested);
        if (match != null) matches.add(new Candidate(file, match));
      }

      return matches.stream()
          .sorted(Comparator
              .comparingInt((Candidate c) -> c.match().priority()).reversed()
              .thenComparingLong(c -> c.match().span())
              .thenComparing(c -> c.file().toString()))
          .map(Candidate::file)
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private static Metadata read(Path file) {
    try (ZipFile zip = new ZipFile(file.toFile())) {
      String custom = readEntry(zip, "imperial-pack.json");
      if (custom != null) {
        List<Range> ranges = parseVersions(custom);
        if (!ranges.isEmpty()) return new Metadata(ranges);
      }

      String mcmeta = readEntry(zip, "pack.mcmeta");
      if (mcmeta == null) return null;

      Matcher description = DESCRIPTION.matcher(mcmeta);
      if (!description.find()) return null;

      String text = unescapeJson(description.group(1));
      List<Range> ranges = parseVersions(text);
      return ranges.isEmpty() ? null : new Metadata(ranges);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static List<Range> parseVersions(String text) {
    List<Range> ranges = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    Matcher rangeMatcher = VERSION_RANGE.matcher(text);
    while (rangeMatcher.find()) {
      MinecraftVersion start = MinecraftVersion.parse(rangeMatcher.group(1));
      MinecraftVersion end = MinecraftVersion.parse(rangeMatcher.group(2));
      if (start != null && end != null && start.compareTo(end) <= 0) {
        String key = start + "-" + end;
        if (seen.add(key)) ranges.add(new Range(start, end));
      }
    }

    Matcher exactMatcher = VERSION_EXACT.matcher(text);
    while (exactMatcher.find()) {
      MinecraftVersion version = MinecraftVersion.parse(exactMatcher.group(1));
      if (version == null) continue;
      boolean alreadyCovered = ranges.stream().anyMatch(r -> r.contains(version));
      if (!alreadyCovered && seen.add(version.toString())) ranges.add(new Range(version, version));
    }
    return ranges;
  }

  private static String readEntry(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    if (entry == null || entry.isDirectory() || entry.getSize() > MAX_METADATA_BYTES) return null;
    try (InputStream in = zip.getInputStream(entry)) {
      byte[] bytes = in.readNBytes((int) MAX_METADATA_BYTES + 1);
      if (bytes.length > MAX_METADATA_BYTES) return null;
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  private static String unescapeJson(String value) {
    return value.replace("\\\"", "\"").replace("\\\\\", "\\");
  }

  private static List<Path> listZipFiles(Path directory) throws IOException {
    if (Files.notExists(directory)) return List.of();
    try (var stream = Files.walk(directory)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    }
  }

  private record Candidate(Path file, Match match) {}

  private record Metadata(List<Range> ranges) {
    Match match(MinecraftVersion requested) {
      return ranges.stream()
          .filter(r -> r.contains(requested))
          .map(r -> new Match(r.exact() ? 3 : 2, r.span()))
          .min(Comparator.comparingInt(Match::priority).reversed().thenComparingLong(Match::span))
          .orElse(null);
    }
  }

  private record Match(int priority, long span) {}

  private record Range(MinecraftVersion start, MinecraftVersion end) {
    boolean exact() { return start.compareTo(end) == 0; }
    boolean contains(MinecraftVersion version) {
      return start.compareTo(version) <= 0 && end.compareTo(version) >= 0;
    }
    long span() {
      return ((long) end.major() - start.major()) * 1_000_000L
          + ((long) end.minor() - start.minor()) * 1_000L
          + end.patch() - start.patch();
    }
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
}
