# HexaCord Forward-Port: Conflict Resolution Documentation

## Overview

This document describes the forward-port of HexaCord (HexagonMC/BungeeCord master) onto the latest upstream BungeeCord (SpigotMC/BungeeCord master, commit `edb28b4a`, version `26.1-R0.1-SNAPSHOT`).

**Branch**: `master` (from `origin/master`)
**Merge base**: `ff5727c5ef9c0b56ad35f9816ae6bd660b622cf0` (HexaCord "Revert broken chat PR and align with Spigot")
**HexaCord commits**: 244 commits since divergence
**Upstream commits**: 489 commits since divergence

## Approach

Due to the significant architectural changes between HexaCord (2016-era) and upstream BungeeCord (2026), a direct cherry-pick was infeasible. The approach was to manually apply each HexaCord feature change to the modern upstream codebase, preserving both HexaCord compatibility behavior and upstream improvements.

## Major Conflicts Resolved

### 1. Protocol Layer — 1.7 Client Support

**Files modified**:
- `ProtocolConstants.java` — Added `MINECRAFT_1_7_2 = 4` and `MINECRAFT_1_7_6 = 5` constants; added "1.7.x" to supported versions.
- `DefinedPacket.java` — Added `writeArrayLegacy()` and `readArrayLegacy()` methods using `writeVarShort`/`readVarShort`.
- `Protocol.java` — Added 23 `MINECRAFT_1_7_2` packet mappings (replacing first `MINECRAFT_1_8` mapping per packet for GAME/HANDSHAKE/STATUS protocols). LOGIN protocol mappings correctly left as `MINECRAFT_1_8`. Title (0x45) and PlayerListHeaderFooter (0x47) correctly NOT changed.
- `Chat.java`, `ClientSettings.java`, `EncryptionRequest.java`, `EncryptionResponse.java`, `KeepAlive.java`, `LoginSuccess.java`, `PlayerListItem.java`, `PluginMessage.java`, `ScoreboardObjective.java`, `ScoreboardScore.java`, `TabCompleteRequest.java`, `Team.java` — Added 1.7 compatibility branches.

**Key decisions**:
- Only the first mapping per packet was changed to `MINECRAFT_1_7_2` (not all mappings), matching HexaCord's original intent.
- LOGIN protocol packets were NOT changed to 1.7 (login protocol is identical between 1.7 and 1.8).
- Title and PlayerListHeaderFooter packets were NOT changed (these are 1.8+ features).

### 2. Entity Rewriting — 1.7 Entity Maps

**Files created**:
- `EntityMap_1_7_2.java` — Entity ID rewriting for 1.7.2 protocol.
- `EntityMap_1_7_6.java` — Entity ID rewriting for 1.7.6 protocol.

**Files modified**:
- `EntityMap.java` — Added 1.7 entity map cases + negative packet ID guard (`if ( packetId >= 0 )`).

**Key decisions**:
- Removed `@SuppressFBWarnings` annotation (findbugs not available in upstream).
- Changed `Property` import from `net.md_5.bungee.protocol.Property` to `net.md_5.bungee.protocol.data.Property` (upstream moved Property to data subpackage).

### 3. Forge Handshake Support

**Files modified**:
- `ForgeConstants.java` — Added `FML_LOGIN_PROFILE` and `EXTRA_DATA` constants.
- `ForgeServerHandler.java` — Added `isHandshakeComplete()` method.
- `ForgeServerHandshakeState.java` — Added DONE state logging.
- `ServerConnector.java` — Added Forge IP forwarding (property array extension with `FML_LOGIN_PROFILE`/`EXTRA_DATA`), 1.7 brand packet (`MinecraftOutput` + `MC|Brand`), null-safe scoreboard type.
- `UserConnection.java` — Added 1.7 FML token handling in constructor.
- `PlayerInfoSerializer.java` — Added `protocol` field + constructor, 1.7 UUID handling (undashed UUID for `protocol == 4`).

**Key decisions**:
- Forge IP forwarding extends the property array with two additional properties (`FML_LOGIN_PROFILE` and `EXTRA_DATA`) when the client has an FML token in the handshake.
- 1.7 brand packet uses `MinecraftOutput.writeStringUTF8WithoutLengthHeaderBecauseDinnerboneStuffedUpTheMCBrandPacket()` (no length prefix, as 1.7 expects).
- Scoreboard objective type is null-safe (`objective.getType() != null ? ... : null`).

### 4. Login Flow — 1.7 Compatibility

**Files modified**:
- `UserConnection.java` — Display name length check (16 chars max for 1.7), action bar version guard (1.8+), tab header version guard (1.8+), compression version guard (1.8+).
- `ServerConnector.java` — Login response handling with 1.7 branch for brand packet.

