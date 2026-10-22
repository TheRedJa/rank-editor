package com.theredja.rankeditor.model;

import java.util.UUID;

/** Small builders so tests read as data. */
final class Fixtures {

    static final UUID U1 = UUID.fromString("069be141-3c1b-45c3-b3b1-60d3f9fcd236");
    static final UUID U2 = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private Fixtures() {
    }

    /** Adds a named rank with the given node/value pairs, in order. */
    static Holder rank(RankState s, String id, String... kv) {
        Holder h = new Holder(id, false, "");
        fill(h, kv);
        s.ranks.put(id, h);
        return h;
    }

    /** Adds a player section (and known-player info). */
    static Holder player(RankState s, UUID uuid, boolean op, String... kv) {
        Holder h = new Holder(uuid.toString(), true, "Name" + uuid.toString().substring(0, 4));
        fill(h, kv);
        s.players.put(h.id, h);
        s.playerInfo.put(uuid, new PlayerInfo(uuid, h.comment, false, op));
        return h;
    }

    static void fill(Holder h, String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("odd kv");
        }
        for (int i = 0; i < kv.length; i += 2) {
            h.set(kv[i], kv[i + 1]);
        }
    }

    /** Ranks low (power 1), mid (power 5, parent low), high (power 10, parent mid). */
    static RankState ladder() {
        RankState s = new RankState();
        rank(s, "low", "power", "1", "a.b", "true");
        rank(s, "mid", "power", "5", "parent", "low", "x.y", "foo");
        rank(s, "high", "power", "10", "parent", "mid");
        return s;
    }

    static String ids(java.util.List<Holder> hs) {
        return hs.stream().map(h -> h.id).collect(java.util.stream.Collectors.joining(","));
    }
}
