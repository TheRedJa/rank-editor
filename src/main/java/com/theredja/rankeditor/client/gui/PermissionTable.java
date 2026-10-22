package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.ContextMenuItem;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.theredja.rankeditor.client.ClientEditorSession;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.EditApplier;
import com.theredja.rankeditor.model.EditException;
import com.theredja.rankeditor.model.Entry;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.NodeInfo;
import com.theredja.rankeditor.model.PlayerInfo;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Snapshot;

/**
 * The permission table of the selected rank or player: search box, filter cycle button and the {@link NodeList}, plus
 * the actions on a row (cycle, value editor, comment, context menu). Filtering only chooses which rows the list draws.
 */
@SideOnly(Side.CLIENT)
final class PermissionTable extends Panel {

    /** Which rows the table lists. */
    private enum Mode {
        ALL("All", "Every known permission node"),
        SET_HERE("Set here", "Nodes with a value on this holder itself"),
        IN_CHAIN("Set in chain", "Nodes whose value comes from a rank (own, inherited or by wildcard), not from the default"),
        PENDING("Pending", "Nodes changed by your unapplied edits");

        final String label;
        final String description;

        Mode(String label, String description) {
            this.label = label;
            this.description = description;
        }

        Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final String[] NO_TERMS = new String[0];
    private static final int TOP = 32;

    private final GuiRankEditor editor;
    private final NodeIndex index = new NodeIndex();
    private final InputBox search;
    private final TextActionButton filterButton;
    private final NodeList list;
    private Mode mode = Mode.ALL;
    private Holder holder;
    private String lastHolderKey = "";

    PermissionTable(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.search = new InputBox(this, editor).ghost("Search nodes and descriptions...").onChange(() -> rebuild(true));
        this.filterButton = new TextActionButton(this, "", () -> {
            mode = mode.next();
            rebuild(true);
        }).label(() -> "Filter: " + mode.label).tooltip(() -> {
            List<String> lines = new ArrayList<>();
            for (Mode m : Mode.values()) {
                lines.add((m == mode ? "> " : "  ") + m.label + ": " + m.description);
            }
            return lines;
        });
        this.list = new NodeList(this, editor);
    }

    void focusSearch() {
        search.focus();
    }

    @Override
    public void addWidgets() {
        RankState state = editor.state();
        Snapshot server = ClientEditorSession.server();
        holder = editor.current();
        if (state != null && server != null && holder != null) {
            index.update(server.nodes(), state, server.state().holder(holder.player, holder.id));
            list.setContext(holder, state);
            String key = (holder.player ? "p:" : "r:") + holder.id;
            boolean switched = !key.equals(lastHolderKey);
            lastHolderKey = key;
            rebuild(switched);
        } else {
            list.setItems(List.of());
        }
        add(search);
        add(filterButton);
        add(list);
    }

    @Override
    public void alignWidgets() {
        int buttonWidth = 100;
        Ui.place(search, 0, 0, Math.max(40, width - buttonWidth - 4), 16);
        Ui.place(filterButton, width - buttonWidth, 0, buttonWidth, 16);
        Ui.place(list, 0, TOP, width, height - TOP);
    }

    private void rebuild(boolean toTop) {
        if (holder == null) {
            return;
        }
        String text = search.getText().trim();
        String[] terms = text.isEmpty() ? NO_TERMS : text.toLowerCase(Locale.ROOT).split("\\s+");
        Set<String> chain = mode == Mode.IN_CHAIN ? NodeIndex.chainNodes(editor.state(), holder) : Set.of();
        Set<String> pending = editor.pendingNodes();
        List<PermRow> shown = new ArrayList<>();
        PermRow adder = adderFor(text);
        if (adder != null) {
            shown.add(adder);
        }
        for (PermRow row : index.all()) {
            if (!row.matches(terms)) {
                continue;
            }
            boolean keep = switch (mode) {
                case ALL -> true;
                case SET_HERE -> holder.entries.containsKey(row.node);
                case IN_CHAIN -> NodeIndex.inChain(chain, row.node);
                case PENDING -> pending.contains(row.node);
            };
            if (keep) {
                shown.add(row);
            }
        }
        list.setItems(shown);
        if (toTop) {
            list.scrollToTop();
        }
    }

    /** The "+ Add" row for a typed, valid node that the table doesn't list yet. */
    private PermRow adderFor(String text) {
        if (text.isEmpty() || NodeIndex.isSpecial(text) || index.contains(text)) {
            return null;
        }
        try {
            EditApplier.checkNode(text);
        } catch (EditException e) {
            return null;
        }
        return PermRow.adder(text);
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        int ty = y + 20;
        Ui.text(theme, "Permission", x + 5, ty, Ui.MUTED);
        Ui.text(theme, "Own value", x + list.ownX() + 3, ty, Ui.MUTED);
        int effLabelX = x + list.effX() + 3;
        Ui.text(theme, "Effective", effLabelX, ty, Ui.MUTED);
        String count = list.items().size() + " / " + index.all().size();
        int right = x + w - RowList.BAR_WIDTH - 3;
        if (right - theme.getStringWidth(count) > effLabelX + theme.getStringWidth("Effective") + 6) {
            Ui.textRight(theme, count, right, ty, Ui.DIM);
        }
    }

    // --- row actions ---

