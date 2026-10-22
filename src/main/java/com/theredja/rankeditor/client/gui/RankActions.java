package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.ContextMenuItem;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.RankState;

/** Rank-level operations of the editor: dialogs for create / rename / duplicate / delete, default flags and parent lists. */
@SideOnly(Side.CLIENT)
final class RankActions {

    private static final int MENU_LIMIT = 20;

    private RankActions() {
    }

    static boolean validPower(String text) {
        try {
            int power = Integer.parseInt(text);
            return power >= 0 && power <= RankState.MAX_POWER;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String powerError(String text) {
        return validPower(text) ? null : "Power is a whole number from 0 to " + RankState.MAX_POWER;
    }

    /** @param keep id the name may equal (the rank being renamed), or null */
    private static String nameError(RankState state, String name, String keep) {
        if (name.isEmpty()) {
            return "Enter a name";
        }
        if (!RankState.isValidRankName(name)) {
            return "Use lowercase letters, digits and _ ('none' is reserved)";
        }
        if (!name.equals(keep) && state.ranks.containsKey(name)) {
            return "Rank '" + name + "' already exists";
        }
        return null;
    }

    static void newRank(GuiRankEditor editor) {
        new PromptDialog(editor, "New rank")
                .field("Name", "", name -> nameError(editor.state(), name, null))
                .field("Power", "1", RankActions::powerError)
                .onAccept(values -> create(editor, values.get(0), "", values.get(1)))
                .open();
    }

    private static void duplicate(GuiRankEditor editor, Holder rank) {
        String suggestion = rank.id + "_copy";
        for (int i = 2; editor.state().ranks.containsKey(suggestion); i++) {
            suggestion = rank.id + "_copy" + i;
        }
        new PromptDialog(editor, "Duplicate rank '" + rank.id + "'")
                .message("Copies all permissions except the default-rank flags.")
                .field("Name", suggestion, name -> nameError(editor.state(), name, null))
                .field("Power", Integer.toString(editor.state().power(rank)), RankActions::powerError)
                .onAccept(values -> create(editor, values.get(0), rank.id, values.get(1)))
                .open();
    }

    private static String create(GuiRankEditor editor, String name, String copyFrom, String power) {
        String error = editor.tryStage(Edit.createRank(name, copyFrom, Integer.parseInt(power)));
        if (error == null) {
            editor.selectRank(name);
        }
        return error;
    }

    private static void rename(GuiRankEditor editor, Holder rank) {
        String id = rank.id;
        new PromptDialog(editor, "Rename rank '" + id + "'")
                .message("Parent lists are updated everywhere.")
                .field("New name", id, name -> nameError(editor.state(), name, id))
                .onAccept(values -> {
                    String name = values.get(0);
                    if (name.equals(id)) {
                        return null;
                    }
                    String error = editor.tryStage(Edit.renameRank(id, name));
                    if (error == null) {
                        editor.selectRank(name);
                    }
                    return error;
                })
                .open();
    }

    private static void delete(GuiRankEditor editor, Holder rank) {
        String id = rank.id;
        new PromptDialog(editor, "Delete rank")
                .message("Delete rank '" + id + "'?", "It is also removed from every parent list.")
                .okLabel("Delete")
                .onAccept(values -> editor.tryStage(Edit.deleteRank(id)))
                .open();
    }

    /** Right-click menu of a rank row. */
    static void openMenu(GuiRankEditor editor, Holder rank) {
        boolean mutable = editor.canMutate();
        List<ContextMenuItem> items = new ArrayList<>();
        items.add(new ContextMenuItem("Rename...", Icon.EMPTY, () -> rename(editor, rank)).setEnabled(mutable));
        items.add(new ContextMenuItem("Duplicate...", Icon.EMPTY, () -> duplicate(editor, rank)).setEnabled(mutable));
        items.add(new ContextMenuItem("Delete...", Icon.EMPTY, () -> delete(editor, rank)).setEnabled(mutable));
        items.add(ContextMenuItem.SEPARATOR);
        items.add(new ContextMenuItem("Make default player rank", Icon.EMPTY, () -> setDefault(editor, rank, RankState.NODE_DEFAULT_PLAYER, true))
                .setEnabled(mutable));
        items.add(new ContextMenuItem("Make default OP rank", Icon.EMPTY, () -> setDefault(editor, rank, RankState.NODE_DEFAULT_OP, true))
                .setEnabled(mutable));
        editor.openMenu(items);
    }

    // --- default flags ---

    /**
     * Sets or clears a default flag. Setting it also clears the rank that currently holds it explicitly, in the same
     * {@code stage} call so the two edits are accepted or rejected together.
     */
    static void setDefault(GuiRankEditor editor, Holder rank, String flag, boolean on) {
        List<Edit> edits = new ArrayList<>();
        if (on) {
            Holder holder = editor.state().explicitDefault(flag);
            if (holder != null && !holder.id.equals(rank.id)) {
                edits.add(Edit.set(false, holder.id, flag, ""));
            }
            edits.add(Edit.set(false, rank.id, flag, "true"));
        } else {
            edits.add(Edit.set(false, rank.id, flag, ""));
        }
        editor.stage(edits.toArray(Edit[]::new));
    }

    static void toggleDefault(GuiRankEditor editor, Holder rank, String flag) {
        if (rank != null) {
            setDefault(editor, rank, flag, !rank.local(flag).equals("true"));
        }
    }

    static boolean isExplicitDefault(Holder rank, String flag) {
        return rank != null && rank.local(flag).equals("true");
    }

    /** True when the rank is the default only because no rank sets the flag (lowest / highest power fallback). */
    static boolean isImplicitDefault(RankState state, Holder rank, String flag) {
        if (rank == null || state == null || state.explicitDefault(flag) != null) {
            return false;
        }
        Holder fallback = flag.equals(RankState.NODE_DEFAULT_PLAYER) ? state.defaultPlayerRank() : state.defaultOpRank();
        return fallback != null && fallback.id.equals(rank.id);
    }

    // --- parents ---

    /** Chips for the parent ranks of a rank or player; unknown parents are red. */
    static List<ChipPanel.Item> parentChips(GuiRankEditor editor, Holder holder) {
        List<ChipPanel.Item> items = new ArrayList<>();
        RankState state = editor.state();
        if (holder == null || state == null) {
            return items;
        }
        boolean mutable = editor.canMutate();
        for (String id : holder.parentIds()) {
            Holder parent = state.ranks.get(id);
            items.add(new ChipPanel.Item(id, parent != null ? Ui.ACCENT : Ui.FALSE,
                    parent != null ? "Power " + state.power(parent) : "This rank doesn't exist",
                    mutable ? () -> setParents(editor, holder, without(holder.parentIds(), id)) : null));
        }
        return items;
    }

    private static List<String> without(List<String> ids, String id) {
        List<String> copy = new ArrayList<>(ids);
        copy.remove(id);
        return copy;
    }

    private static void setParents(GuiRankEditor editor, Holder holder, List<String> ids) {
        editor.stage(Edit.set(holder.player, holder.id, RankState.NODE_PARENT, String.join(", ", ids)));
    }

    /** Menu of the ranks that can still be added as parent (not already parents, not the rank itself). */
    static void openParentMenu(GuiRankEditor editor, Holder holder) {
        if (holder == null) {
            return;
        }
        RankState state = editor.state();
        List<String> have = holder.parentIds();
        List<ContextMenuItem> items = new ArrayList<>();
        int hidden = 0;
        for (Holder rank : state.ranksByPower()) {
            if ((!holder.player && rank.id.equals(holder.id)) || have.contains(rank.id)) {
                continue;
            }
            if (items.size() >= MENU_LIMIT) {
                hidden++;
                continue;
            }
            List<String> added = new ArrayList<>(have);
            added.add(rank.id);
            items.add(new ContextMenuItem(rank.id + "  (power " + state.power(rank) + ")", Icon.EMPTY, () -> setParents(editor, holder, added)));
        }
        if (hidden > 0) {
            items.add(new ContextMenuItem("... and " + hidden + " more", Icon.EMPTY, () -> {
            }).setEnabled(false));
        }
        if (items.isEmpty()) {
            items.add(new ContextMenuItem("No more ranks to add", Icon.EMPTY, () -> {
            }).setEnabled(false));
        }
        editor.openMenu(items);
    }
}
