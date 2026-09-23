package id.imperial.resourcepack.paper;

import id.imperial.resourcepack.common.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ImperialResourcePackPaper extends JavaPlugin implements Listener {
  private final ExecutorService worker = Executors.newFixedThreadPool(3, r -> {
    Thread t = new Thread(r, "ImperialResourcePack-IO");
    t.setDaemon(true);
    return t;
  });
  private ResourcePackManager manager;
  private ResourcePackHost host;
  private ResourcePackStats stats;
  private volatile ResourcePackConfig config;
  private volatile String inventoryFingerprint = "";
  /** Persistent per-player pack preference: UUID -> pack filename. */
  private final ConcurrentMap<UUID, String> playerPackPreferences = new ConcurrentHashMap<>();
  private Path playerPreferencesFile;
  private BukkitTask autoReloadTask;

  @Override
  public void onEnable() {
    manager = new ResourcePackManager(getLogger());
    host = new ResourcePackHost(manager, getLogger());
    stats = new ResourcePackStats();
    reload();
    loadPlayerPreferences();
    getServer().getPluginManager().registerEvents(this, this);

    var command = Objects.requireNonNull(getCommand("irp"));
    var executor = new PaperCommand(this);
    command.setExecutor(executor);
    command.setTabCompleter(executor);
  }

  @Override
  public void onDisable() {
    if (autoReloadTask != null) autoReloadTask.cancel();
    host.close();
    worker.shutdownNow();
  }

  @EventHandler
  public void join(PlayerJoinEvent event) {
    ResourcePackConfig c = config;
    if (c != null && c.enabled() && c.deliveryEnabled()) {
      long ticks = Math.max(1, c.sendDelayMs() / 50);
      getServer().getScheduler().runTaskLater(this, () -> send(event.getPlayer()), ticks);
    }
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    // Preference is intentionally kept; it must survive reconnects.
  }

  @EventHandler
  public void packStatus(PlayerResourcePackStatusEvent event) {
    if (!config.statsEnabled()) return;
    switch (event.getStatus()) {
      case ACCEPTED -> stats.accepted();
      case SUCCESSFULLY_LOADED -> stats.loaded();
      case DECLINED -> stats.declined();
      case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> stats.failed();
      default -> { }
    }
  }

  void send(Player player) {
    ResourcePackConfig c = config;
    ActivePack selected = resolvePlayerPack(player.getUniqueId());
    if (selected == null) selected = manager.active();
    if (c == null || selected == null || !c.enabled() || !c.deliveryEnabled()
        || c.publicUrl().isBlank() || !host.running()) {
      return;
    }

    // The HTTP host selects the requested ZIP using the encoded pack query parameter.
    String relative = packsDirectory().relativize(selected.file().toAbsolutePath().normalize()).toString()
        .replace(java.io.File.separatorChar, '/');
    String url = ResourcePackHost.urlForPack(c.publicUrl(), relative);

    if (c.debug()) getLogger().info("[IRP DEBUG] Sending pack to " + player.getName());

    player.setResourcePack(
        selected.id(),
        url,
        selected.sha1(),
        MiniMessage.miniMessage().deserialize(c.prompt()),
        c.required()
    );
    if (c.statsEnabled()) stats.sent();
  }

  void applyToOnlinePlayers() {
    if (!getServer().isPrimaryThread()) {
      getServer().getScheduler().runTask(this, this::applyToOnlinePlayers);
      return;
    }
    for (Player player : getServer().getOnlinePlayers()) send(player);
  }

  synchronized String use(org.bukkit.command.CommandSender sender, String value) {
    ResourcePackConfig c = config;
    ResourcePackManager.ResolutionResult resolution =
        manager.resolveValidDetailed(packsDirectory(), value, c);
    if (!resolution.success()) return "No valid pack matched '" + value + "': " + resolution.message();

    var result = manager.prepare(resolution.file(), packsDirectory(), c);
    if (!result.success()) return "Pack was NOT changed: " + result.message();

    if (sender instanceof Player player) {
      playerPackPreferences.put(player.getUniqueId(), result.pack().file().getFileName().toString());
      savePlayerPreferences();
      getServer().getScheduler().runTask(this, () -> send(player));
      return "Your private Active Pack is now " + result.pack().file().getFileName()
          + ". Other Java players are unchanged.";
    }

    var global = manager.activate(resolution.file(), packsDirectory(), c);
    if (!global.success()) return "Main Active Pack was NOT changed: " + global.message();

    inventoryFingerprint = manager.inventoryFingerprint(packsDirectory());
    applyToOnlinePlayers();
    return "Main Active Pack changed to " + global.pack().file().getFileName()
        + ". Players without a private override will receive it.";
  }

  private ActivePack resolvePlayerPack(UUID uuid) {
    String filename = playerPackPreferences.get(uuid);
    if (filename == null || filename.isBlank()) return null;
    ResourcePackManager.ResolutionResult resolution =
        manager.resolveValidDetailed(packsDirectory(), filename, config);
    if (!resolution.success()) {
      getLogger().warning("[ImperialResourcePack] Saved player pack '" + filename
          + "' for " + uuid + " is unavailable; using Main Active Pack.");
      return null;
    }
    var prepared = manager.prepare(resolution.file(), packsDirectory(), config);
    return prepared.success() ? prepared.pack() : null;
  }

  private void loadPlayerPreferences() {
    playerPreferencesFile = getDataFolder().toPath().resolve("player-packs.yml");
    if (Files.notExists(playerPreferencesFile)) return;
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerPreferencesFile.toFile());
    var section = yaml.getConfigurationSection("players");
    if (section == null) return;
    for (String key : section.getKeys(false)) {
      try {
        UUID uuid = UUID.fromString(key);
        String file = section.getString(key);
        if (file != null && !file.isBlank()) playerPackPreferences.put(uuid, file);
      } catch (IllegalArgumentException ignored) { }
    }
  }

  private synchronized void savePlayerPreferences() {
    try {
      if (playerPreferencesFile == null) {
        playerPreferencesFile = getDataFolder().toPath().resolve("player-packs.yml");
      }
      Files.createDirectories(playerPreferencesFile.getParent());
      YamlConfiguration yaml = new YamlConfiguration();
      for (var entry : playerPackPreferences.entrySet()) {
        yaml.set("players." + entry.getKey(), entry.getValue());
      }
      yaml.save(playerPreferencesFile.toFile());
    } catch (IOException e) {
      getLogger().warning("[ImperialResourcePack] Could not save player-packs.yml: " + e.getMessage());
    }
  }

  String openUseMenu(org.bukkit.command.CommandSender sender) {
    sender.sendMessage("[IRP] Use /irp use <version> to choose your private pack. Example: /irp use 1.21.5");
    sender.sendMessage("[IRP] Your choice is saved and restored when you rejoin.");
    return "";
  }


  String pathsMessage() {
    Path dir = packsDirectory().toAbsolutePath().normalize();
    return "Pack directory: " + dir
        + "\nHosting: " + (config.publicUrl().isBlank() ? "MISSING" : config.publicUrl());
  }

  String diagnoseMessage(String version) {
    if (version == null || version.isBlank()) return diagnoseSummary();
    var route = manager.route(version);
    Path resolved = manager.versionPack(packsDirectory(), version);
    String expected = manager.expectedRouteFile(version);
    ResourcePackValidator.ValidationResult validation = resolved == null
        ? ResourcePackValidator.ValidationResult.invalid("No file resolved.")
        : manager.validate(resolved, config);

    StringBuilder out = new StringBuilder();
    out.append("Client version: ").append(version).append('\n');
    out.append("Route: ").append(route.map(SorterResolver.Route::range).orElse("NONE")).append('\n');
    out.append("Expected: ").append(expected.isBlank() ? "NONE" : expected).append('\n');
    out.append("Resolved file: ").append(resolved == null ? "NONE" : resolved.toAbsolutePath().normalize()).append('\n');
    out.append("Exists: ").append(resolved != null && Files.isRegularFile(resolved)).append('\n');
    out.append("ZIP valid: ").append(validation.valid()).append(" (" + validation.message() + ")\n");
    out.append("pack.mcmeta root: ").append(hasRootMcmeta(resolved)).append('\n');
    if (resolved != null) {
      var prepared = manager.prepare(resolved, packsDirectory(), config);
      out.append("SHA-1: ").append(prepared.success() ? prepared.pack().sha1Hex() : "N/A").append('\n');
      out.append("Size: ").append(prepared.success() ? prepared.pack().size() : "N/A").append('\n');
      out.append("Final URL: ").append(prepared.success() ? ResourcePackHost.urlForPack(config.publicUrl(),
          packsDirectory().relativize(resolved.toAbsolutePath().normalize()).toString().replace(java.io.File.separatorChar, '/')) : "N/A").append('\n');
      if (!prepared.success()) out.append("Reason: ").append(prepared.message()).append('\n');
    }
    return out.toString().trim();
  }

  String diagnoseHostingMessage() {
    return host.diagnostic();
  }

  String diagnoseSummary() {
    ResourcePackManager.AuditReport report = manager.audit(packsDirectory(), config);
    StringBuilder out = new StringBuilder();
    out.append("Pack directory: ").append(packsDirectory().toAbsolutePath().normalize()).append('\n');
    out.append("ZIP detected: ").append(report.zipDetected()).append('\n');
    out.append("Version routes: ").append(report.routeCount()).append('\n');
    out.append("Resolved: ").append(report.resolvedRoutes()).append('/').append(report.routeCount()).append('\n');
    out.append("Invalid ZIP: ").append(report.invalidZip()).append('\n');
    out.append("Missing pack.mcmeta: ").append(report.missingMcmeta()).append('\n');
    out.append("Duplicate basenames: ").append(report.duplicateBasenames()).append('\n');
    out.append("Missing routes: ").append(report.missingRoutes()).append('\n');
    if (!report.routeFailures().isEmpty()) out.append("Route failures: ").append(report.routeFailures()).append('\n');
    if (!report.failures().isEmpty()) out.append("Pack failures: ").append(report.failures()).append('\n');
    return out.toString().trim();
  }

  String statusMessage() {
    ActivePack active = manager.active();
    return "enabled=" + config.enabled()
        + ", delivery=" + config.deliveryEnabled()
        + ", hosting=" + host.running()
        + ", active=" + (active == null ? "none" : active.file().getFileName())
        + ", cache=" + manager.cacheSize()
        + ", packs=" + manager.listAll(packsDirectory()).size();
  }

  String statsMessage() {
    return "sent=" + stats.sentCount()
        + ", accepted=" + stats.acceptedCount()
        + ", loaded=" + stats.loadedCount()
        + ", declined=" + stats.declinedCount()
        + ", failed=" + stats.failedCount()
        + ", auto-reloads=" + stats.reloadCount();
  }

  void rescanAsync(org.bukkit.command.CommandSender sender) {
    sender.sendMessage("[IRP] Rescan started asynchronously; player delivery is not triggered.");
    Path packs = packsDirectory();
    ResourcePackConfig c = config;
    worker.submit(() -> {
      manager.rebuildRouteCache(packs);
      manager.prewarm(packs, c);
      ResourcePackManager.AuditReport report = manager.audit(packs, c);
      inventoryFingerprint = manager.inventoryFingerprint(packs);
      getServer().getScheduler().runTask(this, () -> {
        sender.sendMessage("[IRP] Rescan complete: resolved " + report.resolvedRoutes() + "/" + report.routeCount()
            + ", invalid=" + report.invalidZip() + ", missingRoutes=" + report.missingRoutes()
            + ", duplicates=" + report.duplicateBasenames());
      });
    });
  }

  void playerMessage(org.bukkit.command.CommandSender sender, String name) {
    Player player = Bukkit.getPlayerExact(name);
    if (player == null) {
      sender.sendMessage("[IRP] Player not found or offline: " + name);
      return;
    }
    ActivePack pack = resolvePlayerPack(player.getUniqueId());
    if (pack == null) pack = manager.active();
    String url = pack == null ? "N/A" : ResourcePackHost.urlForPack(config.publicUrl(),
        packsDirectory().relativize(pack.file().toAbsolutePath().normalize()).toString()
            .replace(java.io.File.separatorChar, '/'));
    sender.sendMessage("[IRP] Player=" + player.getName()
        + " | privatePack=" + (playerPackPreferences.getOrDefault(player.getUniqueId(), "NONE"))
        + " | effectivePack=" + (pack == null ? "NONE" : pack.file().getFileName())
        + " | URL=" + url
        + " | SHA-1=" + (pack == null ? "N/A" : pack.sha1Hex())
        + " | status=" + (pack == null ? "NONE" : "READY"));
  }

  String reload() {
    try {
      Path data = getDataFolder().toPath();
      Files.createDirectories(data);
      Path file = data.resolve("config.yml");
      if (Files.notExists(file)) {
        try (InputStream in = getResource("config.yml")) {
          if (in == null) throw new IOException("Missing bundled config.yml");
          Files.copy(in, file);
        }
      }

      ResourcePackConfig loaded = ResourcePackConfig.load(file);
      Path packs = resolvePacksDirectory(data, loaded.packsDirectory());
      Files.createDirectories(packs);

      config = loaded;
      manager.scan(packs, loaded);
      host.start(loaded, worker, packs);
      warnPublicUrl(loaded, packs);
      configureAutoReload();

      return "Reload complete. Active=" +
          (manager.active() == null ? "none" : manager.active().file().getFileName())
          + ". No players were resent.";
    } catch (Exception e) {
      getLogger().severe("[ImperialResourcePack] ERROR: " + e.getMessage());
      return "Reload failed: " + e.getMessage();
    }
  }

  private void configureAutoReload() {
    if (autoReloadTask != null) autoReloadTask.cancel();
    ResourcePackConfig c = config;
    if (!c.autoReload()) return;
    long ticks = Math.max(20, c.autoReloadIntervalMs() / 50);
    autoReloadTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
      Path packs = packsDirectory();
      String current = manager.inventoryFingerprint(packs);
      if (current.equals(inventoryFingerprint)) return;
      inventoryFingerprint = current;
      worker.submit(() -> {
        manager.rebuildRouteCache(packs);
        manager.prewarm(packs, config);
        ResourcePackManager.AuditReport report = manager.audit(packs, config);
        getLogger().info("[ImperialResourcePack] Pack inventory changed; cache/routes rebuilt: "
            + report.resolvedRoutes() + "/" + report.routeCount() + " routes resolved.");
      });
    }, ticks, ticks);
  }

  private void logAudit(ResourcePackManager.AuditReport report, Path packs) {
    getLogger().info("[ImperialResourcePack] Pack directory: " + packs.toAbsolutePath().normalize());
    getLogger().info("[ImperialResourcePack] ZIP detected: " + report.zipDetected());
    getLogger().info("[ImperialResourcePack] Version routes: " + report.routeCount());
    getLogger().info("[ImperialResourcePack] Resolved: " + report.resolvedRoutes() + "/" + report.routeCount());
    getLogger().info("[ImperialResourcePack] Invalid ZIP: " + report.invalidZip());
    getLogger().info("[ImperialResourcePack] Missing pack.mcmeta: " + report.missingMcmeta());
    getLogger().info("[ImperialResourcePack] Duplicate basenames: " + report.duplicateBasenames());
    getLogger().info("[ImperialResourcePack] Missing routes: " + report.missingRoutes());
    report.routeFailures().forEach(x -> getLogger().warning("[ImperialResourcePack] Route failure: " + x));
    report.failures().forEach(x -> getLogger().warning("[ImperialResourcePack] Pack failure: " + x));
    report.duplicates().forEach(x -> getLogger().warning("[ImperialResourcePack] Duplicate basename: " + x));
  }

  private void warnPublicUrl(ResourcePackConfig c, Path packs) {
    if (c.publicUrl().isBlank()) {
      getLogger().warning("[ImperialResourcePack] WARNING: hosting.public-url is empty.");
      return;
    }
    try {
      URI uri = URI.create(c.publicUrl());
      int port = uri.getPort();
      if (port != -1 && port != c.port()) {
        getLogger().warning("[ImperialResourcePack] WARNING: public-url uses port " + port
            + " while hosting listens on " + c.port() + ". This is valid only when a reverse proxy forwards to the host.");
      } else if (port == -1) {
        getLogger().info("[ImperialResourcePack] public-url has no explicit port; assuming reverse proxy/default HTTP(S) forwarding to " + c.port() + ".");
      }
      if (uri.getPath() != null && !uri.getPath().equals(c.hostPath())) {
        getLogger().warning("[ImperialResourcePack] WARNING: public-url path '" + uri.getPath()
            + "' differs from hosting.path '" + c.hostPath() + "'.");
      }
    } catch (IllegalArgumentException e) {
      getLogger().warning("[ImperialResourcePack] WARNING: invalid hosting.public-url: " + c.publicUrl());
    }
  }

  private static Path resolvePacksDirectory(Path data, String configured) {
    Path raw = Path.of(configured);
    return raw.isAbsolute() ? raw.normalize() : data.resolve(raw).normalize();
  }

  private boolean hasRootMcmeta(Path file) {
    if (file == null || !Files.isRegularFile(file)) return false;
    try (var zip = new java.util.zip.ZipFile(file.toFile())) {
      var entry = zip.getEntry("pack.mcmeta");
      return entry != null && !entry.isDirectory();
    } catch (IOException e) {
      return false;
    }
  }

  private int detectClientProtocol(Player player) {
    try {
      Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
      Object api = viaClass.getMethod("getAPI").invoke(null);
      Object result = api.getClass().getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId());
      if (result instanceof Number number && number.intValue() > 0) return number.intValue();
    } catch (Throwable ignored) { }
    return player.getProtocolVersion();
  }

  private void debug(String message) {
    if (config != null && config.debug()) getLogger().info("[IRP DEBUG] " + message);
  }

  private void warnOnce(String key, String message) {
    getLogger().warning(message);
  }

  Path packsDirectory() {
    ResourcePackConfig c = config;
    if (c == null) return getDataFolder().toPath().resolve("packs").normalize();
    return resolvePacksDirectory(getDataFolder().toPath(), c.packsDirectory());
  }

  ResourcePackManager manager() { return manager; }
  ResourcePackHost host() { return host; }
  ResourcePackConfig config() { return config; }
  ResourcePackStats stats() { return stats; }

  private record Selection(int protocol, String version, SorterResolver.Route route, ActivePack pack, String reason) {}
}
