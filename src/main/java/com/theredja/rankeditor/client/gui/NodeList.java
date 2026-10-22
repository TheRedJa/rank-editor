package com.theredja.rankeditor.client.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.NodeInfo;
import com.theredja.rankeditor.model.PlayerInfo;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Resolver;

/**
 * The permission rows: node, this holder's own value and the effective value with its source. Only visible rows are
 * drawn and only they are resolved; resolutions are cached until the editor data changes.
 */
@SideOnly(Side.CLIENT)
final class NodeList extends RowList<PermRow> {

    /** Rendered effective-value cell. */
    private record Cell(String text, Color4I color, Resolver.Resolution resolution) {
    }

    private static final Color4I DIVIDER = Color4I.WHITE.withAlpha(25);

    private final GuiRankEditor editor;
    private final PermissionTable table;
    private final Map<String, Cell> cells = new HashMap<>();
    private Holder holder;
    private RankState state;
    private boolean op;

    NodeList(PermissionTable parent, GuiRankEditor editor) {
        super(parent, 12);
        this.editor = editor;
        this.table = parent;
    }

    void setContext(Holder holder, RankState state) {
        this.holder = holder;
        this.state = state;
        cells.clear();
        PlayerInfo info = holder.player ? state.playerInfo.get(UUID.fromString(holder.id)) : null;
        this.op = info != null && info.op();
    }

    int ownX() {
        return effX() - ownWidth();
    }

    int effX() {
        return contentWidth() - effWidth();
    }

    private int ownWidth() {
        return Math.clamp(contentWidth() / 5, 54, 96);
    }

    private int effWidth() {
        int eff = Math.clamp(contentWidth() * 3 / 10, 80, 200);
        return Math.max(60, Math.min(eff, contentWidth() - ownWidth() - 100));
    }

    @Override
    protected String emptyText() {
        return "No matching permissions";
    }

    @Override
    protected void drawRow(Theme theme, PermRow row, int x, int y, int w, int h, boolean hover) {
        if (holder == null) {
            return;
        }
        int ty = y + (h - 8) / 2;
        if (row.adder) {
            Ui.text(theme, Ui.ellipsize(theme, "+ Add '" + row.node + "'", w - 8), x + 5, ty, Ui.TRUE);
            return;
        }
        int ownX = ownX();
        int effX = effX();
        if (editor.pendingNodes().contains(row.node)) {
            Ui.PENDING.draw(x, y + 1, 2, h - 2);
        }
        DIVIDER.draw(x + ownX - 1, y, 1, h);
        DIVIDER.draw(x + effX - 1, y, 1, h);
        Ui.text(theme, Ui.ellipsize(theme, row.node, ownX - 10), x + 5, ty, row.info == null ? Ui.MUTED : Ui.TEXT);
        if (hover && localMouseX() >= ownX && localMouseX() < effX) {
            Ui.HOVER.draw(x + ownX, y, effX - ownX - 1, h);
        }
        String own = holder.local(row.node);
        if (own.isEmpty()) {
            Ui.text(theme, "-", x + ownX + 3, ty, Ui.DIM);
        } else {
            Ui.text(theme, Ui.ellipsize(theme, own, effX - ownX - 7), x + ownX + 3, ty, Ui.valueColor(own));
        }
        Cell cell = cell(row);
        Ui.text(theme, Ui.ellipsize(theme, cell.text(), w - effX - 6), x + effX + 3, ty, cell.color());
    }

    private Cell cell(PermRow row) {
        return cells.computeIfAbsent(row.node, node -> buildCell(row));
    }