**Key decisions**:
- 1.7 clients cannot handle action bar, tab headers, or compression — all guarded with `MINECRAFT_1_8` checks.
- Display name length check only applies to 1.7 clients (1.8+ allows longer names).

### 5. Connection Handling — Configuration

**Files modified**:
- `Configuration.java` — Added `customServerName`, `alwaysHandlePackets`, `pluginChannelLimit` fields + getters.
- `ProxyConfig.java` — Added `getCustomServerName()`, `getAlwaysHandlePackets()`, `getPluginChannelLimit()` interface methods.
- `BungeeCord.java` — Added `gson` and `gsonLegacy` fields; changed `getName()` to return `config.getCustomServerName()`.
- `PingHandler.java` — Updated to use `BungeeCord.getInstance().gson`/`gsonLegacy`; removed static `gson` field.
- `InitialHandler.java` — Updated gson usage + channel limit config (`getPluginChannelLimit()`).
- `UpstreamBridge.java` — Added `getAlwaysHandlePackets()` check.
- `ServerUnique.java` — Added username collection + 1.7 packet splitting.
- `DownstreamBridge.java` — Added null-safe scoreboard type + 1.7 brand handling.
- `CommandBungee.java` — Updated message to use `getName()` + 1.7 support line.
- `QueryHandler.java` — Updated version string to include custom server name.
- `BungeeTitle.java` — Added 1.8 version check for title sending.

**Key decisions**:
- `gson` and `gsonLegacy` use `VersionedComponentSerializer.getDefault().getGson().newBuilder()` to preserve upstream's Gson configuration while allowing HexaCord-specific serialization.
- `getName()` returns `config.getCustomServerName()` (default "HexaCord") instead of hardcoded "BungeeCord".

### 6. Module Management — Travis CI

**Files modified**:
- `ModuleManager.java` — Added `TravisCiModuleSource` registration; changed default module URLs from `jenkins://` to `travis-ci://`.

**Files created**:
- `TravisCiModuleSource.java` — Downloads modules from virtualWinter/BungeeCord GitHub releases.

**Key decisions**:
- Travis CI module source downloads from `https://github.com/virtualWinter/BungeeCord/releases/download/v{version}/{module}.jar`.
- Default module URLs changed from `jenkins://` to `travis-ci://` to match HexaCord's release infrastructure.

### 7. Update Checking — BungeeCordLauncher

**Files modified**:
- `BungeeCordLauncher.java` — Replaced `SimpleDateFormat`/`Calendar` build-date check with GitHub API version check + self-compiled version handling.

**Key decisions**:
- Self-compiled builds (version "unknown") get a 2-second warning.
- Release builds check `https://api.github.com/repos/virtualWinter/BungeeCord/releases/latest` for newer versions.
- Network failures fall back to a 2-second warning (graceful degradation).

## Compilation Fixes Applied

1. **PlayerListItem.java**: `displayName` is `BaseComponent` in upstream (was `String` in HexaCord) — wrapped with `new TextComponent(readString(buf))`. `ping` is `Integer` in upstream — cast `buf.readShort()` to `int`.
2. **ServerConnector.java**: `Property` import changed from `net.md_5.bungee.protocol.Property` to `net.md_5.bungee.protocol.data.Property` (upstream moved the class).
3. **EntityMap_1_7_6.java**: Removed `@SuppressFBWarnings` annotation (findbugs not available); fixed `Property` import.
4. **UserConnection.java**: `setTabHeader(BaseComponent[], BaseComponent[])` delegates to single-component version (upstream `ChatComponentTransformer.transform()` returns `BaseComponent[]`, not `BaseComponent`).
5. **ServerUnique.java**: `setDisplayName()` expects `BaseComponent` — wrapped username with `new TextComponent(username)`.
6. **Checkstyle**: Removed unused imports (`GsonBuilder`, `Arrays`) and redundant same-package imports (`BungeeServerInfo`, `PlayerInfoSerializer`); fixed import ordering in `BungeeCordLauncher.java` and `ServerConnector.java`.

## Not Applied (Upstream Already Handles)

1. **Negative packet ID fix (`30adece6`)**: Upstream Protocol.java already has `if ( id > MAX_PACKET_ID || id < 0 )` guard in `getPacket()`.
2. **ServerConnectRequest annotation (`9bc41433`)**: Upstream uses `ProxyServer.getInstance().getConfig().getServerConnectTimeout()` instead of annotation-based default.
3. **ServerConnector dimension cast removal (`b7f8db9f`)**: Upstream no longer casts `login.getDimension()` to byte.

## Build Verification

- **Java**: OpenJDK 21.0.11
- **Maven**: 3.9.16
- **Build command**: `mvn clean install -DskipTests`
- **Result**: SUCCESS — all modules compile with no errors
