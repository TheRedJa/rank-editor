package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.EditApplier;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.RankState;

/** Header of a rank: name, power, comment, the two default-rank toggles and the parent chips. */
@SideOnly(Side.CLIENT)
final class RankHeader extends HolderHeader {

    private final GuiRankEditor editor;
    private final Label title;
    private final Label powerCaption;
    private final Label commentCaption;
    private final InputBox power;
    private final InputBox comment;
    private final ToggleBox defaultPlayer;
    private final ToggleBox defaultOp;
    private final ChipPanel parents;

    RankHeader(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.title = new Label(this, () -> {
            Holder rank = editor.current();
            return rank == null ? "" : "Rank: " + rank.id + (editor.isRankPending(rank.id) ? "  (modified)" : "");
        }).color(() -> {
            Holder rank = editor.current();
            return rank != null && editor.isRankPending(rank.id) ? Ui.PENDING : Ui.TEXT;
        });
        this.powerCaption = new Label(this, () -> "Power").color(Ui.MUTED);
        this.commentCaption = new Label(this, () -> "Comment").color(Ui.MUTED);
        this.power = new InputBox(this, editor)
                .ghost("0")
                .source(() -> editor.current() == null ? "" : editor.current().local(RankState.NODE_POWER))
                .validator(RankActions::validPower)
                .editable(editor::canMutate)
                .commit(this::commitPower);
        this.comment = new InputBox(this, editor)
                .ghost("Rank comment")
                .source(() -> editor.current() == null ? "" : editor.current().comment)
                .validator(text -> text.length() <= EditApplier.MAX_COMMENT_LENGTH)
                .editable(editor::canMutate)
                .commit(text -> editor.tryStage(Edit.comment(false, editor.current().id, "", text)));
        comment.charLimit = EditApplier.MAX_COMMENT_LENGTH;
        this.defaultPlayer = defaultToggle("Default player rank", RankState.NODE_DEFAULT_PLAYER, "Players without a personal rank use this rank.");
        this.defaultOp = defaultToggle("Default OP rank", RankState.NODE_DEFAULT_OP, "Operators also inherit from this rank.");
        this.parents = new ChipPanel(this, editor, "Parents:", () -> RankActions.parentChips(editor, editor.current()),
                () -> RankActions.openParentMenu(editor, editor.current()));
    }

    private ToggleBox defaultToggle(String label, String flag, String explanation) {
        return new ToggleBox(this, label,
                () -> RankActions.isExplicitDefault(editor.current(), flag),
                () -> RankActions.isImplicitDefault(editor.state(), editor.current(), flag),
                () -> RankActions.toggleDefault(editor, editor.current(), flag))
                .enabled(editor::canMutate)
                .tooltip(() -> List.of(explanation,
                        "Without any rank setting " + flag + ", FTB Utilities falls back to the "
                                + (flag.equals(RankState.NODE_DEFAULT_PLAYER) ? "lowest" : "highest") + " power rank (shown as implicit).",
                        "Only one rank can hold it; ticking this clears it on the previous holder."));
    }

    private String commitPower(String text) {
        String normalized = Integer.toString(Integer.parseInt(text.trim()));
        Holder rank = editor.current();
        if (normalized.equals(rank.local(RankState.NODE_POWER))) {
            return null;
        }
        return editor.tryStage(Edit.set(false, rank.id, RankState.NODE_POWER, normalized));
    }

    @Override
    public void addWidgets() {
        power.syncFromSource();
        comment.syncFromSource();
        add(title);
        add(powerCaption);
        add(power);
        add(commentCaption);
        add(comment);
        add(defaultPlayer);
        add(defaultOp);
        add(parents);
    }

    @Override
    int layout(int w) {
        Ui.place(title, 0, 0, w, 12);
        int y = 16;
        Ui.place(powerCaption, 0, y + 4, 34, 10);
        Ui.place(power, 36, y, 64, 16);
        Ui.place(commentCaption, 108, y + 4, 48, 10);
        Ui.place(comment, 158, y, Math.max(40, w - 158), 16);
        y += 21;
        int toggleWidth = Math.max(100, Math.min(170, (w - 6) / 2));
        Ui.place(defaultPlayer, 0, y, toggleWidth, 12);
        Ui.place(defaultOp, toggleWidth + 6, y, toggleWidth, 12);
        y += 17;
        int chipsHeight = parents.layout(w);
        parents.setPosAndSize(0, y, w, chipsHeight);
        return y + chipsHeight + 2;
    }
}
