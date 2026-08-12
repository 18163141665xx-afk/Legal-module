<div align="center">

[![QQ](https://img.shields.io/badge/QQ-3684078503-12B7F5?style=for-the-badge&logo=tencentqq&logoColor=white)](https://wpa.qq.com/msgrd?v=3&uin=3684078503&site=qq&menu=yes)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)]()
[![Forge](https://img.shields.io/badge/Forge-1.20.1-orange?style=for-the-badge&logo=curseforge&logoColor=white)]()
[![Version](https://img.shields.io/badge/Version-1.0.0-4C9AFF?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-Custom-9cf?style=for-the-badge)]()

**🌐 Language / 语言：** [🇺🇸 English](README.en.md) | [🇨🇳 简体中文](README.md)

</div>

# Rule of Law — Minecraft Forge 1.20.1 Mod

Monitors player behavior and runs in-game trials & punishments based on the
*Criminal Law of the People's Republic of China* and the *Constitution*.
A **bedrock prison** (8 cells, force-loaded chunks) is automatically generated at (1000, 180, 1000).
All sentences are **measured in game time** (1 game day = 20 real minutes), capped at **10 game days**.
For issues / contact: QQ 3684078503

---

## 1. Crimes & Punishments

| Trigger | Crime | Law | Punishment |
|---|---|---|---|
| Killing a player | Intentional Homicide | Art. 232 | 8~10 days prison + disenfranchisement (mute) + fine; **3rd offense → death penalty** |
| Killing a villager | Intentional Homicide (villager) | Art. 232 | 5~8 days prison |
| Attacking a player | Intentional Injury | Art. 234 | 1st: warning+fine → 2nd: 2 days control → 3rd+: 1~3 days prison |
| Killing panda/axolotl/dolphin/turtle/polar bear/sniffer | Endangering Protected Wildlife | Art. 341 | 2~5 days prison + fine |
| Hunting >10 common animals in a day | Illegal Hunting | Art. 341(2) | fine + 1 day control; repeat: 1 day detention |
| Placing fire (flint & steel) | Arson | Art. 114 | 3~5 days prison |
| Detonating TNT | Explosion | Art. 115 | 5~8 days prison (warning first); **3rd offense → death penalty** |
| Opening a chest within 6 blocks of another player | Theft | Art. 264 | 1st: warning → 2nd: fine → 3rd+: 1~2 days prison |
| Breaking blocks within 32 blocks of a village | Intentional Property Damage | Art. 275 | 1st: warning → 2nd: fine → 3rd+: 1~2 days prison |
| Spamming 5 messages within 8 seconds | Provoking Trouble | Art. 293 | mute 1 day; repeat: mute 2 days + control 1 day |

> **Fine = losing HP** (no longer costs XP levels). **Death penalty**: triggered on the 3rd
> offense of Intentional Homicide / Explosion — the player is killed instantly and the spawn
> point resets to the world spawn.

**Constitution elements**: arrest broadcasts cite Art. 37 (no arrest without court decision);
pardon commands cite Arts. 67 & 80.

**Penalty types**: Warning / Fine (HP loss) / Control (community correction: slowness + weakness) /
Disenfranchisement (mute) / Imprisonment (bedrock prison) / Death penalty (instant kill).
**Repeat offenders**: the 3rd offense of the same crime gets the maximum sentence (persisted in save data);
Homicide & Explosion get the death penalty on the 3rd offense.
**Keep Inventory**: the mod force-enables "no drops on death" for everyone (items + XP kept),
no matter the cause of death (execution, fall damage, being killed, etc.).

**Prison facilities**: each cell has a red bed, supply chest (16 bread / 8 apples / 4 cooked beef),
glowstone + floor torch + hanging lantern lighting.
**Respawn handling**: spawn is locked inside the cell while imprisoned; on release / pardon / death penalty
the spawn resets to the world spawn; even if the sentence expires while offline, the player is
automatically sent back to spawn on login instead of being stuck in the prison.

**Anti-escape system**: bedrock shell + per-second patrol detection (teleport back instantly) +
**+1 day for attempted escape** (30s cooldown, total capped at 10 days) + ender pearl/chorus fruit
banned + spawn locked in cell + block breaking disabled while serving.
**Commutations (good behavior)**: per Art. 78, staying obediently in your cell (no escape attempts)
reduces the sentence by **1 day per full game day**, down to **half of the original sentence**;
1 escape wipes your good-behavior progress, 2 escapes permanently revoke commutation eligibility.

## 2. Commands

- `/law` — help
- `/law crimes` — list all crimes & laws
- `/law prisoners` — list current prisoners & remaining time
- `/law pardon <player>` — grant a pardon (requires OP)


> Detailed tutorial, FAQ and customization points (prison location, max sentence, adding crimes)
> are covered in this README. Note: shared bases may trigger false positives — increase the cooldown
> or add a whitelist in `BehaviorMonitor.onContainerOpen`.

### Dev / Debug (optional)
```bash
./gradlew runClient   # launch a test client with the mod
./gradlew runServer   # launch a test server
```

## 4. FAQ

- **Change prison location/size**: edit `PrisonManager.ORIGIN` and `CELLS`.
- **Change max sentence**: edit `RuleOfLawMod.MAX_PRISON_DAYS`.
- **Add a new crime**: add an enum to `CrimeType` → add an event listener in `BehaviorMonitor` → add a sentencing branch in `CourtSystem.decideAndApply`.

## 5. Project Structure

```
RuleOfLawMod/
├── build.gradle / settings.gradle / gradle.properties   # build config (ForgeGradle 6)
└── src/main/
    ├── java/com/ruleoflaw/
    │   ├── RuleOfLawMod.java          # main class (MOD_ID, global constants)
    │   ├── crime/CrimeType.java       # crime enum (crime, law article, sentencing range)
    │   ├── court/CourtSystem.java     # court: prosecution, judgment, execution (fine/mute/control/prison)
    │   ├── monitor/BehaviorMonitor.java# behavior monitoring: all game event entry points
    │   ├── prison/PrisonManager.java  # bedrock prison: generation, imprisonment, release, anti-escape
    │   ├── prison/PrisonData.java     # SavedData: prisoners/mutes/control/offense records
    │   └── command/LawCommands.java   # /law commands (incl. pardon)
    └── resources/
        ├── META-INF/mods.toml         # mod metadata
        └── pack.mcmeta
```

> This mod is for entertainment / legal-education purposes; law citations are gamified simplifications,
> not legal advice.
> This README covers both the "how to build" and the "how to play" parts.
