package com.theredja.rankeditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.feed_the_beast.ftbutilities.ranks.Rank;
import com.feed_the_beast.ftbutilities.ranks.Ranks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Differential test: random rank configurations are resolved by {@link Resolver} / {@link RankState} and by the real
 * FTB Utilities {@link Rank} / {@link Ranks} classes (from the dev classpath), and the results must be identical.
 */
class FtbuDifferentialTest {

    private static final int CONFIGS = 3000;
    private static final String[] ENTRY_NODES = { "a", "a.b", "a.b.c", "x.y", "*" };
    private static final String[] QUERY_NODES = { "a", "a.b", "a.b.c", "a.b.c.d", "a.z", "x", "x.y", "x.y.z", "x.q", "q", "q.r.s",
            "*", "parent", "power" };
    private static final String[] VALUES = { "true", "false", "foo", "bar", "1", "TRUE", "<&b{name}&r>" };
    private static final String[] POWERS = { "0", "1", "2", "3", "5", "5", "10", "-4", "2147483647", "2147483646" };
    private static final String[] JOINERS = { ",", ", ", " , ", " ,", "," };

    /** {@code Ranks} without a live Universe: FTBU's {@code getRank(String)} only touches the universe for unknown ids. */
    private static final class OfflineRanks extends Ranks {
        OfflineRanks() {
            super(null);
        }

        @Override
        public Rank getRank(String id) {
            return id.isEmpty() || id.equals("none") ? null : ranks.get(id);
        }
    }

    /**
     * Stand-in for {@code PlayerRank}, whose constructor is package-private and whose op check needs a running server.
     * Its parent lookup is our own re-implementation of the documented behaviour (listed parents, plus the default OP
     * rank for ops and the default player rank, sorted by power); defaults come from the real {@code Ranks}.
     */
    private static final class FakePlayerRank extends Rank {
        private final boolean op;

        FakePlayerRank(Ranks r, UUID id, boolean op) {
            super(r, id.toString());
            this.op = op;
        }

        @Override
        public int getPower() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public Set<Rank> getActualParents() {
            Stream<Rank> listed = Arrays.stream(getLocalPermission("parent").split(","))
                    .map(id -> ranks.getRank(id.trim()))
                    .filter(r -> r != null && !r.isPlayer());
            Stream<Rank> defaults = Stream.of(op ? ranks.getDefaultOPRank() : null, ranks.getDefaultPlayerRank())
                    .filter(Objects::nonNull);
            return Stream.concat(listed, defaults).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private record Config(RankState state, Ranks ftbu, String description) {
    }

    private static <T> T pick(Random rnd, T[] a) {
        return a[rnd.nextInt(a.length)];
    }

    private static Config generate(long seed) {
        Random rnd = new Random(seed);
        int n = 1 + rnd.nextInt(8);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            names.add("r" + i);
        }
        // topological order: a rank may only name parents that come earlier in it, so no cycles
        List<String> topo = new ArrayList<>(names);
        Collections.shuffle(topo, rnd);
        // file order is independent of the topological order
        List<String> fileOrder = new ArrayList<>(names);
        Collections.shuffle(fileOrder, rnd);

        RankState state = new RankState();
        Ranks ftbu = new OfflineRanks();
        StringBuilder desc = new StringBuilder("seed=" + seed + "\n");
        for (String id : fileOrder) {
            List<String[]> kv = new ArrayList<>();
            if (rnd.nextInt(10) < 7) {
                kv.add(new String[] { "power", pick(rnd, POWERS) });
            }
            if (rnd.nextInt(14) == 0) {
                kv.add(new String[] { "default_player_rank", "true" });
            }
            if (rnd.nextInt(14) == 0) {
                kv.add(new String[] { "default_op_rank", "true" });
            }
            for (String node : ENTRY_NODES) {
                if (rnd.nextInt(10) < 3) {
                    kv.add(new String[] { node, pick(rnd, VALUES) });
                }
            }
            String parent = parentValue(rnd, topo.subList(0, topo.indexOf(id)));
            if (!parent.isEmpty()) {
                kv.add(new String[] { "parent", parent });
            }
            Collections.shuffle(kv, rnd);

            Holder h = new Holder(id, false, "");
            Rank r = new Rank(ftbu, id);
            for (String[] e : kv) {
                h.set(e[0], e[1]);
                r.setPermission(e[0], e[1]);
            }
            state.ranks.put(id, h);
            ftbu.ranks.put(id, r);
            desc.append("  ").append(h).append('\n');
        }
        return new Config(state, ftbu, desc.toString());
    }

