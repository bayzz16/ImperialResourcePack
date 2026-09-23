package id.imperial.resourcepack.common;

import static org.junit.jupiter.api.Assertions.*;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class PackMetadataResolverTest {
  @Test
  void routesByPackMcmetaDescriptionEvenWhenFilenameHasNoVersion() throws Exception {
    Path dir = Files.createTempDirectory("irp-metadata");
    try {
      Path pack = dir.resolve("ImperialGUI-NewName.zip");
      writePack(pack, """
          {
            "pack": {
              "pack_format": 69,
              "description": "Imperial X SOL GUI V11 (1.21.9-1.21.10)"
            }
          }
          """);

      assertEquals(pack, PackMetadataResolver.find(dir, "1.21.9"));
      assertEquals(pack, PackMetadataResolver.find(dir, "1.21.10"));
      assertNull(PackMetadataResolver.find(dir, "1.21.11"));
    } finally {
      deleteTree(dir);
    }
  }

  @Test
  void exactMetadataBeatsBroaderMetadataRange() throws Exception {
    Path dir = Files.createTempDirectory("irp-metadata-priority");
    try {
      Path broad = dir.resolve("random-a.zip");
      Path exact = dir.resolve("random-b.zip");
      writePack(broad, """
          {"pack":{"description":"Imperial GUI 1.21.6-1.21.10"}}
          """);
      writePack(exact, """
          {"pack":{"description":"Imperial GUI 1.21.9"}}
          """);

      assertEquals(exact, PackMetadataResolver.find(dir, "1.21.9"));
      assertEquals(broad, PackMetadataResolver.find(dir, "1.21.10"));
    } finally {
      deleteTree(dir);
    }
  }

  private static void writePack(Path file, String mcmeta) throws Exception {
    try (OutputStream out = Files.newOutputStream(file);
         ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("pack.mcmeta"));
      zip.write(mcmeta.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }

  private static void deleteTree(Path root) throws Exception {
    try (var stream = Files.walk(root)) {
      stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
        try { Files.deleteIfExists(path); } catch (Exception e) { throw new RuntimeException(e); }
      });
    }
  }
}
