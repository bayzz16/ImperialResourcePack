package id.imperial.resourcepack.common;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ResourcePackManagerVersionMappingTest {
  @Test
  void matchesInclusiveJavaRange() throws Exception {
    Path dir = Files.createTempDirectory("irp-version-range");
    try {
      Path range = Files.createFile(dir.resolve("ResourcePack-Java_1.21.6-1.21.8.zip"));
      Files.createFile(dir.resolve("ResourcePack-Java_1.21.9-1.21.10.zip"));
      ResourcePackManager manager = new ResourcePackManager(Logger.getLogger("test"));

      assertEquals(range, manager.versionPack(dir, "1.21.6"));
      assertEquals(range, manager.versionPack(dir, "1.21.7"));
      assertEquals(range, manager.versionPack(dir, "1.21.8"));
      assertNull(manager.versionPack(dir, "1.21.5"));
    } finally {
      deleteTree(dir);
    }
  }

  @Test
  void handlesNumericPatchOrderingAndExactMatch() throws Exception {
    Path dir = Files.createTempDirectory("irp-version-exact");
    try {
      Path range = Files.createFile(dir.resolve("ResourcePack-Java_1.21.6-1.21.10.zip"));
      Path exact = Files.createFile(dir.resolve("ResourcePack-1.21.9.zip"));
      ResourcePackManager manager = new ResourcePackManager(Logger.getLogger("test"));

      assertEquals(exact, manager.versionPack(dir, "1.21.9"));
      assertEquals(range, manager.versionPack(dir, "1.21.10"));
    } finally {
      deleteTree(dir);
    }
  }

  @Test
  void rejectsInvalidOrReversedRanges() throws Exception {
    Path dir = Files.createTempDirectory("irp-version-invalid");
    try {
      Files.createFile(dir.resolve("ResourcePack-Java_1.21.10-1.21.6.zip"));
      Files.createFile(dir.resolve("ResourcePack-Java_foo-1.21.8.zip"));
      ResourcePackManager manager = new ResourcePackManager(Logger.getLogger("test"));

      assertNull(manager.versionPack(dir, "1.21.7"));
    } finally {
      deleteTree(dir);
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
