package id.imperial.resourcepack.common;

import java.util.List;

public final class MinecraftProtocolVersions {
  private MinecraftProtocolVersions() {}

  /**
   * Maps the wire protocol reported by Paper to the client version labels
   * understood by SorterResolver. Multiple labels are returned when one
   * resource-pack route intentionally covers several client versions.
   *
   * The first label is the most specific/modern candidate for that protocol.
   */
  public static List<String> versionsFor(int protocol) {
    return switch (protocol) {
      // Java 1.7.x - 1.8.x
      case 5 -> List.of("1.7.10");
      case 47 -> List.of("1.8.9", "1.8");

      // Java 1.9.x - 1.10.x
      case 107 -> List.of("1.9");
      case 108 -> List.of("1.9.1");
      case 109 -> List.of("1.9.2");
      case 110 -> List.of("1.9.4", "1.9.3");
      case 210 -> List.of("1.10.2", "1.10");

      // Java 1.11.x - 1.13.x
      case 315 -> List.of("1.11");
      case 316 -> List.of("1.11.2");
      case 335 -> List.of("1.12");
      case 338 -> List.of("1.12.1");
      case 340 -> List.of("1.12.2");
      case 393 -> List.of("1.13");
      case 401 -> List.of("1.13.1");
      case 404 -> List.of("1.13.2");

      // Java 1.14.x - 1.16.x
      case 477 -> List.of("1.14");
      case 480 -> List.of("1.14.1");
      case 485 -> List.of("1.14.2");
      case 490 -> List.of("1.14.3");
      case 498 -> List.of("1.14.4");
      case 573 -> List.of("1.15");
      case 575 -> List.of("1.15.1");
      case 578 -> List.of("1.15.2");
      case 735 -> List.of("1.16");
      case 736 -> List.of("1.16.1");
      case 751 -> List.of("1.16.2");
      case 753 -> List.of("1.16.3");
      case 754 -> List.of("1.16.5", "1.16.4");

      // Java 1.17.x - 1.19.x
      case 755 -> List.of("1.17");
      case 756 -> List.of("1.17.1");
      case 757 -> List.of("1.18.1", "1.18");
      case 758 -> List.of("1.18.2");
      case 759 -> List.of("1.19");
      case 760 -> List.of("1.19.2", "1.19.1");
      case 761 -> List.of("1.19.3");
      case 762 -> List.of("1.19.4");

      // Java 1.20.x - 1.21.x
      case 763 -> List.of("1.20.1", "1.20");
      case 764 -> List.of("1.20.2");
      case 765 -> List.of("1.20.4", "1.20.3");
      case 766 -> List.of("1.20.6", "1.20.5");
      case 767 -> List.of("1.21.1", "1.21");
      case 768 -> List.of("1.21.3", "1.21.2");
      case 769 -> List.of("1.21.4");
      case 770 -> List.of("1.21.5");
      case 771 -> List.of("1.21.6");
      case 772 -> List.of("1.21.8", "1.21.7");
      case 773 -> List.of("1.21.10", "1.21.9");
      case 774 -> List.of("1.21.11");

      // Newer protocol generations already represented by the sorter manifest.
      case 775 -> List.of("26.1.2", "26.1");
      case 776 -> List.of("26.2");
      case 777 -> List.of("26.3");

      default -> List.of();
    };
  }
}