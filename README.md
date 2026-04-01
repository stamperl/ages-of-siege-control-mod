# Ages Of Siege Control Mod

The `Ages Of Siege Control Mod` is the custom Fabric gameplay layer that drives the `Ages Of Siege` campaign loop.

It adds settlement claiming, defender management, siege planning, staged assaults, and quest-style siege progression on top of vanilla Minecraft and the wider pack.

## What The Mod Does

- lets the player claim a base with the `Settlement Standard`
- lets the player place a `Raid Rally Banner` to define the attacker staging point
- adds an `Army Ledger` screen for:
  - defender placement and post management
  - tactical map review
  - siege chapter / quest-node selection
  - siege preview, staging, locking, and starting
- tracks campaign progress across ages and siege routes
- supports replayable raids, age-defining milestone sieges, and war-supplies rewards
- includes defender tokens, recall tooling, and runtime AI for soldiers and archers
- stores persistent settlement, defender, and siege state in the world save

## Current Gameplay Flow

1. Craft or obtain a `Settlement Standard`
2. Claim the settlement you want to defend
3. Place a `Raid Rally Banner`
4. Open the `Army Ledger`
5. Choose a siege node from the quest-style campaign view
6. Place and prepare defenders
7. Lock the siege, then start it from the ledger
8. Win raids to unlock harder routes and age-defining sieges

## Commands

- `/agesofsiege status`
- `/agesofsiege setbase`
- `/agesofsiege clearbase`
- `/agesofsiege resetprogress`
- `/agesofsiege clearprogress`

These are mainly useful for testing and debugging while the campaign is still being built out.

## Dependencies

Required runtime dependencies:

- Minecraft `1.20.1`
- Fabric Loader `0.18.4` or newer
- Fabric API `0.92.7+1.20.1`
- Java `17`

Build-time dependencies:

- Gradle wrapper in this repo
- Yarn mappings `1.20.1+build.10`
- Fabric Loom `1.6-SNAPSHOT`

## Build

```powershell
.\gradlew.bat build
```

The built jar is written to:

- [build\libs\ages-of-siege-control-0.1.0.jar](C:\Users\Stamp\OneDrive\Documents\Ages%20Of%20Siege%20Control%20Mod\build\libs\ages-of-siege-control-0.1.0.jar)

## Local Testing Paths

Common local install targets used in this workspace:

- [Prism instance mods folder](C:\Users\Stamp\AppData\Roaming\PrismLauncher\instances\Ages%20Of%20Siege-0.1.0\minecraft\mods)
- [Pack Prism mods folder](C:\Users\Stamp\OneDrive\Documents\Ages%20Of%20Siege%20Pack\tools\prism\instances\Ages%20Of%20Siege-0.1.0\minecraft\mods)

If present, the local quest configuration is generated/read from:

- [config\ages_of_siege\siege_quests.json](C:\Users\Stamp\OneDrive\Documents\Ages%20Of%20Siege%20Control%20Mod\config\ages_of_siege\siege_quests.json)

## Repository

- Local workspace: [Ages Of Siege Control Mod](C:\Users\Stamp\OneDrive\Documents\Ages%20Of%20Siege%20Control%20Mod)
- GitHub: [stamperl/ages-of-siege-control-mod](https://github.com/stamperl/ages-of-siege-control-mod)
