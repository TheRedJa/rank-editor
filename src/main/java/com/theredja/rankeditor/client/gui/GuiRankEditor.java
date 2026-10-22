package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import com.feed_the_beast.ftblib.lib.gui.ContextMenu;
import com.feed_the_beast.ftblib.lib.gui.ContextMenuItem;
import com.feed_the_beast.ftblib.lib.gui.GuiBase;
import com.feed_the_beast.ftblib.lib.gui.GuiWrapper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.gui.WidgetType;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.theredja.rankeditor.client.ClientEditorSession;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.Entry;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Snapshot;

/**
 * The Rank Editor screen: tabs, a list of ranks or players on the left, the selected holder's header and permission
 * table on the right.
 *
 * <p>Everything lives on this one screen. Dialogs, menus and the value editor are overlays (the context-menu slot of
 * {@link GuiBase}) rather than screens, so the screen is only ever replaced when the editor really closes, and that
 * is the only time {@link ClientEditorSession#onGuiClosed} is called: either through {@link #closeGui} (Escape and the
 * confirmed discard) or when Minecraft swaps the screen from outside (disconnect, death screen, another mod).
 *
 * <p>Data flows one way: the session notifies {@link #onSessionChanged}, which only schedules a refresh; the refresh
 * rebuilds the panels from the session in {@link #addWidgets}, never while an input event is being dispatched.
 */
@SideOnly(Side.CLIENT)
public class GuiRankEditor extends GuiBase {

    /** The two list modes of the left column. */
    enum Tab { RANKS, PLAYERS }

    private static final int MARGIN = 6;
    private static final int OUTER = 8;
    private static final long ERROR_MILLIS = 10000;
    private static final long INFO_MILLIS = 5000;
    private static final String READ_ONLY = "Read-only: you don't have rankeditor.edit";
    private static final String IDLE_HINT = "Ctrl+S apply  |  Ctrl+Z undo  |  Ctrl+F search  |  Esc close";

    private final List<InputBox> inputs = new ArrayList<>();
    private final TopBar topBar;
    private final Label warning;
    private final RankPanel rankPanel;
    private final PlayerPanel playerPanel;
    private final DetailPanel detail;
    private final StatusBar statusBar;

    private Tab tab = Tab.RANKS;
    private String selectedRank;
    private String selectedPlayer;
    private RankState state;
    private Holder current;
    private Set<String> pendingRanks = Set.of();
    private Set<String> pendingPlayers = Set.of();
    private Set<String> pendingNodes = Set.of();
    private List<String> problems = List.of();
    private int pendingCount;
    private String localMessage = "";
    private long localMessageUntil;
    private boolean released;

    public GuiRankEditor() {
        this.topBar = new TopBar(this, this);
        this.warning = new Label(this,
                () -> "FTB Utilities ranks are disabled (ftbutilities.cfg ranks.enabled) - changes are saved but have no effect")
                .color(Ui.WARN);
        this.rankPanel = new RankPanel(this, this);
        this.playerPanel = new PlayerPanel(this, this);
        this.detail = new DetailPanel(this, this);
        this.statusBar = new StatusBar(this, this);
    }

    // --- called by the session ---

    /** Schedules a rebuild from the session data; selection, search text and scroll positions are kept. */
    public void onSessionChanged() {
        if (!released) {
            refreshWidgets();
        }
    }

    // --- state for the child widgets ---

    RankState state() {
        return state;
    }

    /** The selected rank or player as it is in the preview, or null. */
    Holder current() {
        return current;
    }

    Tab tab() {
        return tab;
    }

    String selectedRankId() {
        return selectedRank;
    }

    String selectedPlayerId() {
        return selectedPlayer;
    }

    boolean isRankPending(String id) {
        return pendingRanks.contains(id);
    }

    boolean isPlayerPending(String id) {
        return pendingPlayers.contains(id);
    }

    /** Nodes of {@link #current()} that differ from the server (including nodes removed by staged edits). */
    Set<String> pendingNodes() {
        return pendingNodes;
    }

    int pendingCount() {
        return pendingCount;
    }

    List<String> problems() {
        return problems;
    }

    boolean ranksActive() {
        Snapshot server = ClientEditorSession.server();
        return server == null || server.ranksActive();
    }

    boolean canEdit() {
        return ClientEditorSession.canEdit();
    }

    /** True when edits can be staged right now (not read-only, not waiting for an apply to finish). */
    boolean canMutate() {
        return mutationDenied() == null;
    }

    String mutationDenied() {
        if (!ClientEditorSession.canEdit()) {
            return READ_ONLY;
        }
        return ClientEditorSession.isApplying() ? "Applying changes, please wait" : null;
    }

    /** Keyboard hints, always shown on the left of the status line. */
    String hintText() {
        return IDLE_HINT;
    }

