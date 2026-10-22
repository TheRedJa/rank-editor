# Rank Editor GUI spec (v0.1)

Goal: replace hand-editing `ranks.txt` / `players.txt` and remembering node names. Discovery and search of nodes is the core value.

## Data access (fixed API, do not change semantics)
- `ClientEditorSession` (`client/ClientEditorSession.java`) is the only data source and sink.
  - `server()` → `Snapshot` (`canEdit`, `ranksActive`, `fingerprint`, `state`, `nodes`), `preview()` → `RankState` with staged edits applied (render THIS).
  - `stage(Edit...)` → `null` on success, else an error string to show (status line / dialog). Never mutate `RankState` directly.
  - `undo()`, `revert()`, `applyToServer()`, `requestRefresh()`, `hasChanges()`, `staged()`, `canEdit()`, `isApplying()`, `status()`, `statusIsError()`, `problems()`.
  - GUI must call `ClientEditorSession.onGuiClosed(this)` exactly when the editor is really closed (NOT when a child popup / yes-no / value editor temporarily replaces the screen).
  - Session calls `gui.onSessionChanged()` after any data/status change; GUI rebuilds affected widgets (keep selection, search text, scroll position where possible).
- Model (`model/`): `RankState` (`ranks`, `players`, `playerInfo`, `power(h)`, `parents(h)`, `actualParents(h)`, `defaultPlayerRank()`, `defaultOpRank()`, `explicitDefault(flag)`, `ranksByPower()`, `displayName(h)`), `Holder` (`id`, `player`, `comment`, `entries`, `local(node)`, `parentIds()`), `Resolver.resolve(state, holder, node)` → `Resolution(value, source, matchedNode, cycle)`, `NodeInfo` (`node`, `kind`, `type`, `playerDefault`, `opDefault`, `description`, `variants`, `min`, `max`, `isBoolean()`, `defaultFor(op)`, `namespace()`), `Edit` factories (`set`, `comment`, `createRank`, `deleteRank`, `renameRank`), `RankState.isValidRankName`, special nodes `RankState.NODE_PARENT/NODE_POWER/NODE_DEFAULT_PLAYER/NODE_DEFAULT_OP`.
- Changed-marker: a holder/entry is "pending" when it differs between `server().state()` and `preview()` (compare `Holder.entries.get(node)` / `Holder.equals`).

## Layout (use nearly the whole scaled screen; recompute on resize)
- Top bar: tabs **Ranks** | **Players**. Right side: pending count ("3 changes"), buttons Undo, Revert, Apply (disabled when nothing staged / applying / read-only), Refresh. Badge "READ-ONLY" when `!canEdit()`.
- Warning strip when `!server().ranksActive()`: "FTB Utilities ranks are disabled (ftbutilities.cfg ranks.enabled) — changes are saved but have no effect".
- Left column (~30%, min 140px): list for the active tab.
  - Ranks: `ranksByPower()`; row = `name` + power right-aligned + markers: default player rank, default OP rank (explicit = bright, implicit fallback = dim, tooltip explains "implicit: lowest/highest power rank because no rank sets default_*_rank"). Pending ranks yellow. "+ New rank" button at the top.
    Right-click menu: Rename…, Duplicate…, Delete (confirm), Make default player rank, Make default OP rank.
  - Players: search box; online first, then by name; green dot online, "OP" tag, faint list of the player's own parent ranks. Click selects.
- Right area: header for the selection, then the permission table.
  - Rank header: name; Power (int input, stage on Enter/focus loss); Parents as chips (× removes) + "+" opens a menu of ranks that are not already parents and not itself; Comment (text input); toggles "Default player rank" / "Default OP rank" (setting one stages `true` on this rank and `""` on the rank that currently has it explicitly, in ONE `stage(...)` call).
  - Player header: name, UUID (small, grey), OP/online state, Ranks chips (the player's `parent` entry) + "+", and a grey line "Effective: " + `actualParents(h)` names, marking ones that come from defaults.
  - Permission table:
    - Search box (space-separated terms, all must match node or description, case-insensitive) + filter cycle button: All / Set here / Set in chain (effective value comes from a rank, not default) / Pending.
    - Rows = catalog nodes ∪ every node used in any holder's entries, minus the special nodes (parent, power, default_player_rank, default_op_rank). Sorted by node. Unknown (not in catalog) nodes are shown with type "?" and treated as strings, except values true/false render as booleans.
    - If the search text is a valid node that is not listed, show a first row "+ Add '<text>'" that opens the value editor for it.
    - Columns: node (trimmed to width, full text + kind + type + description + defaults in tooltip) | this holder's own value ("—" when unset) | effective value with source: `true ← vip`, `false ← * (admin)` for wildcard matches (show matchedNode), `default: false` (players: `defaultFor(op)`; ranks: `default P:false OP:true`). Green for "true", red for "false", grey for unset/default. Pending rows get a yellow left marker.
    - Left click own-value cell: boolean nodes cycle unset → true → false → unset; other types open the value editor. Right click row: Set true / Set false (booleans), Set value…, Clear, Edit comment… (only when set here), Copy node.
    - Value editor: FTBLib `GuiEditConfigValue` with a fitting `ConfigValue` (ConfigInt with min/max, ConfigStringEnum with variants, ConfigString otherwise); empty string = clear. Show stage errors.
  - Very large lists: only build row widgets for the filtered set; keep it responsive with ~5000 nodes (consider building only visible rows if needed).
- Bottom status line: `status()` (red when `statusIsError()`), and if `problems()` is non-empty a "⚠ N problems in rank files" label with the list in its tooltip. The ⚠ char may not render in MC fonts; use "!" if unsure.
- Dialogs: New rank (name + power, optional "copy from" when invoked via Duplicate), Rename, Delete confirm. Validate names with `RankState.isValidRankName` before staging; still show `stage()` errors.
- Keys: Ctrl+S apply, Ctrl+Z undo (only when no text box is focused). Escape/close with staged changes asks "Discard N unapplied changes?" first.
- Read-only mode: everything visible, no mutation controls active (or they show "Read-only" in a tooltip).
