package id.imperial.resourcepack.common;
import java.nio.file.Path;
import java.util.UUID;
public record ActivePack(Path file, long size, long modified, byte[] sha1, String sha1Hex, UUID id, ResourcePackValidator.ValidationResult validation) {}
