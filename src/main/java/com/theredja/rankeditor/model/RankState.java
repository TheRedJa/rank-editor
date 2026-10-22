package com.theredja.rankeditor.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The full editable rank configuration, mirroring FTB Utilities' {@code Ranks}: named ranks in file order plus
 * per-player ranks. Lookup semantics (power, parents, default ranks) replicate FTB Utilities 5.4.1.131 exactly.
 */
public final class RankState {

    public static final String NODE_PARENT = "parent";
    public static final String NODE_DEFAULT_PLAYER = "default_player_rank";
    public static final String NODE_DEFAULT_OP = "default_op_rank";
    public static final String NODE_POWER = "power";

    /** Same as {@code Ranks.RANK_NAME_PATTERN}; {@code none} is reserved. */
    public static final Pattern RANK_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");
    public static final int MAX_POWER = 2147483646;

    /** Named ranks in file order. */
    public final LinkedHashMap<String, Holder> ranks;
    /** Player ranks keyed by dashed UUID string. Holders may be empty (no personal entries). */
    public final LinkedHashMap<String, Holder> players;
    /** Known players; not part of the saved data. */
    public final LinkedHashMap<UUID, PlayerInfo> playerInfo;

    public RankState() {
        this.ranks = new LinkedHashMap<>();
        this.players = new LinkedHashMap<>();
        this.playerInfo = new LinkedHashMap<>();
    }

    public RankState copy() {
        RankState s = new RankState();
        s.ranks.putAll(Holder.deepCopy(ranks));
        s.players.putAll(Holder.deepCopy(players));
        s.playerInfo.putAll(playerInfo);
        return s;
    }

    public static boolean isValidRankName(String id) {
        return !id.isEmpty() && !id.equals("none") && RANK_NAME_PATTERN.matcher(id).matches();
    }

    /** Holder by kind and id; null when missing. */
    public Holder holder(boolean player, String id) {
        return player ? players.get(id) : ranks.get(id);
    }

    /** Same as {@code Rank.getPower()}: a missing or unparsable value counts as 0, players are always highest. */
    public int power(Holder h) {
        if (h.player) {
            return Integer.MAX_VALUE;
        }
        String s = h.local(NODE_POWER);
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Math.clamp(Integer.parseInt(s.trim()), 0, MAX_POWER);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Same as {@code Rank.getParents()}: existing named ranks from the parent entry, sorted by power descending. */
    public List<Holder> parents(Holder h) {
        List<Holder> list = new ArrayList<>();
        for (String id : h.parentIds()) {
            Holder r = ranks.get(id);
            if (r != null) {
                list.add(r);
            }
        }
        sortByPower(list);
        return list;
    }

    /**
     * Same as {@code getActualParents()}: for players this adds the default OP rank (if op) and the default player
     * rank, then sorts by power descending and drops duplicates.
     */
    public List<Holder> actualParents(Holder h) {
        if (!h.player) {
            return parents(h);
        }
        List<Holder> list = new ArrayList<>();
        for (String id : h.parentIds()) {
            Holder r = ranks.get(id);
            if (r != null) {
                list.add(r);
            }
        }
        PlayerInfo info = playerInfo.get(UUID.fromString(h.id));
        if (info != null && info.op()) {
            Holder r = defaultOpRank();
            if (r != null) {
                list.add(r);
            }
        }
        Holder r = defaultPlayerRank();
        if (r != null) {
            list.add(r);
        }
        sortByPower(list);
        return List.copyOf(new LinkedHashSet<>(list));
    }

    private void sortByPower(List<Holder> list) {
        list.sort((a, b) -> Integer.compare(power(b), power(a)));
    }

    /** Rank flagged {@code default_player_rank: true}, else the lowest-power rank (last one on ties). */
    public Holder defaultPlayerRank() {
        Holder explicit = explicitDefault(NODE_DEFAULT_PLAYER);
        if (explicit != null) {
            return explicit;
        }
        Holder result = null;
        int p = Integer.MAX_VALUE;
        for (Holder r : ranks.values()) {
            if (power(r) <= p) {
                p = power(r);
                result = r;
            }
        }
        return result;
    }

    /** Rank flagged {@code default_op_rank: true}, else the highest-power rank (last one on ties). */
    public Holder defaultOpRank() {
        Holder explicit = explicitDefault(NODE_DEFAULT_OP);
        if (explicit != null) {
            return explicit;
        }
        Holder result = null;
        int p = 0;
        for (Holder r : ranks.values()) {
            if (power(r) >= p) {
                p = power(r);
                result = r;
            }
        }
        return result;
    }

    public Holder explicitDefault(String flagNode) {
        for (Holder r : ranks.values()) {
            if (r.local(flagNode).equals("true")) {
                return r;
            }
        }
        return null;
    }

    public String displayName(Holder h) {
        if (!h.player) {
            return h.id;
        }
        PlayerInfo info = playerInfo.get(UUID.fromString(h.id));
        if (info != null && !info.name().isEmpty()) {
            return info.name();
        }
        return h.comment.isEmpty() ? h.id : h.comment;
    }

    /** Ranks sorted by power descending, file order on ties. */
    public List<Holder> ranksByPower() {
        List<Holder> list = new ArrayList<>(ranks.values());
        sortByPower(list);
        return list;
    }

    /**
     * Number of user-visible differences between {@code a} and {@code b}: each rank or player section that exists on
     * only one side counts once, and within sections present on both sides each added, removed or changed entry and a
     * changed section comment count once. Returns at least 1 when the saved data differs in any way (e.g. rank order).
     */
    public static int diffCount(RankState a, RankState b) {
        int n = diffHolders(a.ranks, b.ranks) + diffHolders(a.players, b.players);
        return n == 0 && a.fingerprint() != b.fingerprint() ? 1 : n;
    }

    private static int diffHolders(Map<String, Holder> a, Map<String, Holder> b) {
        int n = 0;
        for (Holder ha : a.values()) {
            Holder hb = b.get(ha.id);
            if (hb == null) {
                n += ha.isEmpty() && ha.player ? 0 : 1;
                continue;
            }
            if (!ha.comment.equals(hb.comment)) {
                n++;
            }
            for (Entry e : ha.entries.values()) {
                if (!e.equals(hb.entries.get(e.node()))) {
                    n++;
                }
            }
            for (String node : hb.entries.keySet()) {
                if (!ha.entries.containsKey(node)) {
                    n++;
                }
            }
        }
        for (Holder hb : b.values()) {
            if (!a.containsKey(hb.id) && !(hb.isEmpty() && hb.player)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Fingerprint of everything that gets saved (rank order, entries, comments, non-empty player ranks). Client and
     * server compute it the same way, so an edit batch can be rejected if the data changed underneath it.
     */
    public long fingerprint() {
        StringBuilder sb = new StringBuilder();
        appendHolders(sb, ranks);
        Map<String, Holder> sortedPlayers = new TreeMap<>();
        players.forEach((k, v) -> {
            if (!v.isEmpty()) {
                sortedPlayers.put(k, v);
            }
        });
        sb.append('\u0001');
        appendHolders(sb, sortedPlayers);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (d[i] & 0xFF);
            }
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void appendHolders(StringBuilder sb, Map<String, Holder> map) {
        for (Holder h : map.values()) {
            sb.append('[').append(h.id).append('\u0002').append(h.comment).append('\n');
            for (Entry e : h.entries.values()) {
                sb.append(e.node()).append('\u0002').append(e.value()).append('\u0002').append(e.comment()).append('\n');
            }
        }
    }
}
