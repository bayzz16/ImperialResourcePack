package id.imperial.resourcepack.paper;

import id.imperial.resourcepack.common.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;

public final class ImperialResourcePackPaper extends JavaPlugin implements Listener {
  private final ExecutorService worker = Executors.newFixedThreadPool(2);
  private ResourcePackManager manager;
  private ResourcePackHost host;
  private ResourcePackStats stats;
  private volatile ResourcePackConfig config;
  private volatile String inventoryFingerprint = "";
  private BukkitTask autoReloadTask;

  @Override
  public void onEnable() {
    manager = new ResourcePackManager(getLogger());
    host = new ResourcePackHost(manager, getLogger());
    stats = new ResourcePackStats();
    reload();
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
    ActivePack fallback = manager.active();
    if (c == null || fallback == null || c.publicUrl().isBlank() || !host.running()) return;

    int protocol = player.getProtocolVersion();
    ActivePack selected = fallback;
    String detected = "fallback";
    if (c.versionMapping()) {
      for (String version : MinecraftProtocolVersions.versionsFor(protocol)) {
        Path file = manager.versionPack(packsDirectory(), version);
        if (file == null) continue;
        var prepared = manager.prepare(file, packsDirectory(), c);
        if (prepared.success()) {
          selected = prepared.pack();
          detected = version;
          break;
        }
        getLogger().warning("[ImperialResourcePack] Ignoring invalid version-mapped pack "
            + file.getFileName() + ": " + prepared.message());
      }
    }

    if (c.versionMapping() && "fallback".equals(detected) && !c.fallbackToActive()) {
      getLogger().warning("[ImperialResourcePack] No mapped pack for protocol " + protocol
          + " and fallback is disabled.");
      return;
    }

    String url = ResourcePackHost.urlForPack(c.publicUrl(), selected.file().getFileName().toString());
    getLogger().info("[ImperialResourcePack] Client=" + player.getName()
        + " protocol=" + protocol + ", detected-version=" + detected
        + ", sending=" + selected.file().getFileName());

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

  synchronized String use(String value) {
    ResourcePackConfig c = config;
    Path selected = null;

    if (looksLikeVersion(value)) {
      selected = manager.versionPack(packsDirectory(), value);
    }
    if (selected == null) selected = manager.resolveValid(packsDirectory(), value, c);
    if (selected == null) return "No valid pack matched '" + value + "'.";

    var result = manager.activate(selected, packsDirectory(), c);
    if (!result.success()) return "Pack was NOT changed: " + result.message();

    inventoryFingerprint = manager.inventoryFingerprint(packsDirectory());
    applyToOnlinePlayers();
    return "Active pack changed to " + result.pack().file().getFileName()
        + ". Online Java players are being updated automatically.";
  }

  private static boolean looksLikeVersion(String value) {
    return value != null && value.matches("\\d+\\.\\d+(?:\\.\\d+)?");
  }

  String openUseMenu(org.bukkit.command.CommandSender sender) {
    if (!(sender instanceof org.bukkit.entity.Player player)) {
      sender.sendMessage("[IRP] Console: use /irp use <file-or-version>.");
      return "";
    }
    player.sendMessage(Component.text("§6§lImperialResourcePack §8» §fChoose a Java pack:"));
    for (ResourcePackManager.VersionMapping mapping : manager.versionMappings(packsDirectory())) {
      Component line = Component.text("§e▶ §f" + mapping.label() + " §7→ §b" + mapping.file().getFileName())
          .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
              "/irp use " + mapping.file().getFileName()));
      player.sendMessage(line);
    }
    return "";
  }

  String statusMessage() {
    ActivePack active = manager.active();
    return "enabled=" + config.enabled()
        + " | HTTP=" + host.running()
        + " | mapping=" + config.versionMapping()
        + " | auto-reload=" + config.autoReload()
        + " | active=" + (active == null ? "none" : active.file().getFileName())
        + " | packs=" + manager.listAll(packsDirectory()).size();
  }

  String diagnoseMessage() {
    int total = manager.listAll(packsDirectory()).size();
    int valid = manager.listValid(packsDirectory(), config).size();
    int mapped = manager.versionMappings(packsDirectory()).size();
    return "packs=" + total + ", valid=" + valid + ", mappings=" + mapped
        + ", active=" + (manager.active() == null ? "none" : "OK")
        + ", HTTP=" + (host.running() ? "ONLINE" : "OFFLINE")
        + ", public-url=" + (config.publicUrl().isBlank() ? "MISSING" : "OK");
  }

  String statsMessage() {
    return "sent=" + stats.sentCount()
        + ", accepted=" + stats.acceptedCount()
        + ", loaded=" + stats.loadedCount()
        + ", declined=" + stats.declinedCount()
        + ", failed=" + stats.failedCount()
        + ", auto-reloads=" + stats.reloadCount();
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
      Path packs = data.resolve(loaded.packsDirectory()).normalize();
      if (!packs.startsWith(data)) return "Invalid packs-directory.";

      config = loaded;
      manager.scan(packs, loaded);
      inventoryFingerprint = manager.inventoryFingerprint(packs);
      host.start(loaded, worker, packs);
      configureAutoReload();

      if (loaded.publicUrl().isBlank()) {
        getLogger().warning("[ImperialResourcePack] WARNING: hosting.public-url is empty.");
      }
      return "Reload complete. Active=" +
          (manager.active() == null ? "none" : manager.active().file().getFileName());
    } catch (Exception e) {
      getLogger().severe("[ImperialResourcePack] ERROR: " + e.getMessage());
      return "Reload failed: " + e.getMessage();
    }
  }

  private void configureAutoReload() {
    if (autoReloadTask != null) autoReloadTask.cancel();
    if (!config.autoReload()) return;

    long periodTicks = Math.max(20, config.autoReloadIntervalMs() / 50);
    autoReloadTask = getServer().getScheduler().runTaskTimer(this, () -> {
      try {
        String before = inventoryFingerprint;
        ActivePack previous = manager.active();
        manager.scan(packsDirectory(), config);
        String after = manager.inventoryFingerprint(packsDirectory());

        if (!Objects.equals(before, after)) {
          inventoryFingerprint = after;
          if (config.statsEnabled()) stats.reload();
          boolean activeChanged = previous == null
              ? manager.active() != null
              : manager.active() != null
                  && (!previous.sha1Hex().equals(manager.active().sha1Hex())
                      || previous.modified() != manager.active().modified()
                      || !previous.file().equals(manager.active().file()));

          getLogger().info("[ImperialResourcePack] Packs folder changed. "
              + "Discovery/validation refreshed automatically.");
          if (activeChanged || config.versionMapping()) applyToOnlinePlayers();
        }
      } catch (Exception e) {
        getLogger().warning("[ImperialResourcePack] Auto-reload check failed: " + e.getMessage());
      }
    }, periodTicks, periodTicks);
  }

  ResourcePackManager manager() { return manager; }
  ResourcePackHost host() { return host; }
  ResourcePackConfig config() { return config; }
  Path packsDirectory() { return getDataFolder().toPath().resolve(config.packsDirectory()).normalize(); }
  ResourcePackStats stats() { return stats; }
}
