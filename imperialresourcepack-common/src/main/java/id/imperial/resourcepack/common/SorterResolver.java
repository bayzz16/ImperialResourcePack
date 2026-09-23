package id.imperial.resourcepack.common;

import java.util.List;
import java.util.Optional;

/**
 * Native version router for ImperialResourcePack.
 *
 * Integrates the supplied Imperial X SOL sorter routes into the existing
 * resource-pack manager. This is deliberately plugin-local and does not
 * replace the existing pack lifecycle, validation, hosting, or commands.
 */
public final class SorterResolver {
  public record Route(
      String range,
      String file,
      String sha1,
      String sha256,
      long bytes,
      String tier,
      String connection,
      Integer packFormat,
      Integer resourcePackVersionMajor,
      Integer resourcePackVersionMinor
  ) {}

  public record BedrockRoute(
      String file,
      String supportedClients,
      String emulatedJava,
      String note
  ) {}

  private static final List<Route> ROUTES = List.of(
    new Route("1.7.10-1.8.9","1_Resource_Pack_Java/00_Legacy_Fallback_1.7.10-1.13.2/GUI(1.7.10-1.8.9).zip","3bfc3d83a1b5b43bc1b5e528c5a96bb2368a735c","cb48f0e7c0a936b83b5573a8f52c475e9463a669a89ad7d5e180a0cd6e91b8b4",64275,"fallback_no_cmd","ViaRewind + ViaVersion + ViaBackwards",1,null,null),
    new Route("1.9-1.10.2","1_Resource_Pack_Java/00_Legacy_Fallback_1.7.10-1.13.2/GUI(1.9-1.10.2).zip","485d8fc9032f5a54f6d106a15cacf46e57ca3b93","5d5a5ae7a342485f2583d967da918a34130448f69ec9f1ab8e6d97de5a64a124",64273,"fallback_no_cmd","ViaVersion/ViaBackwards",2,null,null),
    new Route("1.11-1.12.2","1_Resource_Pack_Java/00_Legacy_Fallback_1.7.10-1.13.2/GUI(1.11-1.12.2).zip","2503c8a27e53d778a760848aab499266fc49fc75","ef9d5011b409198b88960fd619bb126ec3a5ff96b0aff02a2a8f986d19da0271",64273,"fallback_no_cmd","ViaVersion/ViaBackwards",3,null,null),
    new Route("1.13-1.13.2","1_Resource_Pack_Java/00_Legacy_Fallback_1.7.10-1.13.2/GUI(1.13-1.13.2).zip","80a0f756334c7fac0a8a7f342d24226f009da01a","8fe2f958f77729b8a06edac82a16bb9b594cc609d9fbcdd0a8d6c56cce4a43e4",73187,"fallback_no_cmd","ViaVersion/ViaBackwards",4,null,null),
    new Route("1.14-1.14.4","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.14-1.14.4).zip","7531d43ac3c3f4ec591e48c5e5f4f5e437e90dc0","f079120c735847eefdd2c23deff8c585f56c6784c697edb295f169a5f7ce249c",1649883,"full_custom_icons","ViaVersion/ViaBackwards",4,null,null),
    new Route("1.15-1.16.1","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.15-1.16.1).zip","2e36396f92fefbbac4eb54a6e79ce68272a42b00","e397b26803f20ca3207fe5adc43a197a5fefa324b5766c86a85ec776966e76c8",1649884,"full_custom_icons","ViaVersion/ViaBackwards",5,null,null),
    new Route("1.16.2-1.16.5","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.16.2-1.16.5).zip","c0fce2f251168fdf524e6978c9224e942cdd3a30","8748f55d6999970dae2f7c28b8eca213e467269a9defda2dc0580d1860ef6874",1649884,"full_custom_icons","ViaVersion/ViaBackwards",6,null,null),
    new Route("1.17-1.17.1","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.17-1.17.1).zip","a9b420392f1dab3be50a2e4904f1cc6cc16218ef","baa87f1ffac9ddf440e492ce6b23097475f72d6f44147b9c95d029331127ad3c",1649883,"full_custom_icons","ViaVersion/ViaBackwards",7,null,null),
    new Route("1.18-1.18.2","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.18-1.18.2).zip","e3e1e9ebacd6ca01a0cb565ab951db5c034edd7d","952a7cbec0803129d66433df0e5cce3558eb0d100cdd3d70f30e3bea40eb78fc",1649883,"full_custom_icons","ViaVersion/ViaBackwards",8,null,null),
    new Route("1.19-1.19.2","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.19-1.19.2).zip","54babd9bbae04c6742b8cdbfccddf890a4904b17","eb0340582c301faa80d9299bf8730d6fe53067ad0d0b9a780bc62d447322a2cc",1649883,"full_custom_icons","ViaVersion/ViaBackwards",9,null,null),
    new Route("1.19.3","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.19.3).zip","9d9ad4bf88c0393144dc8ca2af2bc71df9f6312d","e70ee9de6b143df63c0a024b9a33e00aaf791826e20cbb6713d4ada9c7f05723",1649882,"full_custom_icons","ViaVersion/ViaBackwards",12,null,null),
    new Route("1.19.4","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.19.4).zip","bba3ce6836538b0859a1639d54da127dbab9c746","1aa051887b3ea845b8e150a7e85b1f771b02ea684b42fb8c74082c6784044466",1649882,"full_custom_icons","ViaVersion/ViaBackwards",13,null,null),
    new Route("1.20-1.20.1","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.20-1.20.1).zip","6c6015f588957bcb9dac0d04be9525e359bdcdf8","509e42307526449f71f78525a0add083210fadc8176fd16a9811e2879b3ad45c",1649884,"full_custom_icons","ViaVersion/ViaBackwards",15,null,null),
    new Route("1.20.2","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.20.2).zip","7285022258620ba8bfd41283707adf3789e4c982","52824ad981085f9af7bb4a464b888bb12573674b5faa86992791e51e0dc7861b",1649882,"full_custom_icons","ViaVersion/ViaBackwards",18,null,null),
    new Route("1.20.3-1.20.4","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.20.3-1.20.4).zip","79132a6f7754b9a1ebf6c80811bef37bbc24c579","8c3d74397f5f38b5b07f4dbca5b867b0ab7fbfe938f2bbbe030cdb1e0c6f51c3",1649885,"full_custom_icons","ViaVersion/ViaBackwards",22,null,null),
    new Route("1.20.5-1.20.6","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.20.5-1.20.6).zip","6285c53ab0bd07bd4b63eb8d24905b90ee7eb454","39bac0c8365d968b9534dfe5aac7b1d332202b9d1d812dfbdfa9f0aa186d0d22",1649885,"full_custom_icons","ViaVersion/ViaBackwards",32,null,null),
    new Route("1.21-1.21.1","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.21-1.21.1).zip","045f9cd95ad3314d602375c2302b13b6564eb51a","b7ec1a80626b3dfbf4de930fd22b44a2ea7b897f3f26f7b17a381d6fadbca332",1649884,"full_custom_icons","ViaVersion/ViaBackwards",34,null,null),
    new Route("1.21.2-1.21.3","1_Resource_Pack_Java/01_Full_CMD_1.14-1.21.3/GUI(1.21.2-1.21.3).zip","a83e59732af2a815c630e17f6d5df65e80ecbcea","a8ffc47d8a6a8877b182de7d45c84e308f4fd5960a4ef40d27b6a50df3511076",1649884,"full_custom_icons","ViaVersion/ViaBackwards",42,null,null),
    new Route("1.21.4","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.4).zip","ff34e7ac086c156af22a19d765f78222c30461fa","1d0d26db977e2d544e239f164094d4d78ab9428464411480c3497b0a250897be",3110566,"modern_item_definition","ViaVersion/ViaBackwards",46,null,null),
    new Route("1.21.5","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.5).zip","dedf40b5350a95e3e25134f1c69c4f6970ec585f","45ee4c668a6f3d92caf05268cae0804dc9600c79483ae2946679287f017b12ee",3110566,"modern_item_definition","ViaVersion/ViaBackwards",55,null,null),
    new Route("1.21.6","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.6).zip","5f27309918bb6ce695b3fed08127861c1ab98880","1e3a123b714cf04312b28e984fb8ba2e9d2f2dc67307a8411db9d0162100ca5b",3110566,"modern_item_definition","ViaVersion/ViaBackwards",63,null,null),
    new Route("1.21.7-1.21.8","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.7-1.21.8).zip","305fb234b141704f8b37759b089852751541578d","bf5c5708ff5087316d3ea613e724d9094059bdd8f34f200442ccd89815f5ffc0",3110569,"modern_item_definition","ViaVersion/ViaBackwards",64,null,null),
    new Route("1.21.9-1.21.10","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.9-1.21.10).zip","a9af6327b457b6bc3064dcd6d2ffc1910954d0fd","31ba1bdaa66ca0403e941562207fbed6dbb9092bb2819e7db44f2bced5eda43d",3110591,"modern_item_definition","ViaVersion/ViaBackwards",null,69,0),
    new Route("1.21.11","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(1.21.11).zip","107bc721b8979aa82ea410622e719d5d44c4e29f","22cbc9f7bf26c6560ad6caae4c0f58805c122814e4a236a34dcbf02921208bfc",3110587,"modern_item_definition","ViaVersion/ViaBackwards",null,75,0),
    new Route("26.1-26.1.2","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(26.1-26.1.2).zip","9f8bf68083412996c733aa3fbe44e96540684403","ac1dacb805eeb1045954b1a00bf1ded67b2fe181293fe627ce80dc7b4624747d",3110589,"modern_item_definition","ViaVersion/ViaBackwards",null,84,0),
    new Route("26.2","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(26.2).zip","cab4fa7bfd342c19e6b987760356b5fca40bc1b0","c3da4e40145e92f2071410fb5ec867955cd75ac9780a922cd9f622eabca75d2d",3110584,"modern_item_definition","ViaVersion/ViaBackwards",null,88,0),
    new Route("26.3","1_Resource_Pack_Java/02_Modern_1.21.4-26.3/GUI(26.3).zip","cb44527278f3e411105c05acfbec977414fd7087","b56a394fcffc6ed000408150110aafc0ee4bfb4f76809ad2273b2529758af133",3110584,"modern_item_definition","ViaVersion/ViaBackwards",null,97,1)
  );