    private Cell buildCell(PermRow row) {
        Resolver.Resolution r = Resolver.resolve(state, holder, row.node);
        if (r.cycle()) {
            return new Cell("parent loop!", Ui.FALSE, r);
        }
        if (r.isSet()) {
            boolean inherited = r.inherited(holder);
            boolean wildcard = r.wildcard(row.node);
            String text = r.value();
            if (inherited || wildcard) {
                String source = inherited ? state.displayName(r.source()) : "here";
                text += " <- " + (wildcard ? r.matchedNode() + " (" + source + ")" : source);
            }
            return new Cell(text, Ui.valueColor(r.value()), r);
        }
        NodeInfo info = row.info;
        if (info == null) {
            return new Cell("unset", Ui.DIM, r);
        }
        if (holder.player) {
            return new Cell("default: " + shown(info.defaultFor(op)), Ui.MUTED, r);
        }
        return new Cell("default P:" + shown(info.playerDefault()) + " OP:" + shown(info.opDefault()), Ui.MUTED, r);
    }

    private static String shown(String value) {
        return value.isEmpty() ? "?" : value;
    }

    @Override
    protected void rowClicked(PermRow row, MouseButton button) {
        if (holder == null) {
            return;
        }
        if (button.isRight()) {
            table.openMenu(row);
        } else if (button.isLeft()) {
            if (row.adder) {
                table.editValue(row);
            } else if (localMouseX() >= ownX() && localMouseX() < effX()) {
                table.activate(row);
            }
        }
    }

    @Override
    protected void rowTooltip(PermRow row, List<String> out) {
        if (holder == null) {
            return;
        }
        if (row.adder) {
            out.add("Add '" + row.node + "'");
            out.add("Not on the server's list; click to set a value.");
            return;
        }
        int lx = localMouseX();
        if (lx >= effX()) {
            effectiveTooltip(row, out);
        } else if (lx >= ownX()) {
            out.add(row.node);
            out.add(holder.local(row.node).isEmpty() ? "Not set here" : "Set here: " + holder.local(row.node));
            out.add("Click to change, right-click for more");
        } else {
            nodeTooltip(row, out);
        }
    }

    private void nodeTooltip(PermRow row, List<String> out) {
        Theme theme = getGui().getTheme();
        out.add(TextFormatting.YELLOW + row.node);
        NodeInfo info = row.info;
        if (info == null) {
            out.add(TextFormatting.GRAY + "Type " + row.typeLabel() + ": not registered on the server (found in a rank file)");
            out.add(TextFormatting.GRAY + "Treated as text; true / false act as a boolean.");
            return;
        }
        String kind = switch (info.kind()) {
            case PERMISSION -> "Permission";
            case RANK_CONFIG -> "Rank config";
            case COMMAND -> "Command";
            case PREFIX -> "Prefix (covers every node below it)";
        };
        out.add(TextFormatting.GRAY + kind + ", type " + info.type() + range(info));
        if (!info.description().isEmpty()) {
            for (String line : Ui.wrap(theme, info.description(), 220)) {
                out.add(TextFormatting.WHITE + line);
            }
        }
        if (!info.variants().isEmpty()) {
            out.add(TextFormatting.GRAY + "Values: " + String.join(", ", info.variants().subList(0, Math.min(12, info.variants().size())))
                    + (info.variants().size() > 12 ? ", ..." : ""));
        }
        out.add(TextFormatting.GRAY + "Default: player " + shown(info.playerDefault()) + ", op " + shown(info.opDefault()));
    }

    private static String range(NodeInfo info) {
        boolean lo = info.min() != Integer.MIN_VALUE;
        boolean hi = info.max() != Integer.MAX_VALUE;
        if (!lo && !hi) {
            return "";
        }
        return " (" + (lo ? info.min() : "...") + " to " + (hi ? info.max() : "...") + ")";
    }

    private void effectiveTooltip(PermRow row, List<String> out) {
        Resolver.Resolution r = cell(row).resolution();
        out.add(TextFormatting.YELLOW + row.node);
        if (r.cycle()) {
            out.add("A parent loop prevents resolving this node");
        } else if (r.isSet()) {
            out.add("Effective value: " + r.value());
            out.add(r.inherited(holder) ? "Inherited from " + state.displayName(r.source()) : "Set on this " + (holder.player ? "player" : "rank"));
            if (r.wildcard(row.node)) {
                out.add("Matched through '" + r.matchedNode() + "'");
            }
        } else {
            out.add("Not set anywhere in the rank chain;");
            out.add("the server falls back to the default.");
        }
    }
}
