# ImperialResourcePack 1.2.0

- Java-only per-player version routing.
- Recursive ZIP discovery with duplicate-basename protection.
- Exact path, root filename, recursive filename, and version-route resolution.
- Root pack.mcmeta validation and ZIP safety checks.
- Cached validation/SHA-1 keyed by file size and last-modified time.
- Async prewarm/rescan and non-resending reload.
- Multi-pack HTTP hosting with encoded pack selection.
- Added /irp paths, /irp diagnose, /irp rescan, and /irp player diagnostics.
- Startup route audit for all 27 configured Java routes.
- Removed unused Bedrock/Floodgate/Geyser route model from the Java-only plugin.

## Unreleased - Per-player persistent packs
- Added UUID-based player-packs.yml persistence for Java players.
- /irp use <version> now prepares and sends only the executing player's selected pack without changing Main Active Pack.
- JOIN restores the saved personal pack; unavailable personal packs fall back to Main Active Pack without deleting the preference.
- Separated imperialresourcepack.use from admin-only subcommands.
- Auto-reload continues to rescan/cache only and does not resend packs to online players.
