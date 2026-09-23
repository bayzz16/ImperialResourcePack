package id.imperial.resourcepack.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import id.imperial.resourcepack.common.*;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

@Plugin(id="imperialresourcepack", name="ImperialResourcePack", version="1.1.0", authors={"bayzz16"})
public final class ImperialResourcePackVelocity {
  private final ProxyServer proxy;
  private final Logger logger;
  private final Path data;
  private final ExecutorService worker = Executors.newFixedThreadPool(2);
  private final ResourcePackManager manager;
  private final ResourcePackHost host;
  private final ResourcePackStats stats = new ResourcePackStats();
  private volatile ResourcePackConfig config;
  private volatile String inventoryFingerprint = "";
  private ScheduledTask autoReloadTask;

  @Inject
  public ImperialResourcePackVelocity(
      ProxyServer proxy,
      Logger logger,
      @com.velocitypowered.api.plugin.annotation.DataDirectory Path data
  ) {
    this.proxy = proxy;
    this.logger = logger;
    this.data = data;
    manager = new ResourcePackManager(logger);
    host = new ResourcePackHost(manager, logger);
  }

  @Subscribe
  public void initialize(ProxyInitializeEvent event) {
    reload();
    proxy.getCommandManager().register("irp", new VelocityCommand(this));
    logger.info("[ImperialResourcePack] Plugin enabled.");
  }

  @Subscribe
  public void shutdown(ProxyShutdownEvent event) {
    if (autoReloadTask != null) autoReloadTask.cancel();
    host.close();
    worker.shutdownNow();
  }

  @Subscribe
  public void login(PostLoginEvent event) {
    ResourcePackConfig c = config;
    if (c != null && c.enabled() && c.deliveryEnabled()) {
      proxy.getScheduler().buildTask(this, () -> send(event.getPlayer()))
          .delay(c.sendDelayMs(), TimeUnit.MILLISECONDS)
          .schedule();
    }
  }

  @Subscribe
  public void packStatus(PlayerResourcePackStatusEvent event) {
    if (!config.statsEnabled()) return;
    switch (event.getStatus()) {
      case ACCEPTED -> stats.accepted();
      case SUCCESSFUL -> stats.loaded();
      case DECLINED -> stats.declined();
      case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> stats.failed();
      default -> { }
    }
  }

  void send(Player player) {
    ResourcePackConfig c = config;
    ActivePack fallback = manager.active();
    if (c == null || fallback == null || c.publicUrl().isBlank() || !host.running()) return;

    ProtocolVersion pv = player.getProtocolVersion();
    int protocol = pv.getProtocol();
    ActivePack selected = fallback;
    String detected = "fallback";

    if (c.versionMapping()) {
      for (String version : pv.getVersionsSupportedBy()) {
        Path file = manager.versionPack(packsDirectory(), version);
        if (file == null) continue;
        var prepared = manager.prepare(file, packsDirectory(), c);
        if (prepared.success()) {
          selected = prepared.pack();
          detected = version;
          break;
        }
        logger.warning("[ImperialResourcePack] Ignoring invalid version-mapped pack "
            + file.getFileName() + ": " + prepared.message());
      }
    }

    if (c.versionMapping() && "fallback".equals(detected) && !c.fallbackToActive()) {
      logger.warning("[ImperialResourcePack] No mapped pack for protocol " + protocol
          + " and fallback is disabled.");
      return;
    }

    String packPath = packsDirectory().relativize(selected.file().toAbsolutePath().normalize()).toString().replace(java.io.File.separatorChar, '/');
    String url = ResourcePackHost.urlForPack(c.publicUrl(), packPath);
    logger.info("[ImperialResourcePack] Client=" + player.getUsername()
        + " protocol=" + protocol + ", detected-version=" + detected
        + ", sending=" + selected.file().getFileName());

    ResourcePackInfo info = proxy.createResourcePackBuilder(url)
        .setHash(selected.sha1())
        .setId(selected.id())
        .setPrompt(MiniMessage.miniMessage().deserialize(c.prompt()))
        .setShouldForce(c.required())
        .build();

    player.sendResourcePackOffer(info);
    if (c.statsEnabled()) stats.sent();
  }

  void applyToOnlinePlayers() {
    for (Player player : proxy.getAllPlayers()) send(player);
  }

  synchronized String use(String value) {
    ResourcePackConfig c = config;
    Path selected = null;
    if (value != null && value.matches("\\d+\\.\\d+(?:\\.\\d+)?")) {
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
    return "packs=" + manager.listAll(packsDirectory()).size()
        + ", valid=" + manager.listValid(packsDirectory(), config).size()
        + ", mappings=" + manager.versionMappings(packsDirectory()).size()
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

  synchronized String reload() {
    try {
      Files.createDirectories(data);
      Path file = data.resolve("config.yml");
      if (Files.notExists(file)) {
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
          if (in == null) throw new IllegalStateException("Missing bundled config.yml");
          Files.copy(in, file);
        }
      }

      ResourcePackConfig loaded = ResourcePackConfig.load(file);
      Path packs = packsDirectory(loaded);
      if (!packs.startsWith(data)) return "Invalid packs-directory.";

      config = loaded;
      manager.scan(packs, loaded);
      inventoryFingerprint = manager.inventoryFingerprint(packs);
      host.start(loaded, worker, packs);
      configureAutoReload();

      if (loaded.publicUrl().isBlank()) {
        logger.warning("[ImperialResourcePack] WARNING: hosting.public-url is empty.");
      }
      return "Reload complete. Active="
          + (manager.active() == null ? "none" : manager.active().file().getFileName());
    } catch (Exception e) {
      logger.severe("[ImperialResourcePack] ERROR: " + e.getMessage());
      return "Reload failed: " + e.getMessage();
    }
  }

  private void configureAutoReload() {
    if (autoReloadTask != null) autoReloadTask.cancel();
    if (!config.autoReload()) return;

    autoReloadTask = proxy.getScheduler()
        .buildTask(this, this::autoReloadCheck)
        .repeat(Math.max(1000, config.autoReloadIntervalMs()), TimeUnit.MILLISECONDS)
        .schedule();
  }

  private void autoReloadCheck() {
    try {
      String before = inventoryFingerprint;
      ActivePack previous = manager.active();
      manager.scan(packsDirectory(), config);
      String after = manager.inventoryFingerprint(packsDirectory());

      if (!before.equals(after)) {
        inventoryFingerprint = after;
        if (config.statsEnabled()) stats.reload();
        boolean activeChanged = previous == null
            ? manager.active() != null
            : manager.active() != null
                && (!previous.sha1Hex().equals(manager.active().sha1Hex())
                    || previous.modified() != manager.active().modified()
                    || !previous.file().equals(manager.active().file()));

        logger.info("[ImperialResourcePack] Packs folder changed. Discovery/validation refreshed automatically.");
        if (activeChanged || config.versionMapping()) applyToOnlinePlayers();
      }
    } catch (Exception e) {
      logger.warning("[ImperialResourcePack] Auto-reload check failed: " + e.getMessage());
    }
  }

  private Path packsDirectory(ResourcePackConfig c) {
    return data.resolve(c.packsDirectory()).normalize();
  }

  Path packsDirectory() { return packsDirectory(config); }
  ResourcePackManager manager() { return manager; }
  ResourcePackHost host() { return host; }
  ResourcePackConfig config() { return config; }
  ResourcePackStats stats() { return stats; }
}