    /** Left click on the own-value cell: booleans cycle unset, true, false; everything else opens the editor. */
    void activate(PermRow row) {
        String own = holder.local(row.node);
        if (isBooleanLike(row, own)) {
            String next = own.isEmpty() ? "true" : own.equals("true") ? "false" : "";
            editor.stage(Edit.set(holder.player, holder.id, row.node, next));
        } else {
            editValue(row);
        }
    }

    private static boolean isBooleanLike(PermRow row, String own) {
        return row.info != null ? row.info.isBoolean() : own.equals("true") || own.equals("false");
    }

    void setValue(PermRow row, String value) {
        editor.stage(Edit.set(holder.player, holder.id, row.node, value));
    }

    void editValue(PermRow row) {
        String denied = editor.mutationDenied();
        if (denied != null) {
            editor.showError(denied);
            return;
        }
        Holder target = holder;
        NodeInfo info = row.info;
        String current = target.local(row.node);
        String initial = current;
        if (initial.isEmpty() && info != null && !info.isBoolean()) {
            PlayerInfo player = target.player ? editor.state().playerInfo.get(UUID.fromString(target.id)) : null;
            initial = info.defaultFor(player != null && player.op());
        }
        ValueEditor dialog = new ValueEditor(row.node, initial, validatorFor(info), hintsFor(row), text -> {
            if (!text.equals(current)) {
                editor.stage(Edit.set(target.player, target.id, row.node, text));
            }
        }, editor::closeOverlay);
        editor.openOverlay(dialog);
    }

    void editComment(PermRow row) {
        String denied = editor.mutationDenied();
        Entry entry = holder.entries.get(row.node);
        if (denied != null || entry == null) {
            editor.showError(denied != null ? denied : "Set a value first, then comment it");
            return;
        }
        Holder target = holder;
        new PromptDialog(editor, "Comment for " + row.node)
                .field("Comment", entry.comment(),
                        text -> text.length() <= EditApplier.MAX_COMMENT_LENGTH ? null : "Comment is longer than " + EditApplier.MAX_COMMENT_LENGTH)
                .onAccept(values -> editor.tryStage(Edit.comment(target.player, target.id, row.node, values.get(0))))
                .open();
    }

    void openMenu(PermRow row) {
        Theme theme = editor.getTheme();
        boolean mutable = editor.canMutate();
        boolean set = !holder.local(row.node).isEmpty();
        List<ContextMenuItem> items = new ArrayList<>();
        items.add(new ContextMenuItem(Ui.ellipsize(theme, row.node, 180), Icon.EMPTY, () -> {
        }).setEnabled(false));
        items.add(ContextMenuItem.SEPARATOR);
        if (row.info == null || row.info.isBoolean()) {
            items.add(new ContextMenuItem("Set true", Icon.EMPTY, () -> setValue(row, "true")).setEnabled(mutable));
            items.add(new ContextMenuItem("Set false", Icon.EMPTY, () -> setValue(row, "false")).setEnabled(mutable));
        }
        items.add(new ContextMenuItem("Set value...", Icon.EMPTY, () -> editValue(row)).setEnabled(mutable));
        items.add(new ContextMenuItem("Clear", Icon.EMPTY, () -> setValue(row, "")).setEnabled(mutable && set));
        if (set) {
            items.add(new ContextMenuItem("Edit comment...", Icon.EMPTY, () -> editComment(row)).setEnabled(mutable));
        }
        items.add(ContextMenuItem.SEPARATOR);
        items.add(new ContextMenuItem("Copy node", Icon.EMPTY, () -> Widget.setClipboardString(row.node)));
        editor.openMenu(items);
    }

    // --- value editor helpers ---

    private static Predicate<String> validatorFor(NodeInfo info) {
        return text -> {
            if (!text.equals(text.trim())) {
                return false;
            }
            if (info == null) {
                return true;
            }
            if (info.isBoolean()) {
                return text.equals("true") || text.equals("false");
            }
            if (!info.variants().isEmpty()) {
                return info.variants().contains(text);
            }
            try {
                switch (info.type()) {
                    case "int" -> {
                        int value = Integer.parseInt(text);
                        return value >= info.min() && value <= info.max();
                    }
                    case "long" -> Long.parseLong(text);
                    case "double" -> {
                        if (!Double.isFinite(Double.parseDouble(text))) {
                            return false;
                        }
                    }
                    default -> {
                    }
                }
            } catch (NumberFormatException e) {
                return false;
            }
            return true;
        };
    }

    private static List<String> hintsFor(PermRow row) {
        NodeInfo info = row.info;
        List<String> hints = new ArrayList<>();
        hints.add(row.node);
        if (info == null) {
            hints.add("Unknown node: any text is accepted");
        } else if (info.isBoolean()) {
            hints.add("true or false");
        } else if (!info.variants().isEmpty()) {
            hints.add("One of: " + String.join(", ", info.variants()));
        } else {
            boolean lo = info.min() != Integer.MIN_VALUE;
            boolean hi = info.max() != Integer.MAX_VALUE;
            hints.add("Type " + info.type() + (lo || hi ? " (" + (lo ? info.min() : "...") + " to " + (hi ? info.max() : "...") + ")" : ""));
        }
        if (info != null && !info.description().isEmpty()) {
            hints.add(info.description());
        }
        hints.add("Leave empty to remove the entry");
        return hints;
    }
}
