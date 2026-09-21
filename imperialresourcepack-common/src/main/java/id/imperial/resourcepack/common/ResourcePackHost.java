package id.imperial.resourcepack.common;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public final class ResourcePackHost implements AutoCloseable {
  private final ResourcePackManager manager;
  private final Logger logger;
  private HttpServer server;
  private Path packsDirectory;
  private ResourcePackConfig config;

  public ResourcePackHost(ResourcePackManager manager, Logger logger) {
    this.manager = manager;
    this.logger = logger;
  }

  public synchronized boolean start(ResourcePackConfig config, Executor executor, Path packsDirectory) {
    close();
    this.config = config;
    this.packsDirectory = packsDirectory.toAbsolutePath().normalize();
    if (!config.hostingEnabled()) return false;
    try {
      Files.createDirectories(this.packsDirectory);
      server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
      server.createContext(config.hostPath(), this::pack);
      server.createContext("/status", this::status);
      server.setExecutor(executor);
      server.start();
      logger.info("[ImperialResourcePack] HTTP server listening on "
          + config.bind() + ":" + config.port());
      return true;
    } catch (IOException e) {
      logger.severe("[ImperialResourcePack] ERROR: HTTP server could not start: " + e.getMessage());
      server = null;
      return false;
    }
  }

  private void pack(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equalsIgnoreCase("GET")
        && !exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }

    ActivePack selected = resolveRequestedPack(exchange.getRequestURI());
    if (selected == null) {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }

    exchange.getResponseHeaders().set("Content-Type", "application/zip");
    exchange.getResponseHeaders().set("Content-Length", String.valueOf(selected.size()));
    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
    exchange.getResponseHeaders().set("ETag", """ + selected.sha1Hex() + """);
    exchange.sendResponseHeaders(200, exchange.getRequestMethod().equalsIgnoreCase("HEAD") ? -1 : selected.size());

    if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
      try (InputStream in = Files.newInputStream(selected.file());
           OutputStream out = exchange.getResponseBody()) {
        in.transferTo(out);
      }
    } else {
      exchange.close();
    }
  }

  private ActivePack resolveRequestedPack(URI uri) {
    String filename = queryParam(uri.getRawQuery(), "pack");
    if (filename == null || filename.isBlank()) return manager.active();
    Path file = manager.resolveValid(packsDirectory, filename, config);
    if (file == null) return null;
    var prepared = manager.prepare(file, packsDirectory, config);
    return prepared.success() ? prepared.pack() : null;
  }

  private static String queryParam(String rawQuery, String wanted) {
    if (rawQuery == null || rawQuery.isBlank()) return null;
    for (String item : rawQuery.split("&")) {
      int equals = item.indexOf('=');
      String key = equals < 0 ? item : item.substring(0, equals);
      if (!wanted.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) continue;
      String value = equals < 0 ? "" : item.substring(equals + 1);
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    return null;
  }

  private void status(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestMethod().equalsIgnoreCase("GET")
        && !exchange.getRequestMethod().equalsIgnoreCase("HEAD")) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    ActivePack p = manager.active();
    byte[] body = (p == null
        ? "{"active":false}"
        : "{"active":true,"file":"" + escape(p.file().getFileName().toString())
            + "","sha1":"" + p.sha1Hex() + ""}")
        .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, exchange.getRequestMethod().equalsIgnoreCase("HEAD") ? -1 : body.length);
    if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    } else {
      exchange.close();
    }
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace(""", "\\"");
  }

  public static String urlForPack(String publicUrl, String filename) {
    if (publicUrl == null || publicUrl.isBlank() || filename == null) return publicUrl;
    String separator = publicUrl.contains("?") ? "&" : "?";
    return publicUrl + separator + "pack=" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
  }

  public synchronized boolean running() { return server != null; }

  public synchronized void close() {
    if (server != null) {
      server.stop(1);
      server = null;
    }
  }
}
