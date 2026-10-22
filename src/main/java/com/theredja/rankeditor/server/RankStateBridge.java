package com.theredja.rankeditor.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.util.ServerUtils;
import com.feed_the_beast.ftbutilities.ranks.PlayerRank;
import com.feed_the_beast.ftbutilities.ranks.Rank;
import com.feed_the_beast.ftbutilities.ranks.Ranks;
import com.theredja.rankeditor.model.Entry;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.PlayerInfo;
import com.theredja.rankeditor.model.RankState;

/** Converts between FTB Utilities' live {@link Ranks} and the editor model. Server thread only. */
public final class RankStateBridge {

    private RankStateBridge() {
    }

    public static RankState read(Ranks ranks) {
        RankState s = new RankState();
        for (Rank rank : ranks.ranks.values()) {
            s.ranks.put(rank.getId(), toHolder(rank, rank.getId(), false));
        }
        for (PlayerRank rank : ranks.playerRanks.values()) {
            s.players.put(rank.uuid.toString(), toHolder(rank, rank.uuid.toString(), true));
            s.playerInfo.put(rank.uuid, new PlayerInfo(rank.uuid, rank.profile.getName() == null ? rank.comment : rank.profile.getName(),
                    false, ServerUtils.isOP(ranks.universe.server, rank.profile)));
        }
        for (ForgePlayer p : ranks.universe.getPlayers()) {
            if (p.isFake()) {
                continue;
            }
            UUID id = p.getId();
            s.players.computeIfAbsent(id.toString(), k -> new Holder(k, true, p.getName()));
            s.playerInfo.put(id, new PlayerInfo(id, p.getName(), p.isOnline(), p.isOP()));
        }
        return s;
    }

    private static Holder toHolder(Rank rank, String id, boolean player) {
        Holder h = new Holder(id, player, rank.comment);
        for (Rank.Entry e : rank.permissions.values()) {
            h.entries.put(e.node, new Entry(e.node, e.value, e.comment));
        }
        return h;
    }

    /**
     * Replaces the live rank data with {@code s}. Existing {@link Rank} objects are reused by id so other references
     * stay valid; the caller must call {@link Ranks#save()} afterwards (which also clears all caches).
     */
    public static void write(RankState s, Ranks ranks) {
        Map<String, Rank> existing = new HashMap<>(ranks.ranks);
        ranks.ranks.clear();
        for (Holder h : s.ranks.values()) {
            Rank rank = existing.get(h.id);
            if (rank == null) {
                rank = new Rank(ranks, h.id);
            }
            fill(rank, h);
            rank.comment = h.comment;
            ranks.ranks.put(h.id, rank);
        }
        for (Holder h : s.players.values()) {
            UUID id = UUID.fromString(h.id);
            PlayerRank rank = ranks.playerRanks.get(id);
            if (rank == null) {
                if (h.isEmpty()) {
                    continue;
                }
                PlayerInfo info = s.playerInfo.get(id);
                rank = ranks.getPlayerRank(new GameProfile(id, info == null || info.name().isEmpty() ? null : info.name()));
            }
            fill(rank, h);
        }
        ranks.clearCache();
    }

    private static void fill(Rank rank, Holder h) {
        rank.permissions.clear();
        for (Entry e : h.entries.values()) {
            Rank.Entry re = new Rank.Entry(e.node());
            re.value = e.value();
            re.comment = e.comment();
            rank.permissions.put(e.node(), re);
        }
    }
}
