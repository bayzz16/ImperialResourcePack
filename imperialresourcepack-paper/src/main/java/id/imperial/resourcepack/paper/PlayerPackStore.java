package id.imperial.resourcepack.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    preferences.put(uuid, version);
    save();
  }

  synchronized void save() {
    try {
      Files.createDirectories(file.getParent());
      YamlConfiguration yaml = new YamlConfiguration();
      for (Map.Entry<UUID, String> entry : preferences.entrySet()) {
        yaml.set("players." + entry.getKey(), entry.getValue());
      }
      yaml.save(file.toFile());
    } catch (IOException e) {
      throw new IllegalStateException("Could not save " + file, e);
    }
  }

  synchronized void reload() {
    load();
  }
}
