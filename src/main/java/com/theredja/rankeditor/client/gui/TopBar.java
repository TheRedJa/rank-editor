package com.theredja.rankeditor.client.gui;

import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.theredja.rankeditor.client.ClientEditorSession;
import com.theredja.rankeditor.model.RankState;

/** Tabs on the left; pending-change count, Undo, Revert, Apply and Refresh on the right. */
@SideOnly(Side.CLIENT)
final class TopBar extends Panel {

    private static final String READ_ONLY = "Read-only: you don't have rankeditor.edit";

    private final GuiRankEditor editor;
    private final TabButton ranksTab;
    private final TabButton playersTab;
    private final Label readOnly;
    private final Label changes;
    private final TextActionButton undo;
    private final TextActionButton revert;
    private final TextActionButton apply;
    private final TextActionButton refresh;

    TopBar(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.ranksTab = new TabButton(this, () -> "Ranks (" + count(true) + ")", () -> editor.tab() == GuiRankEditor.Tab.RANKS,
                () -> editor.setTab(GuiRankEditor.Tab.RANKS));
        this.playersTab = new TabButton(this, () -> "Players (" + count(false) + ")", () -> editor.tab() == GuiRankEditor.Tab.PLAYERS,
                () -> editor.setTab(GuiRankEditor.Tab.PLAYERS));
        this.readOnly = new Label(this, () -> editor.canEdit() ? "" : "READ-ONLY").color(Ui.FALSE).alignRight()
                .tooltip(() -> List.of(READ_ONLY));
        this.changes = new Label(this, () -> {
            int n = editor.pendingCount();
            return n == 0 ? "No changes" : n + (n == 1 ? " change" : " changes");
        }).color(() -> editor.pendingCount() == 0 ? Ui.DIM : Ui.PENDING).alignRight();
        this.undo = new TextActionButton(this, "Undo", ClientEditorSession::undo)
                .enabled(() -> editor.canMutate() && ClientEditorSession.hasChanges())
                .tooltip(() -> List.of(editor.canEdit() ? "Undo the last staged change (Ctrl+Z)" : READ_ONLY));
        this.revert = new TextActionButton(this, "Revert", ClientEditorSession::revert)
                .enabled(() -> editor.canMutate() && ClientEditorSession.hasChanges())
                .tooltip(() -> List.of(editor.canEdit() ? "Discard all unapplied changes" : READ_ONLY));
        this.apply = new TextActionButton(this, "Apply", ClientEditorSession::applyToServer)
                .label(() -> ClientEditorSession.isApplying() ? "Applying..." : "Apply")
                .enabled(() -> editor.canMutate() && ClientEditorSession.hasChanges())
                .tooltip(() -> List.of(editor.canEdit() ? "Save the changes to the server (Ctrl+S)" : READ_ONLY));
        this.refresh = new TextActionButton(this, "Refresh", ClientEditorSession::requestRefresh)
                .enabled(() -> !ClientEditorSession.isApplying())
                .tooltip("Reload ranks and players from the server");
    }

    private int count(boolean ranks) {
        RankState state = editor.state();
        return state == null ? 0 : ranks ? state.ranks.size() : state.players.size();
    }

    @Override
    public void addWidgets() {
        add(ranksTab);
        add(playersTab);
        add(readOnly);
        add(changes);
        add(undo);
        add(revert);
        add(apply);
        add(refresh);
    }

    @Override
    public void alignWidgets() {
        Theme theme = getGui().getTheme();
        ranksTab.setPosAndSize(0, 0, 76, height);
        playersTab.setPosAndSize(78, 0, 84, height);
        int x = width;
        for (TextActionButton button : List.of(refresh, apply, revert, undo)) {
            int w = Math.max(40, theme.getStringWidth(button == apply ? "Applying..." : button.getTitle()) + 14);
            x -= w;
            button.setPosAndSize(x, 2, w, height - 4);
            x -= 3;
        }
        x -= 4;
        int changesWidth = Math.clamp(x - 166, 0, 80);
        changes.setPosAndSize(x - changesWidth, 4, changesWidth, 12);
        x -= changesWidth + 4;
        int readOnlyWidth = Math.clamp(x - 166, 0, 70);
        readOnly.setPosAndSize(x - readOnlyWidth, 4, readOnlyWidth, 12);
    }
}
