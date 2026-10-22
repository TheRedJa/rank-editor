package com.theredja.rankeditor.model;

import static com.theredja.rankeditor.model.Fixtures.U1;
import static com.theredja.rankeditor.model.Fixtures.U2;
import static com.theredja.rankeditor.model.Fixtures.ids;
import static com.theredja.rankeditor.model.Fixtures.player;
import static com.theredja.rankeditor.model.Fixtures.rank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankStateTest {

    // ---- default ranks

    @Test
    void noRanksMeansNoDefaults() {
        RankState s = new RankState();
        assertNull(s.defaultPlayerRank());
        assertNull(s.defaultOpRank());
    }

    @Test
    void explicitDefaultPlayerRankWinsOverPower() {
        RankState s = new RankState();
        rank(s, "low", "power", "1");
        Holder flagged = rank(s, "mid", "power", "50", "default_player_rank", "true");
        rank(s, "high", "power", "99");
        assertSame(flagged, s.defaultPlayerRank());
    }

    @Test
    void explicitDefaultOpRankWinsOverPower() {
        RankState s = new RankState();
        rank(s, "low", "power", "1");
        Holder flagged = rank(s, "mid", "power", "50", "default_op_rank", "true");
        rank(s, "high", "power", "99");
        assertSame(flagged, s.defaultOpRank());
    }

    @Test
    void flagValueOtherThanTrueIsNotExplicit() {
        RankState s = new RankState();
        Holder low = rank(s, "low", "power", "1", "default_player_rank", "false");
        rank(s, "high", "power", "9", "default_op_rank", "TRUE");
        assertNull(s.explicitDefault(RankState.NODE_DEFAULT_PLAYER));
        assertNull(s.explicitDefault(RankState.NODE_DEFAULT_OP));
        assertSame(low, s.defaultPlayerRank());
        assertEquals("high", s.defaultOpRank().id);
    }

    @Test
    void severalExplicitFlagsFirstInFileOrderWins() {
        RankState s = new RankState();
        rank(s, "x", "power", "1");
        Holder first = rank(s, "y", "default_player_rank", "true");
        rank(s, "z", "default_player_rank", "true");
        assertSame(first, s.defaultPlayerRank());
    }

    @Test
    void implicitDefaultPlayerIsLowestPower() {
        RankState s = Fixtures.ladder();
        assertEquals("low", s.defaultPlayerRank().id);
    }

    @Test
    void implicitDefaultOpIsHighestPower() {
        RankState s = Fixtures.ladder();
        assertEquals("high", s.defaultOpRank().id);
    }

    @Test
    void implicitDefaultPlayerTieTakesLastInFileOrder() {
        RankState s = new RankState();
        rank(s, "a", "power", "3");
        rank(s, "b", "power", "3");
        rank(s, "c", "power", "7");
        assertEquals("b", s.defaultPlayerRank().id);
    }

    @Test
    void implicitDefaultOpTieTakesLastInFileOrder() {
        RankState s = new RankState();
        rank(s, "a", "power", "7");
        rank(s, "b", "power", "7");
        rank(s, "c", "power", "3");
        assertEquals("b", s.defaultOpRank().id);
    }

    @Test
    void missingPowerCountsAsZeroForDefaults() {
        RankState s = new RankState();
        rank(s, "a", "power", "5");
        rank(s, "b", "x", "true");
        rank(s, "c", "power", "5");
        assertEquals("b", s.defaultPlayerRank().id);
        assertEquals("c", s.defaultOpRank().id);
    }

    @Test
    void allZeroPowerOpDefaultIsLastRank() {
        RankState s = new RankState();
        rank(s, "a", "x", "1");
        rank(s, "b", "x", "1");
        assertEquals("b", s.defaultOpRank().id);
        assertEquals("b", s.defaultPlayerRank().id);
    }

    // ---- power parsing

    @Test
    void powerParsing() {
        RankState s = new RankState();
        assertEquals(0, s.power(rank(s, "missing", "x", "1")));
        assertEquals(0, s.power(rank(s, "garbage", "power", "abc")));
        assertEquals(0, s.power(rank(s, "float", "power", "1.5")));
        assertEquals(0, s.power(rank(s, "negative", "power", "-5")));
        assertEquals(0, s.power(rank(s, "zero", "power", "0")));
        assertEquals(7, s.power(rank(s, "seven", "power", "7")));
        assertEquals(7, s.power(rank(s, "plus", "power", "+7")));
        assertEquals(7, s.power(rank(s, "padded", "power", " 7 ")));
        assertEquals(RankState.MAX_POWER, s.power(rank(s, "maxint", "power", "2147483647")));
        assertEquals(RankState.MAX_POWER, s.power(rank(s, "max", "power", "2147483646")));
        assertEquals(2147483645, s.power(rank(s, "belowmax", "power", "2147483645")));
        // NumberFormatException territory (FTBU itself would throw here; the editor must not)
        assertEquals(0, s.power(rank(s, "huge", "power", "99999999999")));
        assertEquals(0, s.power(rank(s, "hugeneg", "power", "-99999999999")));
    }

    @Test
    void playersAlwaysHaveHighestPower() {
        RankState s = new RankState();
        assertEquals(Integer.MAX_VALUE, s.power(player(s, U1, false, "power", "3")));
    }

    // ---- parents

    @Test
    void parentsAreSortedByPowerDescendingStable() {
        RankState s = new RankState();
        rank(s, "a", "power", "1");
        rank(s, "b", "power", "9");
        rank(s, "c", "power", "1");
        Holder child = rank(s, "child", "parent", "c, a, b");
        assertEquals("b,c,a", ids(s.parents(child)));
    }

    @Test
    void parentsDropMissingDuplicatesAndBlanks() {
        RankState s = new RankState();
        rank(s, "a", "power", "1");
        Holder child = rank(s, "child", "parent", "a, ghost,, none, a ,");
        assertEquals("a", ids(s.parents(child)));
        assertEquals(java.util.List.of("a", "ghost", "none"), child.parentIds());
    }

    @Test
    void actualParentsOfNamedRankAreParents() {
        RankState s = Fixtures.ladder();
        assertEquals("mid", ids(s.actualParents(s.ranks.get("high"))));
        assertEquals("", ids(s.actualParents(s.ranks.get("low"))));
    }

    @Test
    void playerWithoutOpGetsExplicitParentsAndDefaultPlayerRank() {
        RankState s = Fixtures.ladder();
        Holder p = player(s, U1, false);
        assertEquals("low", ids(s.actualParents(p)));
        Holder q = player(s, U2, false, "parent", "mid");
        assertEquals("mid,low", ids(s.actualParents(q)));
    }

    @Test
    void opPlayerAlsoGetsDefaultOpRankSortedByPower() {
        RankState s = Fixtures.ladder();
        Holder p = player(s, U1, true, "parent", "mid");
        assertEquals("high,mid,low", ids(s.actualParents(p)));
    }

    @Test
    void actualParentsDeduplicate() {
        RankState s = Fixtures.ladder();
        // explicit parents duplicate both defaults
        Holder p = player(s, U1, true, "parent", "low, high, low");
        assertEquals("high,low", ids(s.actualParents(p)));
    }

    @Test
    void unknownPlayerCountsAsNonOp() {
        RankState s = Fixtures.ladder();
        Holder p = new Holder(U1.toString(), true, "");
        s.players.put(p.id, p);
        assertEquals("low", ids(s.actualParents(p)));
    }

    @Test
    void opWithExplicitDefaultRanks() {
        RankState s = new RankState();
        rank(s, "guest", "power", "50", "default_player_rank", "true");
        rank(s, "boss", "power", "1", "default_op_rank", "true");
        rank(s, "other", "power", "99");
        Holder p = player(s, U1, true);
        assertEquals("guest,boss", ids(s.actualParents(p)));
    }

    @Test
    void ranksByPowerKeepsFileOrderOnTies() {
        RankState s = new RankState();
        rank(s, "a", "power", "1");
        rank(s, "b", "power", "5");
        rank(s, "c", "power", "1");
        rank(s, "d", "power", "5");
        assertEquals("b,d,a,c", ids(s.ranksByPower()));
    }

    // ---- names / display

    @Test
    void rankNameValidity() {
        assertTrue(RankState.isValidRankName("abc_123"));
        assertTrue(RankState.isValidRankName("0"));
        assertFalse(RankState.isValidRankName(""));
        assertFalse(RankState.isValidRankName("none"));
        assertTrue(RankState.isValidRankName("none1"));
        assertFalse(RankState.isValidRankName("Admin"));
        assertFalse(RankState.isValidRankName("a-b"));
        assertFalse(RankState.isValidRankName("a b"));
        assertFalse(RankState.isValidRankName("a\n"));
        assertFalse(RankState.isValidRankName("ä"));
    }

    @Test
    void displayNamePrefersKnownPlayerName() {
        RankState s = new RankState();
        Holder p = new Holder(U1.toString(), true, "FromComment");
        s.players.put(p.id, p);
        assertEquals("FromComment", s.displayName(p));
        s.playerInfo.put(U1, new PlayerInfo(U1, "Live", true, false));
        assertEquals("Live", s.displayName(p));
        s.playerInfo.put(U1, new PlayerInfo(U1, "", true, false));
        assertEquals("FromComment", s.displayName(p));
        p.comment = "";
        assertEquals(U1.toString(), s.displayName(p));
        assertEquals("some", s.displayName(rank(s, "some", "power", "1")));
    }

    @Test
    void copyIsDeep() {
        RankState s = Fixtures.ladder();
        RankState c = s.copy();
        c.ranks.get("low").set("power", "99");
        c.ranks.remove("mid");
        assertEquals("1", s.ranks.get("low").local("power"));
        assertTrue(s.ranks.containsKey("mid"));
    }

    // ---- fingerprint

    @Test
    void fingerprintStableForEqualStates() {
        assertEquals(Fixtures.ladder().fingerprint(), Fixtures.ladder().fingerprint());
        RankState s = Fixtures.ladder();
        assertEquals(s.fingerprint(), s.copy().fingerprint());
    }

    @Test
    void fingerprintChangesOnAnyChange() {
        long base = Fixtures.ladder().fingerprint();
        java.util.List<java.util.function.Consumer<RankState>> mutations = java.util.List.of(
                s -> s.ranks.get("low").set("power", "2"),
                s -> s.ranks.get("low").set("new.node", "true"),
                s -> s.ranks.get("low").set("a.b", ""),
                s -> s.ranks.get("low").entries.put("a.b", s.ranks.get("low").entries.get("a.b").withComment("c")),
                s -> s.ranks.get("low").comment = "section comment",
                s -> {
                    // reorder ranks
                    Holder first = s.ranks.remove("low");
                    s.ranks.put("low", first);
                },
                s -> {
                    // reorder entries inside a rank
                    Holder h = s.ranks.get("low");
                    Entry power = h.entries.remove("power");
                    h.entries.put("power", power);
                },
                s -> s.ranks.remove("high"),
                s -> rank(s, "extra", "power", "1"),
                s -> player(s, U1, false, "x", "true"));
        for (int i = 0; i < mutations.size(); i++) {
            RankState s = Fixtures.ladder();
            mutations.get(i).accept(s);
            assertTrue(s.fingerprint() != base, "mutation " + i + " did not change the fingerprint");
        }
    }

    @Test
    void fingerprintIgnoresEmptyPlayersAndPlayerInfoAndPlayerOrder() {
        RankState a = Fixtures.ladder();
        long base = a.fingerprint();
        player(a, U1, true); // empty holder + info
        assertEquals(base, a.fingerprint());

        RankState b = Fixtures.ladder();
        RankState c = Fixtures.ladder();
        player(b, U1, false, "x", "1");
        player(b, U2, false, "y", "2");
        player(c, U2, false, "y", "2");
        player(c, U1, false, "x", "1");
        assertEquals(b.fingerprint(), c.fingerprint(), "players are sorted by id");
        assertTrue(b.fingerprint() != base);
    }

    @Test
    void fingerprintDistinguishesFieldBoundaries() {
        RankState a = new RankState();
        rank(a, "r", "n", "ab");
        RankState b = new RankState();
        rank(b, "r", "na", "b");
        assertTrue(a.fingerprint() != b.fingerprint());
    }
}