  private static final BedrockRoute BEDROCK = new BedrockRoute(
      "2_Resource_Pack_Bedrock/Imperial_X_SOL_Bedrock_V11.mcpack",
      "26.30-26.51",
      "26.2",
      "Current Geyser does not support older Bedrock clients."
  );

  public static List<Route> routes() { return ROUTES; }
  public static BedrockRoute bedrock() { return BEDROCK; }

  public static Optional<Route> resolve(String rawVersion) {
    if (rawVersion == null || rawVersion.isBlank()) return Optional.empty();
    Version v = Version.parse(rawVersion);
    if (v == null) return Optional.empty();
    return ROUTES.stream().filter(r -> matches(v, r.range())).findFirst();
  }

  private static boolean matches(Version v, String range) {
    String[] parts = range.split("-", 2);
    Version lo = Version.parse(parts[0]);
    Version hi = parts.length == 1 ? lo : Version.parse(parts[1]);
    return lo != null && hi != null && v.compareTo(lo) >= 0 && v.compareTo(hi) <= 0;
  }

  private record Version(int major, int minor, int patch) implements Comparable<Version> {
    static Version parse(String input) {
      String s = input.trim();
      String[] a = s.split("\\.");
      if (a.length < 2 || a.length > 3) return null;
      try {
        int major = Integer.parseInt(a[0]);
        int minor = Integer.parseInt(a[1]);
        int patch = a.length == 3 ? Integer.parseInt(a[2].replaceAll("[^0-9].*$", "")) : 0;
        return new Version(major, minor, patch);
      } catch (NumberFormatException e) {
        return null;
      }
    }

    @Override public int compareTo(Version o) {
      int c = Integer.compare(major, o.major);
      if (c != 0) return c;
      c = Integer.compare(minor, o.minor);
      return c != 0 ? c : Integer.compare(patch, o.patch);
    }
  }

  private SorterResolver() {}
}
