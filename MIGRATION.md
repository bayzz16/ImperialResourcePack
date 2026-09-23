# Migration to 1.2.0

1. Replace the old Paper JAR with `ImperialResourcePack-1.2.0.jar`.
2. Keep `plugins/ImperialResourcePack/config.yml`; existing settings are preserved.
3. Put Java ZIP packs under the configured `packs-directory`. Nested folders are supported.
4. `active-pack` is fallback only when version-mapping cannot resolve a client pack.
5. Reloading rescans/rebuilds the cache and does not resend packs to online players.
6. Use `/irp diagnose 1.21.10` and `/irp diagnose hosting` after migration.
7. Bedrock/Floodgate/Geyser handling is intentionally outside this plugin.
