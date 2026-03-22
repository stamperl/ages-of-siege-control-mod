# Ages Of Siege Control Mod

This repository contains the custom Fabric gameplay mod for `Ages Of Siege`.

## First Slice

The first playable slice keeps the opening close to vanilla Minecraft:

- start a world normally
- gather resources and build a shelter
- choose the base you want to defend later
- mark that base with the `Settlement Standard` item or a command

No siege starts automatically yet. This mod only lays the first clean foundation for base ownership, later siege waves, and age progression.

## Main Features

- persistent world save data for the claimed base
- `Settlement Standard` item to claim a base
- `/agesofsiege status`
- `/agesofsiege setbase`
- `/agesofsiege clearbase`
- `/give @s ages_of_siege:settlement_standard`

## Build

```powershell
.\gradlew.bat build
```

The built jar appears in `build\libs\`.

## Local Pack Testing

Copy the remapped jar from `build\libs\` into your Prism instance `mods` folder to test it with the pack.

You can also run:

```powershell
.\install-to-prism.bat
```
