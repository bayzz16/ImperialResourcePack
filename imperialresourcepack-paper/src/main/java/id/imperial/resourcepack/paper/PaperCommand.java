package id.imperial.resourcepack.paper;

import id.imperial.resourcepack.common.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PaperCommand implements TabExecutor {
  private static final String P = "imperialresourcepack.admin";
  private final ImperialResourcePackPaper plugin;

  PaperCommand(ImperialResourcePackPaper plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, String[] args) {
    if (!sender.hasPermission(P)) {
      sender.sendMessage("[IRP] No permission.");
      return true;
    }

    if (args.length == 0) {
      sender.sendMessage("[IRP] /irp use [file|version] | list | info | reload | validate | status | diagnose | stats | profile");
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "list" -> {
        List<Path> all = plugin.manager().listAll(plugin.packsDirectory());
        if (all.isEmpty()) {
          sender.sendMessage("[IRP] No ZIP files found in packs.");
          return true;
        }
        Path active = plugin.manager().active() == null ? null : plugin.manager().active().file();
        sender.sendMessage("[IRP] Packs in folder:");
        for (Path file : all) {
          boolean valid = plugin.manager().validate(file, plugin.config()).valid();
          sender.sendMessage(" - " + file.getFileName()
              + (file.equals(active) ? " [ACTIVE]" : "")
              + (valid ? " [VALID]" : " [INVALID]"));
        }
      }
      case "use" -> {
        if (args.length < 2) {
          plugin.openUseMenu(sender);
          return true;
        }
        sender.sendMessage("[IRP] " + plugin.use(args[1]));
      }
      case "info" -> {
        ActivePack x = plugin.manager().active();
        sender.sendMessage("[IRP] " + (x == null
            ? "No active pack."
            : x.file().getFileName() + " | size=" + x.size()
                + " | SHA-1=" + x.sha1Hex() + " | UUID=" + x.id()
                + " | HTTP=" + plugin.host().running()));
      }
      case "reload" -> sender.sendMessage("[IRP] " + plugin.reload());
      case "validate" -> sender.sendMessage("[IRP] " + plugin.manager().validateActive(plugin.config()).message());
      case "status" -> sender.sendMessage("[IRP] " + plugin.statusMessage());
      case "diagnose" -> sender.sendMessage("[IRP] " + plugin.diagnoseMessage());
      case "stats" -> sender.sendMessage("[IRP] " + plugin.statsMessage());
      case "profile" -> profile(sender, args);
      default -> sender.sendMessage("[IRP] Unknown subcommand.");
    }
    return true;
  }

  private void profile(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      if (plugin.config().profiles().isEmpty()) {
        sender.sendMessage("[IRP] No profiles configured. Add them under profiles: in config.yml.");
        return;
      }
      sender.sendMessage("[IRP] Profiles:");
      plugin.config().profiles().forEach((name, file) -> sender.sendMessage(" - " + name + " -> " + file));
      return;
    }

    if (args[1].equalsIgnoreCase("use")) {
      if (args.length < 3) {
        sender.sendMessage("[IRP] Usage: /irp profile use <name>");
        return;
      }
      String file = plugin.config().profiles().get(args[2]);
      if (file == null) {
        sender.sendMessage("[IRP] Unknown profile: " + args[2]);
        return;
      }
      sender.sendMessage("[IRP] " + plugin.use(file));
      return;
    }

    sender.sendMessage("[IRP] Usage: /irp profile [list|use <name>]");
  }

  @Override
  public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, String[] args) {
    if (!sender.hasPermission(P)) return List.of();

    if (args.length == 1) {
      return part(List.of("list", "use", "info", "reload", "validate", "status", "diagnose", "stats", "profile"), args[0]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("use")) {
      List<String> result = new ArrayList<>();
      result.addAll(plugin.manager().listAll(plugin.packsDirectory()).stream()
          .map(x -> x.getFileName().toString()).toList());
      result.addAll(plugin.manager().versionMappings(plugin.packsDirectory()).stream()
          .map(ResourcePackManager.VersionMapping::label).toList());
      return part(result, args[1]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
      return part(List.of("list", "use"), args[1]);
    }

    if (args.length == 3 && args[0].equalsIgnoreCase("profile")
        && args[1].equalsIgnoreCase("use")) {
      return part(new ArrayList<>(plugin.config().profiles().keySet()), args[2]);
    }

    return List.of();
  }

  private static List<String> part(List<String> values, String input) {
    String q = input.toLowerCase(Locale.ROOT);
    return values.stream()
        .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(q))
        .distinct()
        .sorted()
        .toList();
  }
}