    /** Transient message on the right of the status line; info expires after a few seconds, errors a bit later. */
    String messageText() {
        if (hasLocalMessage()) {
            return localMessage;
        }
        String status = ClientEditorSession.status();
        if (status.isEmpty()) {
            return "";
        }
        if (ClientEditorSession.isApplying()) {
            return status;
        }
        long ttl = ClientEditorSession.statusIsError() ? ERROR_MILLIS : INFO_MILLIS;
        return ClientEditorSession.statusAgeMillis() < ttl ? status : "";
    }

    Color4I messageColor() {
        if (hasLocalMessage() || ClientEditorSession.statusIsError()) {
            return Ui.FALSE;
        }
        if (ClientEditorSession.isApplying()) {
            return Ui.PENDING;
        }
        return Ui.TRUE;
    }

    private boolean hasLocalMessage() {
        return !localMessage.isEmpty() && System.currentTimeMillis() < localMessageUntil;
    }

    // --- actions for the child widgets ---

    /** Shows a message in the status line until the next successful edit (or a few seconds). */
    void showError(String message) {
        localMessage = message;
        localMessageUntil = System.currentTimeMillis() + ERROR_MILLIS;
    }

    /** Stages edits atomically; returns the error or null. Nothing is shown, the caller reports the error. */
    String tryStage(Edit... edits) {
        String denied = mutationDenied();
        return denied != null ? denied : ClientEditorSession.stage(edits);
    }

    /** Like {@link #tryStage} but reports an error in the status line. */
    String stage(Edit... edits) {
        String error = tryStage(edits);
        if (error != null) {
            showError(error);
        } else {
            localMessage = "";
        }
        return error;
    }

    void setTab(Tab next) {
        if (tab != next) {
            blurInputs();
            closeContextMenu();
            tab = next;
            refreshWidgets();
        }
    }

    void selectRank(String id) {
        blurInputs();
        tab = Tab.RANKS;
        selectedRank = id;
        refreshWidgets();
    }

    void selectPlayer(String id) {
        blurInputs();
        tab = Tab.PLAYERS;
        selectedPlayer = id;
        refreshWidgets();
    }

    /** Called by every {@link InputBox} that wants focus-loss handling. */
    void register(InputBox box) {
        inputs.add(box);
    }

    private InputBox focusedInput() {
        for (InputBox box : inputs) {
            if (box.isFocused()) {
                return box;
            }
        }
        return null;
    }

    /** Drops focus from every box, committing pending text edits to the current selection first. */
    private void blurInputs() {
        for (InputBox box : inputs) {
            box.blur();
        }
    }

    void openMenu(List<ContextMenuItem> items) {
        blurInputs();
        openContextMenu(items);
    }

    void openOverlay(Panel overlay) {
        blurInputs();
        openContextMenu(overlay);
    }

    void closeOverlay() {
        closeContextMenu();
    }

    /** Escape: asks before throwing away staged edits. */
    private void requestClose() {
        int staged = ClientEditorSession.changeCount();
        if (staged == 0) {
            closeGui(false);
            return;
        }
        new PromptDialog(this, "Unapplied changes")
                .message("Discard " + staged + " unapplied change" + (staged == 1 ? "" : "s") + " and close the editor?")
                .okLabel("Discard")
                .onAccept(values -> {
                    closeGui(false);
                    return null;
                })
                .open();
    }

    // --- model sync and layout ---

    private void sync() {
        Snapshot server = ClientEditorSession.server();
        state = ClientEditorSession.preview();
        current = null;
        pendingRanks = Set.of();
        pendingPlayers = Set.of();
        pendingNodes = Set.of();
        problems = List.of();
        pendingCount = 0;
        if (state == null || server == null) {
            return;
        }
        RankState base = server.state();
        pendingCount = ClientEditorSession.changeCount();
        problems = ClientEditorSession.problems();
        if (selectedRank == null || !state.ranks.containsKey(selectedRank)) {
            List<Holder> byPower = state.ranksByPower();
            selectedRank = byPower.isEmpty() ? null : byPower.getFirst().id;
        }
        if (selectedPlayer != null && !state.players.containsKey(selectedPlayer)) {
            selectedPlayer = null;
        }
        String id = tab == Tab.RANKS ? selectedRank : selectedPlayer;
        current = id == null ? null : (tab == Tab.RANKS ? state.ranks : state.players).get(id);
        if (pendingCount > 0) {
            pendingRanks = differing(state.ranks, base.ranks);
            pendingPlayers = differing(state.players, base.players);
            if (current != null) {
                pendingNodes = differingNodes(current, base.holder(current.player, current.id));
            }
        }
    }

    private static Set<String> differing(Map<String, Holder> preview, Map<String, Holder> base) {
        Set<String> out = new HashSet<>();
        preview.forEach((id, holder) -> {
            if (!holder.equals(base.get(id))) {
                out.add(id);
            }
        });
        return out;
    }

