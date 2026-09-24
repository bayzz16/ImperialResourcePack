package id.imperial.resourcepack.paper;

import id.imperial.resourcepack.common.*;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PaperCommand implements TabExecutor {
  private static final String ADMIN = "imperialresourcepack.admin";
  private static final String USE = "imperialresourcepack.use";
  private final ImperialResourcePackPaper plugin;

  PaperCommand(ImperialResourcePackPaper plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, String[] args) {
    if (args.length == 0) {
      if (!sender.hasPermission(ADMIN) && !sender.hasPermission(USE)) {
        sender.sendMessage("§c✦ §fAkses ditolak §8• §7Kamu tidak memiliki izin.");
        return true;
      }
      sender.sendMessage("§b✦ §fɪᴍᴘᴇʀɪᴀʟʀᴇꜱᴏᴜʀᴄᴇᴘᴀᴄᴋ §8• §7/irp use|list|info|reload|validate|status|diagnose|stats|profile");
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "use" -> {
        if (!sender.hasPermission(USE)) {
          sender.sendMessage("§c✦ §fAkses ditolak §8• §7Kamu tidak memiliki izin.");
          return true;
        }
        if (args.length < 2) {
          plugin.openUseMenu(sender);
          return true;
        }
        sender.sendMessage("[IRP] " + plugin.use(sender, args[1]));
      }
      case "list" -> {
        if (!requireAdmin(sender)) return true;
        List<Path> all = plugin.manager().listAll(plugin.packsDirectory());
        if (all.isEmpty()) {
          sender.sendMessage("§c✦ §fTidak ada resource pack §8• §7Folder packs kosong.");
          return true;
        }
        Path active = plugin.manager().active() == null ? null : plugin.manager().active().file();
        sender.sendMessage("§b✦ §fDaftar Resource Pack");
        for (Path file : all) {
          boolean valid = plugin.manager().validate(file, plugin.config()).valid();
          sender.sendMessage(" - " + file.getFileName()
              + (file.equals(active) ? " [ACTIVE]" : "")
              + (valid ? " [VALID]" : " [INVALID]"));
        }
      }
      case "info" -> {
        if (!requireAdmin(sender)) return true;
        ActivePack x = plugin.manager().active();
        sender.sendMessage("[IRP] " + (x == null
            ? "No active pack."
            : x.file().getFileName() + " | size=" + x.size()
                + " | SHA-1=" + x.sha1Hex() + " | UUID=" + x.id()
                + " | HTTP=" + plugin.host().running()));
      }
      case "reload" -> {
        if (!requireAdmin(sender)) return true;
        sender.sendMessage("[IRP] " + plugin.reload());
      }
      case "validate" -> {
        if (!requireAdmin(sender)) return true;
        sender.sendMessage("[IRP] " + plugin.manager().validateActive(plugin.config()).message());
      }
      case "status" -> {
        if (!requireAdmin(sender)) return true;
        sender.sendMessage("[IRP] " + plugin.statusMessage());
      }
      case "diagnose" -> {
        if (!requireAdmin(sender)) return true;
        if (args.length >= 2 && args[1].equalsIgnoreCase("hosting")) sender.sendMessage("[IRP] " + plugin.diagnoseHostingMessage());
        else if (args.length >= 2) sender.sendMessage("[IRP] " + plugin.diagnoseMessage(args[1]));
        else sender.sendMessage("[IRP] " + plugin.diagnoseMessage(null));
      }
      case "paths" -> {
        if (!requireAdmin(sender)) return true;
        sender.sendMessage("[IRP] " + plugin.pathsMessage());
      }
      case "rescan" -> {
        if (!requireAdmin(sender)) return true;
        plugin.rescanAsync(sender);
      }
      case "player" -> {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) sender.sendMessage("[IRP] Format: /irp player <player>");
        else plugin.playerMessage(sender, args[1]);
      }
      case "stats" -> {
        if (!requireAdmin(sender)) return true;
        sender.sendMessage("[IRP] " + plugin.statsMessage());
      }
      case "profile" -> {
        if (!requireAdmin(sender)) return true;
        profile(sender, args);
      }
      default -> sender.sendMessage("§c✦ §fSubcommand tidak dikenal.");
    }
    return true;
  }

  private void profile(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      if (plugin.config().profiles().isEmpty()) {
        sender.sendMessage("§e✦ §fBelum ada profile yang dikonfigurasi.");
        return;
      }
      sender.sendMessage("[IRP] Profiles:");
      plugin.config().profiles().forEach((name, file) -> sender.sendMessage(" - " + name + " -> " + file));
      return;
    }

    if (args[1].equalsIgnoreCase("use")) {
      if (args.length < 3) {
        sender.sendMessage("§7Format: §f/irp profile use <name>");
        return;
      }
      String file = plugin.config().profiles().get(args[2]);
      if (file == null) {
        sender.sendMessage("§c✦ §fProfile tidak ditemukan: " + args[2]);
        return;
      }
      sender.sendMessage("[IRP] " + plugin.use(sender, file));
      return;
    }

    sender.sendMessage("§7Format: §f/irp profile [list|use <name>]");
  }

  @Override
  public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, String[] args) {
    if (!sender.hasPermission(ADMIN) && !sender.hasPermission(USE)) return List.of();

    if (args.length == 1) {
      List<String> commands = new ArrayList<>();
      if (sender.hasPermission(USE)) commands.add("use");
      if (sender.hasPermission(ADMIN)) {
        commands.addAll(List.of("list", "info", "reload", "validate", "status",
            "diagnose", "paths", "rescan", "player", "stats", "profile"));
      }
      return part(commands, args[0]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("use")) {
      List<String> result = new ArrayList<>();
      result.addAll(plugin.manager().listAll(plugin.packsDirectory()).stream()
          .map(x -> x.getFileName().toString()).toList());
      result.addAll(plugin.manager().versionMappings(plugin.packsDirectory()).stream()
          .map(ResourcePackManager.VersionMapping::label).toList());
      return part(result, args[1]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("diagnose")) {
      return part(List.of("hosting", "1.7.10", "1.8.9", "1.14.4", "1.20.4", "1.21.1",
          "1.21.4", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.2", "26.3"), args[1]);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("player")) return List.of();

    if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
      return part(List.of("list", "use"), args[1]);
    }

    if (args.length == 3 && args[0].equalsIgnoreCase("profile")
        && args[1].equalsIgnoreCase("use")) {
      return part(new ArrayList<>(plugin.config().profiles().keySet()), args[2]);
    }

    return List.of();
  }

  private boolean requireAdmin(CommandSender sender) {
    if (sender.hasPermission(ADMIN)) return true;
    sender.sendMessage("§c✦ §fAkses ditolak §8• §7Perintah ini membutuhkan izin admin.");
    return false;
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
