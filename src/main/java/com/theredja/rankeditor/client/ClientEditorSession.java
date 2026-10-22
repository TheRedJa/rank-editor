package com.theredja.rankeditor.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.theredja.rankeditor.client.gui.GuiRankEditor;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.EditApplier;
import com.theredja.rankeditor.model.EditException;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Snapshot;
import com.theredja.rankeditor.net.MessageApplyEdits;
import com.theredja.rankeditor.net.MessageCloseEditor;
import com.theredja.rankeditor.net.MessageRequestSnapshot;
import com.theredja.rankeditor.net.WireCodec;

/**
 * Client-side editor state: the last server snapshot, the locally staged edits and the preview that results from
 * applying them. The GUI reads {@link #preview()} and stages changes through {@link #stage(Edit...)}; nothing
 * reaches the server until {@link #applyToServer()}. Client thread only.
 */
@SideOnly(Side.CLIENT)
public final class ClientEditorSession {

    /** Vanilla's limit for client-to-server custom payloads is 32767 bytes; leave room for headers. */
    private static final int MAX_APPLY_BYTES = 30000;

    private static Snapshot server;
    private static final List<Edit> STAGED = new ArrayList<>();
    private static RankState preview;
    private static String status = "";
    private static boolean statusError;
    private static long statusSince;
    private static boolean applying;
    private static GuiRankEditor gui;

    private ClientEditorSession() {
    }

    // --- read access for the GUI ---

    public static Snapshot server() {
        return server;
    }

    /** Server state with staged edits applied. */
    public static RankState preview() {
        return preview;
    }

    public static List<Edit> staged() {
        return List.copyOf(STAGED);
    }

    public static boolean hasChanges() {
        return !STAGED.isEmpty();
    }

    /** Net number of differences from the server data (toggling something back and forth counts as nothing). */
    public static int changeCount() {
        return server == null || preview == null ? 0 : RankState.diffCount(server.state(), preview);
    }

    public static boolean canEdit() {
        return server != null && server.canEdit();
    }

    public static boolean isApplying() {
        return applying;
    }

    public static String status() {
        return status;
    }

    public static boolean statusIsError() {
        return statusError;
    }

    /** Milliseconds since the status last changed, so the GUI can let messages expire. */
    public static long statusAgeMillis() {
        return System.currentTimeMillis() - statusSince;
    }

    /** Problems in the loaded files that the user should fix (shown as warnings, they never block applying). */
    public static List<String> problems() {
        return preview == null ? List.of() : EditApplier.problems(preview);
    }

    // --- mutations from the GUI ---

    /**
     * Stages edits if the result is valid. Returns null on success, otherwise the reason (nothing is staged).
     */
    public static String stage(Edit... edits) {
        if (!canEdit()) {
            return "Read-only: you don't have rankeditor.edit";
        }
        if (applying) {
            // onResult clears the staged list on success, which would silently drop edits staged meanwhile.
            return "Wait until the current apply finishes";
        }
        List<Edit> next = new ArrayList<>(STAGED);
        next.addAll(List.of(edits));
        try {
            preview = EditApplier.apply(server.state(), next);
        } catch (EditException e) {
            return e.getMessage();
        }
        STAGED.clear();
        STAGED.addAll(next);
        dropIfNoNetChange();
        setStatus("", false);
        notifyGui();
        return null;
    }

    /** Drops the last staged edit. */
    public static void undo() {
        if (!STAGED.isEmpty()) {
            STAGED.removeLast();
            rebase();
            dropIfNoNetChange();
            notifyGui();
        }
    }

    public static void revert() {
        STAGED.clear();
        rebase();
        setStatus("Changes discarded", false);
        notifyGui();
    }

    public static void applyToServer() {
        if (STAGED.isEmpty() || applying || server == null) {
            return;
        }
        ByteBuf buf = Unpooled.buffer();
        try {
            WireCodec.writeEdits(new DataOut(buf), STAGED);
            if (buf.writerIndex() > MAX_APPLY_BYTES) {
                setStatus("Too many changes for one apply; apply some, then the rest", true);
                notifyGui();
                return;
            }
        } finally {
            buf.release();
        }
        applying = true;
        int n = changeCount();
        setStatus("Applying " + n + " change" + (n == 1 ? "" : "s") + "...", false);
        new MessageApplyEdits(server.fingerprint(), List.copyOf(STAGED)).sendToServer();
        notifyGui();
    }

    public static void requestRefresh() {
        new MessageRequestSnapshot().sendToServer();
    }

    /** Called by the GUI when it closes. Staged edits are discarded. */
    public static void onGuiClosed(GuiRankEditor closed) {
        if (gui == closed) {
            gui = null;
            new MessageCloseEditor().sendToServer();
            server = null;
            preview = null;
            STAGED.clear();
            applying = false;
            setStatus("", false);
        }
    }

    // --- network callbacks ---

    public static void onSnapshot(Snapshot snapshot, boolean open) {
        if (gui == null && !open) {
            return;
        }
        server = snapshot;
        rebase();
        if (gui == null) {
            gui = new GuiRankEditor();
            gui.openGui();
        } else {
            notifyGui();
        }
    }

    public static void onResult(boolean success, String message) {
        if (applying) {
            applying = false;
            if (success) {
                // The new snapshot follows right after; keep the preview until then to avoid flicker.
                STAGED.clear();
            }
        }
        if (gui == null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(message)
                    .setStyle(new net.minecraft.util.text.Style().setColor(success ? TextFormatting.GREEN : TextFormatting.RED)));
            return;
        }
        setStatus(message, !success);
        notifyGui();
    }

    public static void protocolMismatch(int serverProtocol) {
        onResult(false, "Rank Editor version mismatch (server protocol " + serverProtocol + ", client " + WireCodec.PROTOCOL + "); update the mod");
    }

    // --- internals ---

    private static void rebase() {
        if (server == null) {
            preview = null;
            return;
        }
        try {
            preview = EditApplier.apply(server.state(), STAGED);
        } catch (EditException e) {
            STAGED.clear();
            preview = server.state().copy();
            setStatus("Your pending changes no longer fit the server data and were discarded: " + e.getMessage(), true);
        }
    }

    /** Edits that cancel out (true -> false -> unset) leave nothing to apply; forget them so Apply/Revert disable. */
    private static void dropIfNoNetChange() {
        if (!STAGED.isEmpty() && server.state().fingerprint() == preview.fingerprint()) {
            STAGED.clear();
        }
    }

    private static void setStatus(String s, boolean error) {
        status = s;
        statusError = error;
        statusSince = System.currentTimeMillis();
    }

    private static void notifyGui() {
        if (gui != null) {
            gui.onSessionChanged();
        }
    }
}
