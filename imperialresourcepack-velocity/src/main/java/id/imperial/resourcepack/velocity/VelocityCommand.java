package id.imperial.resourcepack.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import id.imperial.resourcepack.common.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

final class VelocityCommand implements SimpleCommand {
  private static final String P = "imperialresourcepack.admin";
  private final ImperialResourcePackVelocity plugin;

  VelocityCommand(ImperialResourcePackVelocity plugin) { this.plugin = plugin; }

  @Override
  public void execute(Invocation invocation) {
    if (!invocation.source().hasPermission(P)) {
      say(invocation, "No permission.");
      return;
    }

    String[] args = invocation.arguments();
    if (args.length == 0) {
      say(invocation, "Usage: /irp use [file|version] | list | info | reload | validate | status | diagnose | stats | profile");
      return;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "list" -> {
        List<Path> all = plugin.manager().listAll(plugin.packsDirectory());
        if (all.isEmpty()) {
          say(invocation, "No ZIP files found in packs.");
          return;
        }
        Path active = plugin.manager().active() == null ? null : plugin.manager().active().file();
        say(invocation, "Packs in folder:");
        for (Path file : all) {
          boolean valid = plugin.manager().validate(file, plugin.config()).valid();
          say(invocation, " - " + file.getFileName()
              + (file.equals(active) ? " [ACTIVE]" : "")
              + (valid ? " [VALID]" : " [INVALID]"));
        }
      }
      case "use" -> {
        if (args.length < 2) {
          say(invocation, "Available Java mappings:");
          for (ResourcePackManager.VersionMapping mapping : plugin.manager().versionMappings(plugin.packsDirectory())) {
            say(invocation, " - " + mapping.label() + " -> " + mapping.file().getFileName());
          }
          return;
        }
        say(invocation, plugin.use(args[1]));
      }
      case "info" -> {
        ActivePack x = plugin.manager().active();
        say(invocation, x == null
            ? "No active pack."
            : x.file().getFileName() + " | size=" + x.size()
                + " | SHA-1=" + x.sha1Hex() + " | UUID=" + x.id()
                + " | HTTP=" + plugin.host().running());
      }
      case "reload" -> say(invocation, plugin.reload());
      case "validate" -> say(invocation, plugin.manager().validateActive(plugin.config()).message());
      case "status" -> say(invocation, plugin.statusMessage());
      case "diagnose" -> say(invocation, plugin.diagnoseMessage());
      case "stats" -> say(invocation, plugin.statsMessage());
      case "profile" -> profile(invocation, args);
      default -> say(invocation, "Unknown subcommand.");
    }
  }

  private void profile(Invocation invocation, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      if (plugin.config().profiles().isEmpty()) {
        say(invocation, "No profiles configured. Add them under profiles: in config.yml.");
        return;
      }
      say(invocation, "Profiles:");
      plugin.config().profiles().forEach((name, file) -> say(invocation, " - " + name + " -> " + file));
      return;
    }

    if (args[1].equalsIgnoreCase("use")) {
      if (args.length < 3) {
        say(invocation, "Usage: /irp profile use <name>");
        return;
      }
      String file = plugin.config().profiles().get(args[2]);
      if (file == null) {
        say(invocation, "Unknown profile: " + args[2]);
        return;
      }
      say(invocation, plugin.use(file));
      return;
    }

    say(invocation, "Usage: /irp profile [list|use <name>]");
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    if (!invocation.source().hasPermission(P)) return List.of();

    String[] args = invocation.arguments();
    if (args.length <= 1) {
      return part(List.of("list", "use", "info", "reload", "validate", "status", "diagnose", "stats", "profile"),
          args.length == 0 ? "" : args[0]);
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

  private static void say(Invocation invocation, String message) {
    invocation.source().sendPlainMessage("[IRP] " + message);
  }
}
