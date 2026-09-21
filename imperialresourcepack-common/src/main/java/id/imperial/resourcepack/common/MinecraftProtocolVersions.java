package id.imperial.resourcepack.common;

import java.util.List;

public final class MinecraftProtocolVersions {
  private MinecraftProtocolVersions() {}

  public static List<String> versionsFor(int protocol) {
    return switch (protocol) {
      case 774 -> List.of("1.21.11");
      case 773 -> List.of("1.21.10", "1.21.9");
      case 772 -> List.of("1.21.8", "1.21.7");
      case 771 -> List.of("1.21.6");
      case 770 -> List.of("1.21.5");
      case 769 -> List.of("1.21.4");
      case 768 -> List.of("1.21.3", "1.21.2");
      case 767 -> List.of("1.21");
      case 766 -> List.of("1.20.6", "1.20.5");
      case 765 -> List.of("1.20.4", "1.20.3");
      case 764 -> List.of("1.20.2");
      case 763 -> List.of("1.20.1", "1.20");
      default -> List.of();
    };
  }
}