    private static Set<String> differingNodes(Holder preview, Holder base) {
        Map<String, Entry> now = preview.entries;
        Map<String, Entry> before = base == null ? Map.of() : base.entries;
        Set<String> out = new HashSet<>();
        now.forEach((node, entry) -> {
            if (!entry.equals(before.get(node))) {
                out.add(node);
            }
        });
        for (String node : before.keySet()) {
            if (!now.containsKey(node)) {
                out.add(node);
            }
        }
        return out;
    }

    @Override
    public Theme getTheme() {
        return DarkTheme.INSTANCE;
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        theme.drawGui(x, y, w, h, WidgetType.NORMAL);
    }

    @Override
    public boolean onInit() {
        setSize(Math.max(320, getScreen().getScaledWidth() - 2 * OUTER), Math.max(220, getScreen().getScaledHeight() - 2 * OUTER));
        return true;
    }

    /** Sizes the child panels before they refresh, so their own alignment already sees the final size. */
    private void layout() {
        int w = width - 2 * MARGIN;
        int y = MARGIN;
        topBar.setPosAndSize(MARGIN, y, w, 20);
        y += 24;
        if (!ranksActive()) {
            warning.setPosAndSize(MARGIN, y, w, 12);
            y += 14;
        }
        int statusY = height - MARGIN - 12;
        statusBar.setPosAndSize(MARGIN, statusY, w, 12);
        int bodyHeight = statusY - 4 - y;
        int leftWidth = Math.max(140, w * 30 / 100);
        rankPanel.setPosAndSize(MARGIN, y, leftWidth, bodyHeight);
        playerPanel.setPosAndSize(MARGIN, y, leftWidth, bodyHeight);
        detail.setPosAndSize(MARGIN + leftWidth + 6, y, w - leftWidth - 6, bodyHeight);
    }

    @Override
    public void addWidgets() {
        sync();
        layout();
        add(topBar);
        if (!ranksActive()) {
            add(warning);
        }
        add(tab == Tab.RANKS ? rankPanel : playerPanel);
        add(detail);
        add(statusBar);
        // The refresh cleared the widget list, which is where GuiBase keeps its open overlay.
        if (contextMenu != null) {
            add(contextMenu);
        }
    }

    @Override
    public void alignWidgets() {
    }

    // --- input ---

    @Override
    public boolean mousePressed(MouseButton button) {
        if (contextMenu != null) {
            // Overlays are modal: a click outside them is theirs to handle or swallow, never the editor's.
            contextMenu.mousePressed(button);
            return true;
        }
        for (InputBox box : inputs) {
            if (box.isFocused() && !box.isMouseOver()) {
                box.blur();
            }
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(int scroll) {
        return contextMenu != null || super.mouseScrolled(scroll);
    }

    @Override
    public boolean keyPressed(int key, char keyChar) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (contextMenu != null) {
                closeContextMenu();
            } else if (focusedInput() != null) {
                focusedInput().cancel();
            } else {
                requestClose();
            }
            return true;
        }
        if (super.keyPressed(key, keyChar)) {
            return true;
        }
        if (contextMenu == null && focusedInput() == null && isCtrlKeyDown()) {
            switch (key) {
                case Keyboard.KEY_S -> {
                    if (canMutate()) {
                        ClientEditorSession.applyToServer();
                    }
                    return true;
                }
                case Keyboard.KEY_Z -> {
                    if (canMutate()) {
                        ClientEditorSession.undo();
                    }
                    return true;
                }
                case Keyboard.KEY_F -> {
                    if (isShiftKeyDown() && tab == Tab.PLAYERS) {
                        playerPanel.focusSearch();
                    } else if (current != null) {
                        detail.focusSearch();
                    }
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** Backspace with nothing focused (and the mouse BACK button) would close the editor; ignore both. */
    @Override
    public void onBack() {
    }

    /** Only Escape closes, handled in {@link #keyPressed}; the inventory key must not. */
    @Override
    public boolean onClosedByKey(int key) {
        return false;
    }

    // --- drawing ---

    @Override
    public void drawWidget(Theme theme, Widget widget, int index, int x, int y, int w, int h) {
        if (widget == contextMenu && !(widget instanceof ContextMenu)) {
            Ui.DIM_SCREEN.draw(0, 0, getScreen().getScaledWidth(), getScreen().getScaledHeight());
        }
        super.drawWidget(theme, widget, index, x, y, w, h);
    }

    // --- lifecycle ---

    /**
     * Wraps the screen so that Minecraft replacing it (disconnect, death screen, another mod's GUI) also ends the
     * session. FTBLib's wrapper never calls {@link GuiBase#onClosed()} in that case, only on {@link #closeGui}.
     */
    @Override
    public GuiScreen getWrapper() {
        return new GuiWrapper(this) {
            @Override
            public void onGuiClosed() {
                super.onGuiClosed();
                release();
            }
        };
    }

    @Override
    public void onClosed() {
        super.onClosed();
        release();
    }

    /** Tells the session the editor is gone, exactly once. */
    private void release() {
        if (released) {
            return;
        }
        released = true;
        Keyboard.enableRepeatEvents(false);
        ClientEditorSession.onGuiClosed(this);
    }
}
