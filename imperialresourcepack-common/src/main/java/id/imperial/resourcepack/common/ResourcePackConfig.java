package id.imperial.resourcepack.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Small, dependency-free reader for this plugin's flat YAML configuration. */
public record ResourcePackConfig(boolean enabled, String packsDirectory, String activePack,
                           boolean deliveryEnabled, boolean required, String prompt, long sendDelayMs,
                           boolean resendOnServerSwitch, boolean hostingEnabled, String bind, int port,
                           String hostPath, String publicUrl, boolean requireMcmeta, long maxSizeBytes) {
  public static ResourcePackConfig load(Path file) throws IOException {
    Map<String, String> values = new HashMap<>(); String section = "";
    for (String raw : Files.readAllLines(file)) {
      String line = raw.strip(); if (line.isEmpty() || line.startsWith("#")) continue;
      if (!raw.startsWith(" ") && line.endsWith(":")) { section = line.substring(0, line.length() - 1); continue; }
      int colon = line.indexOf(':'); if (colon < 1) continue;
      String key = (raw.startsWith(" ") ? section + "." : "") + line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();
      if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
      values.put(key, value);
    }
    return new ResourcePackConfig(bool(values,"enabled",true), get(values,"packs-directory","packs"), get(values,"active-pack",""),
        bool(values,"delivery.enabled",true), bool(values,"delivery.required",false), get(values,"delivery.prompt","<gold>Imperial X SOL</gold> <gray>Resource Pack</gray>"),
        positiveLong(values,"delivery.send-delay-ms",1500), bool(values,"delivery.resend-on-server-switch",false), bool(values,"hosting.enabled",true),
        get(values,"hosting.bind","0.0.0.0"), port(values), normalizedPath(get(values,"hosting.path","/pack")), get(values,"hosting.public-url",""),
        bool(values,"validation.require-pack-mcmeta",true), positiveLong(values,"validation.max-size-mb",256) * 1024L * 1024L);
  }
  private static String get(Map<String,String> v,String k,String d){return v.getOrDefault(k,d);}
  private static boolean bool(Map<String,String> v,String k,boolean d){return Boolean.parseBoolean(get(v,k,String.valueOf(d)));}
  private static long positiveLong(Map<String,String> v,String k,long d){try { long n=Long.parseLong(get(v,k,"")); return n >= 0 ? n : d; } catch(NumberFormatException e){return d;}}
  private static int port(Map<String,String> v){long n=positiveLong(v,"hosting.port",8199);return n>0&&n<65536?(int)n:8199;}
  private static String normalizedPath(String p){return p.startsWith("/")?p:"/"+p;}
}