    private static String parentValue(Random rnd, List<String> allowed) {
        List<String> tokens = new ArrayList<>();
        int count = rnd.nextInt(4);
        for (int i = 0; i < count; i++) {
            int k = rnd.nextInt(10);
            if (k == 0) {
                tokens.add("ghost");
            } else if (k == 1) {
                tokens.add("none");
            } else if (k == 2) {
                tokens.add("");
            } else if (!allowed.isEmpty()) {
                tokens.add(allowed.get(rnd.nextInt(allowed.size()))); // may repeat -> duplicates
            }
        }
        if (tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(pick(rnd, JOINERS));
            }
            sb.append(tokens.get(i));
        }
        // a value consisting only of separators/whitespace is still a legal non-empty string for FTBU
        return sb.toString();
    }

    private static String ids(java.util.Collection<Rank> ranks) {
        List<String> l = new ArrayList<>();
        ranks.forEach(r -> l.add(r.getId()));
        return String.join(",", l);
    }

    @Test
    void namedRanksResolveLikeFtbUtilities() {
        int nonEmpty = 0;
        int inherited = 0;
        int wildcard = 0;
        int viaPower = 0;
        for (long seed = 1; seed <= CONFIGS; seed++) {
            Config c = generate(seed);
            for (Holder h : c.state().ranks.values()) {
                Rank real = c.ftbu().ranks.get(h.id);
                String where = c.description() + "rank " + h.id;
                assertEquals(real.getPower(), c.state().power(h), "power; " + where);
                assertEquals(ids(real.getParents()), Fixtures.ids(c.state().parents(h)), "parents; " + where);
                for (String q : QUERY_NODES) {
                    Resolver.Resolution res = Resolver.resolve(c.state(), h, q);
                    assertFalse(res.cycle(), "no cycles generated; " + where);
                    assertEquals(real.getPermission(q, q, true), res.value(), "node " + q + "; " + where);
                    // non-recursive lookup (no wildcard fallback) too
                    assertEquals(real.getPermission(q, q, false), Resolver.resolveExact(c.state(), h, q).value(),
                            "exact node " + q + "; " + where);
                    nonEmpty += res.isSet() ? 1 : 0;
                    inherited += res.inherited(h) ? 1 : 0;
                    wildcard += res.isSet() && res.wildcard(q) ? 1 : 0;
                    viaPower += res.inherited(h) && c.state().parents(h).size() > 1 ? 1 : 0;
                }
            }
        }
        // guard against a generator that never produces interesting cases
        assertTrue(nonEmpty > 10000 && inherited > 5000 && wildcard > 2000 && viaPower > 1000,
                "weak coverage: " + nonEmpty + "/" + inherited + "/" + wildcard + "/" + viaPower);
    }

    @Test
    void defaultRanksMatchFtbUtilities() {
        for (long seed = 1; seed <= CONFIGS; seed++) {
            Config c = generate(seed);
            Rank dp = c.ftbu().getDefaultPlayerRank();
            Rank dop = c.ftbu().getDefaultOPRank();
            assertEquals(dp == null ? null : dp.getId(), idOf(c.state().defaultPlayerRank()),
                    "default player rank; " + c.description());
            assertEquals(dop == null ? null : dop.getId(), idOf(c.state().defaultOpRank()),
                    "default op rank; " + c.description());
        }
    }

    private static String idOf(Holder h) {
        return h == null ? null : h.id;
    }

    @Test
    void playersResolveLikeFtbUtilitiesPlayerRank() {
        for (long seed = 1; seed <= CONFIGS; seed++) {
            Config c = generate(seed);
            Random rnd = new Random(seed * 31 + 7);
            UUID uuid = new UUID(rnd.nextLong(), rnd.nextLong());
            boolean op = rnd.nextBoolean();
            List<String> all = new ArrayList<>(c.state().ranks.keySet());
            String parent = parentValue(rnd, all);

            FakePlayerRank real = new FakePlayerRank(c.ftbu(), uuid, op);
            Holder h = new Holder(uuid.toString(), true, "p");
            List<String[]> kv = new ArrayList<>();
            if (!parent.isEmpty()) {
                kv.add(new String[] { "parent", parent });
            }
            for (String node : ENTRY_NODES) {
                if (rnd.nextInt(10) < 2) {
                    kv.add(new String[] { node, pick(rnd, VALUES) });
                }
            }
            for (String[] e : kv) {
                h.set(e[0], e[1]);
                real.setPermission(e[0], e[1]);
            }
            c.state().players.put(h.id, h);
            c.state().playerInfo.put(uuid, new PlayerInfo(uuid, "p", false, op));

            String where = c.description() + "player op=" + op + " " + h;
            assertEquals(ids(real.getActualParents()), Fixtures.ids(c.state().actualParents(h)), "actual parents; " + where);
            for (String q : QUERY_NODES) {
                assertEquals(real.getPermission(q, q, true), Resolver.resolve(c.state(), h, q).value(), "node " + q + "; " + where);
            }
        }
    }
}
