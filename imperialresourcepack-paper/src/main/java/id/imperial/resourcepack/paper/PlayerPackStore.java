package id.imperial.resourcepack.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayerPackStore {
  private final Path file;
  private final Map<UUID, String> preferences = new ConcurrentHashMap<>();

  PlayerPackStore(Path file) {
    this.file = file;
  }

  synchronized void load() {
    preferences.clear();
    if (Files.notExists(file)) return;

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
    ConfigurationSection section = yaml.getConfigurationSection("players");
    if (section == null) return;

    for (String key : section.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(key);
        String version = section.getString(key);
        if (version != null && !version.isBlank()) {
          preferences.put(uuid, version);
        }
      } catch (IllegalArgumentException ignored) {
        // Ignore malformed UUID entries.
      }
    }
  }

  String get(UUID uuid) {
    return uuid == null ? null : preferences.get(uuid);
  }

  synchronized void set(UUID uuid, String version) {
    if (uuid == null || version == null || version.isBlank()) return;

    String previous = preferences.put(uuid, version);
    try {
      save();
    } catch (RuntimeException e) {
      if (previous == null) preferences.remove(uuid);
      else preferences.put(uuid, previous);
      throw e;
    }
  }

  synchronized void save() {
    Path parent = file.toAbsolutePath().normalize().getParent();
    Path temp = parent.resolve(file.getFileName() + ".tmp");

    try {
      Files.createDirectories(parent);

      YamlConfiguration yaml = new YamlConfiguration();
      for (Map.Entry<UUID, String> entry : preferences.entrySet()) {
        yaml.set("players." + entry.getKey(), entry.getValue());
      }

      // Write the complete document first, then replace the live file.
      // This prevents a crash during YAML serialization from leaving a partial player-packs.yml.
      yaml.save(temp.toFile());

      try {
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        // Some filesystems do not support ATOMIC_MOVE; REPLACE_EXISTING still avoids
        // exposing the temporary file as the configured player-packs.yml.
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // Keep the original file if cleanup itself fails.
      }
      throw new IllegalStateException("Could not save " + file, e);
    }
  }

  synchronized void reload() {
    load();
  }
}
