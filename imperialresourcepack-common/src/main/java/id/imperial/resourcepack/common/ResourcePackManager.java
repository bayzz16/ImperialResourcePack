package id.imperial.resourcepack.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class ResourcePackManager {
  private final Logger logger; private final ResourcePackValidator validator = new ResourcePackValidator();
  private volatile ActivePack active;
  public ResourcePackManager(Logger logger) { this.logger = logger; }
  public ActivePack active() { return active; }
  public synchronized ActivePack scan(Path packsDirectory, ResourcePackConfig config) {
    logger.info("[ImperialResourcePack] Scanning resource packs...");
    try { Files.createDirectories(packsDirectory); } catch (IOException e) { logger.severe("[ImperialResourcePack] ERROR: Cannot create packs directory: " + e.getMessage()); active=null; return null; }
    List<Path> zips;
    try (var stream = Files.list(packsDirectory)) { zips = stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList(); }
    catch (IOException e) { logger.severe("[ImperialResourcePack] ERROR: Cannot scan packs: " + e.getMessage()); active=null; return null; }
    Path selected = null;
    if (!config.activePack().isBlank()) {
      selected = packsDirectory.resolve(config.activePack()).normalize();
      if (!selected.getParent().equals(packsDirectory.toAbsolutePath().normalize()) || !Files.isRegularFile(selected)) { logger.severe("[ImperialResourcePack] ERROR: Configured active-pack does not exist: " + config.activePack()); active=null; return null; }
    } else if (zips.size() == 1) selected = zips.getFirst();
    else if (zips.isEmpty()) { logger.warning("[ImperialResourcePack] No resource pack ZIP found in " + packsDirectory); active=null; return null; }
    else { logger.warning("[ImperialResourcePack] Multiple ZIP files found; set active-pack explicitly. No pack selected."); active=null; return null; }
    try {
      long size=Files.size(selected), modified=Files.getLastModifiedTime(selected).toMillis();
      ActivePack previous=active;
      if (previous != null && previous.file().equals(selected) && previous.size()==size && previous.modified()==modified) return previous;
      var result=validator.validate(selected, config.requireMcmeta(), config.maxSizeBytes());
      if (!result.valid()) { logger.severe("[ImperialResourcePack] ERROR: " + result.message()); active=null; return null; }
      byte[] hash=sha1(selected); String hex=java.util.HexFormat.of().formatHex(hash); UUID id=UUID.nameUUIDFromBytes(("ImperialResourcePack:"+hex).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      active=new ActivePack(selected,size,modified,hash,hex,id,result);
      logger.info("[ImperialResourcePack] Active pack: " + selected.getFileName()); logger.info("[ImperialResourcePack] SHA-1: " + hex); return active;
    } catch (IOException e) { logger.severe("[ImperialResourcePack] ERROR: Unable to prepare resource pack: " + e.getMessage()); active=null; return null; }
  }
  private static byte[] sha1(Path file) throws IOException { try { MessageDigest digest=MessageDigest.getInstance("SHA-1"); byte[] buf=new byte[8192]; try(InputStream in=Files.newInputStream(file)){for(int n;(n=in.read(buf))!=-1;)digest.update(buf,0,n);} return digest.digest(); } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
  public ResourcePackValidator.ValidationResult validateActive(ResourcePackConfig c) { ActivePack p=active; return p==null ? ResourcePackValidator.ValidationResult.invalid("No active resource pack.") : validator.validate(p.file(),c.requireMcmeta(),c.maxSizeBytes()); }
}
