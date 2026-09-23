package id.imperial.resourcepack.common;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class SorterResolverTest {
  private static final List<String> ROUTES = List.of(
      "1.7.10","1.8.9","1.9","1.10.2","1.11","1.12.2","1.13","1.13.2",
      "1.14","1.14.4","1.15","1.16.1","1.16.2","1.16.5","1.17","1.17.1",
      "1.18","1.18.2","1.19","1.19.2","1.19.3","1.19.4","1.20","1.20.1",
      "1.20.2","1.20.3","1.20.4","1.20.5","1.20.6","1.21","1.21.1","1.21.2",
      "1.21.3","1.21.4","1.21.5","1.21.6","1.21.7","1.21.8","1.21.9","1.21.10",
      "1.21.11","26.1","26.1.2","26.2","26.3"
  );

  @Test
  void all27DeclaredRoutesResolveAtLeastOneRepresentativeVersion() {
    assertEquals(27, SorterResolver.routes().size());
    for (SorterResolver.Route route : SorterResolver.routes()) {
      String[] p = route.range().split("-", 2);
      assertTrue(SorterResolver.resolve(p[0]).isPresent(), "missing start: " + route.range());
      String end = p.length == 1 ? p[0] : p[1];
      assertTrue(SorterResolver.resolve(end).isPresent(), "missing end: " + route.range());
    }
  }

  @Test
  void representativeVersionsMapToExpectedRoute() {
    Map<String,String> expected = Map.ofEntries(
      Map.entry("1.8.9","1.7.10-1.8.9"),
      Map.entry("1.14.4","1.14-1.14.4"),
      Map.entry("1.20.4","1.20.3-1.20.4"),
      Map.entry("1.21.1","1.21-1.21.1"),
      Map.entry("1.21.4","1.21.4"),
      Map.entry("1.21.8","1.21.7-1.21.8"),
      Map.entry("1.21.9","1.21.9-1.21.10"),
      Map.entry("1.21.10","1.21.9-1.21.10"),
      Map.entry("1.21.11","1.21.11"),
      Map.entry("26.2","26.2"),
      Map.entry("26.3","26.3")
    );
    expected.forEach((version, route) ->
        assertEquals(route, SorterResolver.resolve(version).orElseThrow().range(), version));
  }

  @Test
  void resolveValidSupportsNestedBasenameAndRejectsDuplicates() throws Exception {
    Path root = Files.createTempDirectory("irp-resolve");
    try {
      Path nested = Files.createDirectories(root.resolve("nested/gui"));
      Path pack = nested.resolve("GUI(1.21.9-1.21.10).zip");
      writeValidPack(pack);
      ResourcePackManager manager = new ResourcePackManager(Logger.getLogger("test"));
      ResourcePackConfig config = config();
      assertEquals(pack.toAbsolutePath().normalize(),
          manager.resolveValid(root, pack.getFileName().toString(), config).toAbsolutePath().normalize());

      Path duplicateDir = Files.createDirectories(root.resolve("other"));
      writeValidPack(duplicateDir.resolve(pack.getFileName()));
      assertNull(manager.resolveValid(root, pack.getFileName().toString(), config));

      Files.delete(duplicateDir.resolve(pack.getFileName()));
      assertNotNull(manager.resolveValid(root, pack.getFileName().toString(), config));
    } finally {
      deleteTree(root);
    }
  }

  @Test
  void validatorRequiresPackMcmetaAtZipRoot() throws Exception {
    Path root = Files.createTempDirectory("irp-mcmeta");
    try {
      Path nestedMeta = root.resolve("nested-meta.zip");
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(nestedMeta))) {
        out.putNextEntry(new ZipEntry("folder/pack.mcmeta"));
        out.write("{}".getBytes());
      }
      ResourcePackValidator.ValidationResult r =
          new ResourcePackValidator().validate(nestedMeta, true, 1024 * 1024);
      assertFalse(r.valid());
      assertTrue(r.message().toLowerCase(Locale.ROOT).contains("pack.mcmeta"));
    } finally {
      deleteTree(root);
    }
  }

  @Test
  void protocolRepresentativesCoverRequestedModernClients() {
    assertEquals(List.of("1.21.1","1.21"), MinecraftProtocolVersions.versionsFor(767));
    assertEquals(List.of("1.21.4"), MinecraftProtocolVersions.versionsFor(769));
    assertEquals(List.of("1.21.8","1.21.7"), MinecraftProtocolVersions.versionsFor(772));
    assertEquals(List.of("1.21.10","1.21.9"), MinecraftProtocolVersions.versionsFor(773));
    assertEquals(List.of("1.21.11"), MinecraftProtocolVersions.versionsFor(774));
    assertEquals(List.of("26.1.2","26.1"), MinecraftProtocolVersions.versionsFor(775));
    assertEquals(List.of("26.2"), MinecraftProtocolVersions.versionsFor(776));
    assertEquals(List.of("26.3"), MinecraftProtocolVersions.versionsFor(777));
  }

  private static ResourcePackConfig config() {
    return new ResourcePackConfig(true, "packs", "", true, false, "<gray>pack</gray>", 0, false,
        true, "0.0.0.0", 8199, "/pack", "http://localhost:8199/pack", false,
        true, 16 * 1024 * 1024, true, false, 2000, true, true, Map.of());
  }

  private static void writeValidPack(Path file) throws Exception {
    Files.createDirectories(file.getParent());
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("pack.mcmeta"));
      out.write("{\"pack\":{\"description\":\"test\"}}".getBytes());
      out.closeEntry();
    }
  }

  private static void deleteTree(Path root) throws Exception {
    try (var stream = Files.walk(root)) {
      stream.sorted((a,b) -> Integer.compare(b.getNameCount(), a.getNameCount())).forEach(p -> {
        try { Files.deleteIfExists(p); } catch (Exception e) { throw new RuntimeException(e); }
      });
    }
  }
}
