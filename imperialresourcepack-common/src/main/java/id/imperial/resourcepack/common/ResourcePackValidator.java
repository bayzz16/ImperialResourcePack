package id.imperial.resourcepack.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourcePackValidator {
  public ValidationResult validate(Path file, boolean requireMcmeta, long maxSizeBytes) {
    if (!Files.isRegularFile(file) || !file.getFileName().toString().toLowerCase().endsWith(".zip")) return ValidationResult.invalid("Resource pack must be a .zip file.");
    try {
      long size = Files.size(file); if (size > maxSizeBytes) return ValidationResult.invalid("Resource pack exceeds configured maximum size.");
      boolean mcmeta = false;
      try (ZipFile zip = new ZipFile(file.toFile())) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          String name = entries.nextElement().getName().replace('\\','/');
          if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.equals("..") || name.startsWith("../") || name.contains("/../")) return ValidationResult.invalid("ZIP contains unsafe path: " + name);
          if (name.equals("pack.mcmeta")) mcmeta = true;
        }
      }
      return requireMcmeta && !mcmeta ? ValidationResult.invalid("pack.mcmeta not found.") : ValidationResult.success();
    } catch (IOException e) { return ValidationResult.invalid("Resource pack ZIP is invalid: " + e.getMessage()); }
  }
  public record ValidationResult(boolean valid, String message) { public static ValidationResult success(){return new ValidationResult(true,"Valid resource pack.");} public static ValidationResult invalid(String m){return new ValidationResult(false,m);} }
}
