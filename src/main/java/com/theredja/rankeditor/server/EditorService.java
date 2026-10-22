package com.theredja.rankeditor.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.server.permission.PermissionAPI;

import com.feed_the_beast.ftbutilities.ranks.Ranks;
import com.theredja.rankeditor.RankEditor;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.EditApplier;
import com.theredja.rankeditor.model.EditException;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Snapshot;
import com.theredja.rankeditor.net.MessageEditResult;
import com.theredja.rankeditor.net.MessageSnapshot;
import com.theredja.rankeditor.net.WireCodec;

/**
 * Server-side entry point for all editor requests. Every method runs on the server thread (FTBLib schedules
 * {@code MessageToServer.onMessage} there) and re-checks permissions; the client is never trusted.
 */
public final class EditorService {

    private static final long APPLY_COOLDOWN_MS = 250;

    private static final Set<UUID> OPEN = new HashSet<>();
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();

    private EditorService() {
    }

    public static boolean canView(EntityPlayerMP p) {
        return PermissionAPI.hasPermission(p, RankEditorPermissions.VIEW) || canEdit(p);
    }

    public static boolean canEdit(EntityPlayerMP p) {
        return PermissionAPI.hasPermission(p, RankEditorPermissions.EDIT);
    }

    public static void open(EntityPlayerMP player, boolean openGui) {
        if (!canView(player)) {
            fail(player, "You don't have permission to use the Rank Editor (" + RankEditorPermissions.VIEW + ")");
            return;
        }
        if (Ranks.INSTANCE == null) {
            fail(player, "FTB Utilities ranks aren't loaded");
            return;
        }
        OPEN.add(player.getUniqueID());
        new MessageSnapshot(snapshotFor(player, RankStateBridge.read(Ranks.INSTANCE)), openGui).sendTo(player);
    }

    public static void close(EntityPlayerMP player) {
        OPEN.remove(player.getUniqueID());
    }

    public static void onLogout(UUID id) {
        OPEN.remove(id);
        LAST_APPLY.remove(id);
    }

    public static void rejectProtocol(EntityPlayerMP player, int protocol) {
        fail(player, "Rank Editor version mismatch (client protocol " + protocol + ", server " + WireCodec.PROTOCOL + ")");
    }

    public static void apply(EntityPlayerMP player, long baseFingerprint, List<Edit> edits) {
        if (!canEdit(player)) {
            fail(player, "You don't have permission to change ranks (" + RankEditorPermissions.EDIT + ")");
            return;
        }
        Ranks ranks = Ranks.INSTANCE;
        if (ranks == null) {
            fail(player, "FTB Utilities ranks aren't loaded");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_APPLY.get(player.getUniqueID());
        if (last != null && now - last < APPLY_COOLDOWN_MS) {
            fail(player, "Too fast, try again");
            return;
        }
        LAST_APPLY.put(player.getUniqueID(), now);

        RankState current = RankStateBridge.read(ranks);
        if (current.fingerprint() != baseFingerprint) {
            fail(player, "Ranks were changed elsewhere (another admin, /ranks or a reload). Your changes were rebased onto the new data; review and apply again.");
            new MessageSnapshot(snapshotFor(player, current), false).sendTo(player);
            return;
        }

        RankState next;
        try {
            next = EditApplier.apply(current, edits);
        } catch (EditException e) {
            fail(player, e.getMessage());
            return;
        }

        int changes = RankState.diffCount(current, next);
        RankStateBridge.write(next, ranks);
        ranks.save();

        RankEditor.LOGGER.info("{} ({}) applied {} rank change(s) via {} edit(s):", player.getName(), player.getUniqueID(), changes, edits.size());
        for (Edit e : edits) {
            RankEditor.LOGGER.info("  {}", e);
        }

        new MessageEditResult(true, "Applied " + changes + " change" + (changes == 1 ? "" : "s")).sendTo(player);
        broadcast(ranks);
    }

    /** Pushes the new state to every player with the editor open (permissions may have changed, so re-check). */
    private static void broadcast(Ranks ranks) {
        RankState state = RankStateBridge.read(ranks);
        for (UUID id : Set.copyOf(OPEN)) {
            EntityPlayerMP p = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(id);
            if (p == null) {
                OPEN.remove(id);
            } else if (!canView(p)) {
                OPEN.remove(id);
                fail(p, "You no longer have permission to use the Rank Editor");
            } else {
                new MessageSnapshot(snapshotFor(p, state), false).sendTo(p);
            }
        }
    }

    private static Snapshot snapshotFor(EntityPlayerMP player, RankState state) {
        return new Snapshot(canEdit(player), Ranks.isActive(), state.fingerprint(), state, NodeCatalog.build(Ranks.INSTANCE));
    }

    private static void fail(EntityPlayerMP player, String message) {
        new MessageEditResult(false, message).sendTo(player);
    }
}
