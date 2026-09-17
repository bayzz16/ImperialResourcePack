# ImperialResourcePack

Two separate, lightweight Java 25 plugins for serving a Minecraft Java resource-pack ZIP from an internal HTTP server:

| Plugin | Use it on | Output |
|---|---|---|
| Velocity | Velocity 4.1.x proxy/network-wide delivery | `ImperialResourcePack-Velocity.jar` |
| Paper | Paper 1.21.x server delivery | `ImperialResourcePack-Paper.jar` |

They are separate Maven modules and separate plugin JARs. The Velocity JAR has no Paper API and the Paper JAR has no Velocity API. Java clients always receive a resource pack through a reachable URL; neither plugin can force a client that rejects downloading/applying a pack.

## Installation

### Velocity
1. Copy `ImperialResourcePack-Velocity.jar` to the Velocity `plugins/` directory.
2. Start the proxy, then put a ZIP in `plugins/ImperialResourcePack/packs/`.
3. Set `hosting.public-url` and run `/irp reload`.

### Paper
1. Copy `ImperialResourcePack-Paper.jar` to the Paper `plugins/` directory.
2. Start the server, then put a ZIP in `plugins/ImperialResourcePack/packs/`.
3. Set `hosting.public-url` and run `/irp reload`.

If `active-pack` is empty and exactly one ZIP exists, that ZIP is selected automatically. With more than one ZIP, set `active-pack` explicitly; no arbitrary pack is served.

## Configuration

```yaml
enabled: true
packs-directory: "packs"
active-pack: ""
delivery:
  enabled: true
  required: false
  prompt: "<gold>Imperial X SOL</gold> <gray>Resource Pack</gray>"
  send-delay-ms: 1500
hosting:
  enabled: true
  bind: "0.0.0.0"
  port: 8199
  path: "/pack"
  public-url: "http://play.imperial.biz.id:8199/pack"
validation:
  require-pack-mcmeta: true
  max-size-mb: 256
```

`/pack` streams only the active validated ZIP (`GET` and `HEAD`). `/status` reports pack availability. The server is not an arbitrary file server.

## Network and Pterodactyl

Port 8199 must be reachable from players. For Pterodactyl, add an allocation for it, or expose the configured path with a reverse proxy and use that external URL as `public-url`. A successful local bind does not mean players can reach it.

## Commands

All commands require `imperialresourcepack.admin`:

* `/irp reload` — reload config, rescan/validate, update pack and hosting.
* `/irp status` — show active pack, SHA-1, UUID, and HTTP state.
* `/irp validate` — validate the active pack.
* `/irp info` — show pack identity information.

## Validation and caching

The common module validates ZIPs without extracting them, requires `pack.mcmeta` by default, rejects corrupt ZIPs and traversal/absolute paths, and checks configured size. SHA-1 is streamed, cached by file size and modification time, and used in the client offer. The UUID is deterministically derived from SHA-1, so it remains stable until the ZIP changes.

## Troubleshooting

* **No active pack:** confirm it is a `.zip` directly in `packs/`, or configure `active-pack` when multiple ZIPs exist.
* **Pack not offered:** check `delivery.enabled`, `public-url`, and player permissions/client settings.
* **Download failed:** test the public URL from outside your host and configure a Pterodactyl allocation/reverse proxy.
* **Validation failed:** recreate the ZIP with a root `pack.mcmeta` and remove unsafe entries.

## Build notes

The Velocity module uses the official `com.velocitypowered:velocity-api:4.1.1-SNAPSHOT` artifact from the PaperMC Maven repository. The official Velocity source revision for the 4.1.1 line identifies itself as `4.1.1-SNAPSHOT`; this is used instead of an unverified non-snapshot `4.1.1` coordinate. Both platform APIs are Maven `provided` dependencies. GitHub Actions verifies that the final artifacts exist, are readable JARs, contain their platform metadata/configuration, and do not bundle the other platform API.
