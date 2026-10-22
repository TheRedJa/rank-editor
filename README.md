# Rank Editor

An in-game editor for [FTB Utilities](https://www.curseforge.com/minecraft/mc-mods/ftb-utilities-forge) ranks and permissions on Minecraft 1.12.2, so you no longer have to hand-edit `ranks.txt` and remember node names.

**Requires** [Cleanroom](https://github.com/CleanroomMC/Cleanroom) 0.7.x, FTB Library 5.4.7.2 and FTB Utilities 5.4.1.131. Install it on both the server and the clients.

## Features

- Rank list sorted by power. It marks the default player and OP ranks, and shows whether each is set explicitly or picked automatically by FTB Utilities.
- A searchable table of every permission node the server knows: Forge permissions, every command (`command.<mod>.<name>`) and FTB rank configs. The table shows defaults and descriptions.
- For each node it shows the rank's own value and the effective value, plus where that value comes from (`true <- vip`, a wildcard match, or a default). Lookup works exactly like FTB Utilities' own.
- Lets you edit power, parents, default flags, comments and values. Booleans toggle with a click; other values get a typed editor.
- Create, rename, duplicate and delete ranks, and assign ranks to players, including offline players who have joined before.
- Changes are staged until you apply them, with undo and revert. A batch is validated as a whole before it is saved. Parent loops, a non-numeric power and values FTB Utilities would mangle are all rejected.
- Changes apply immediately, with no `/ranks reload`. Every applied change is written to the server log.

## Usage

Open it with `/rankeditor`, from the FTB Library **Admin Panel**, or with the sidebar button (shown to ops only).

| Permission node   | Default | Meaning |
|-------------------|---------|---------|
| `rankeditor.view` | OP      | Open the editor (read-only) |
| `rankeditor.edit` | OP      | Apply changes |

`rankeditor.edit` lets the holder grant themselves any permission, so treat it like OP.

Keyboard shortcuts: `Ctrl+S` apply, `Ctrl+Z` undo, `Ctrl+F` search, `Esc` close.

## Building

```sh
# FTB Library and FTB Utilities are not redistributed: copy FTBLib-5.4.7.2.jar and FTBUtilities-5.4.1.131.jar into libs/
./gradlew build   # JDK 25; output: build/libs/rankeditor-<version>.jar
```

## License

The mod is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). You may use, modify and share it for any noncommercial purpose, which includes running it on a server.

The build scripts (`build.gradle`, `settings.gradle`) are derived from [CleanroomModTemplate](https://github.com/CleanroomMC/CleanroomModTemplate) and remain under [LGPL-3.0](licenses/LGPL-3.0-and-GPL-3.0.txt).

This project is not affiliated with Feed The Beast.
