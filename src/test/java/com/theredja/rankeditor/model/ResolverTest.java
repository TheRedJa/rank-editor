package com.theredja.rankeditor.model;

import static com.theredja.rankeditor.model.Fixtures.U1;
import static com.theredja.rankeditor.model.Fixtures.player;
import static com.theredja.rankeditor.model.Fixtures.rank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResolverTest {

    @Test
    void ownEntryWins() {
        RankState s = Fixtures.ladder();
        Holder mid = s.ranks.get("mid");
        mid.set("a.b", "false");
        Resolver.Resolution r = Resolver.resolve(s, mid, "a.b");
        assertEquals("false", r.value());
        assertSame(mid, r.source());
        assertEquals("a.b", r.matchedNode());
        assertFalse(r.inherited(mid));
        assertFalse(r.wildcard("a.b"));
        assertFalse(r.cycle());
    }

    @Test
    void inheritedFromAncestorReportsSourceHolder() {
        RankState s = Fixtures.ladder();
        Holder high = s.ranks.get("high");
        Resolver.Resolution r = Resolver.resolve(s, high, "a.b");
        assertEquals("true", r.value());
        assertSame(s.ranks.get("low"), r.source());
        assertTrue(r.inherited(high));
        assertFalse(r.inherited(s.ranks.get("low")));
        assertTrue(r.isSet());
    }

    @Test
    void unsetEverywhere() {
        RankState s = Fixtures.ladder();
        Resolver.Resolution r = Resolver.resolve(s, s.ranks.get("high"), "nothing.here");
        assertEquals("", r.value());
        assertFalse(r.isSet());
        assertNull(r.source());
        assertNull(r.matchedNode());
        assertFalse(r.cycle());
        assertFalse(r.inherited(s.ranks.get("high")));
        assertFalse(r.wildcard("nothing.here"));
    }

    @Test
    void parentNodeFallbackReportsMatchedNode() {
        RankState s = new RankState();
        Holder h = rank(s, "r", "power", "1", "a.b", "true");
        Resolver.Resolution r = Resolver.resolve(s, h, "a.b.c.d");
        assertEquals("true", r.value());
        assertEquals("a.b", r.matchedNode());
        assertTrue(r.wildcard("a.b.c.d"));
        assertSame(h, r.source());
    }

    @Test
    void starIsLastResort() {
        RankState s = new RankState();
        Holder h = rank(s, "r", "*", "false", "a", "true");
        Resolver.Resolution r = Resolver.resolve(s, h, "zzz.yyy");
        assertEquals("false", r.value());
        assertEquals("*", r.matchedNode());
        assertTrue(r.wildcard("zzz.yyy"));
        assertEquals("true", Resolver.resolve(s, h, "a.q").value());
        assertEquals("a", Resolver.resolve(s, h, "a.q").matchedNode());
        // querying '*' itself is an exact match, not a wildcard match
        assertFalse(Resolver.resolve(s, h, "*").wildcard("*"));
    }

    @Test
    void specificInheritedBeatsLocalWildcard() {
        // FTBU checks the whole parent chain for the exact node before falling back to a shorter node
        RankState s = new RankState();
        rank(s, "base", "power", "1", "a.b", "true");
        Holder child = rank(s, "child", "power", "2", "parent", "base", "*", "false", "a", "false");
        Resolver.Resolution r = Resolver.resolve(s, child, "a.b");
        assertEquals("true", r.value());
        assertEquals("base", r.source().id);
        // other nodes hit the local wildcards
        assertEquals("false", Resolver.resolve(s, child, "a.c").value());
        assertEquals("a", Resolver.resolve(s, child, "a.c").matchedNode());
    }

    @Test
    void resolveExactSkipsWildcards() {
        RankState s = new RankState();
        Holder h = rank(s, "r", "*", "true");
        assertEquals("", Resolver.resolveExact(s, h, "a").value());
        assertEquals("true", Resolver.resolveExact(s, h, "*").value());
        assertEquals("true", Resolver.resolve(s, h, "a").value());
    }

    @Test
    void higherPowerParentIsAskedFirst() {
        RankState s = new RankState();
        rank(s, "weak", "power", "1", "x", "weak");
        rank(s, "strong", "power", "9", "x", "strong");
        Holder child = rank(s, "child", "parent", "weak, strong");
        assertEquals("strong", Resolver.resolve(s, child, "x").value());
        assertEquals("strong", Resolver.resolve(s, child, "x").source().id);
    }

    @Test
    void searchIsDepthFirst() {
        RankState s = new RankState();
        rank(s, "root", "power", "0", "x", "root");
        rank(s, "strongParent", "power", "9", "parent", "root");
        rank(s, "weakParent", "power", "1", "x", "weak");
        Holder child = rank(s, "child", "parent", "weakParent, strongParent");
        // strongParent has no x itself but its ancestor does; that is found before weakParent's own value
        assertEquals("root", Resolver.resolve(s, child, "x").value());
    }

    @Test
    void diamondIsNotACycle() {
        RankState s = new RankState();
        rank(s, "top", "power", "1", "x", "top");
        rank(s, "left", "power", "2", "parent", "top");
        rank(s, "right", "power", "3", "parent", "top");
        Holder bottom = rank(s, "bottom", "parent", "left, right");
        Resolver.Resolution r = Resolver.resolve(s, bottom, "x");
        assertFalse(r.cycle());
        assertEquals("top", r.value());
        Resolver.Resolution none = Resolver.resolve(s, bottom, "y");
        assertFalse(none.cycle());
        assertEquals("", none.value());
    }

    @Test
    void cycleIsReportedInsteadOfOverflowing() {
        RankState s = new RankState();
        rank(s, "a", "power", "1", "parent", "b");
        rank(s, "b", "power", "2", "parent", "c");
        rank(s, "c", "power", "3", "parent", "a");
        Resolver.Resolution r = Resolver.resolve(s, s.ranks.get("a"), "anything.at.all");
        assertTrue(r.cycle());
        assertFalse(r.isSet());
        assertNull(r.source());
        assertTrue(Resolver.resolveExact(s, s.ranks.get("b"), "x").cycle());
    }

    @Test
    void selfParentIsACycle() {
        RankState s = new RankState();
        Holder a = rank(s, "a", "power", "1", "parent", "a");
        assertTrue(Resolver.resolve(s, a, "x").cycle());
        // a local value is still found without ever looking at the parents
        a.set("x", "ok");
        Resolver.Resolution r = Resolver.resolve(s, a, "x");
        assertFalse(r.cycle());
        assertEquals("ok", r.value());
    }

    @Test
    void valueBeforeTheCycleIsStillFound() {
        RankState s = new RankState();
        rank(s, "a", "power", "1", "parent", "b");
        rank(s, "b", "power", "2", "parent", "a", "x", "found");
        Resolver.Resolution r = Resolver.resolve(s, s.ranks.get("a"), "x");
        assertFalse(r.cycle());
        assertEquals("found", r.value());
    }

    @Test
    void playerResolvesThroughDefaultRanksAndCycleIsDetected() {
        RankState s = new RankState();
        rank(s, "guest", "power", "1", "parent", "admin");
        rank(s, "admin", "power", "9", "parent", "guest");
        Holder p = player(s, U1, true);
        assertTrue(Resolver.resolve(s, p, "x").cycle());

        RankState ok = Fixtures.ladder();
        Holder q = player(ok, U1, false);
        Resolver.Resolution r = Resolver.resolve(ok, q, "a.b");
        assertEquals("true", r.value());
        assertEquals("low", r.source().id);
        assertTrue(r.inherited(q));
    }

    @Test
    void playerEntryBeatsRanksAndOpDefaultAddsHighRank() {
        RankState s = Fixtures.ladder();
        s.ranks.get("high").set("op.only", "yes");
        Holder normal = player(s, U1, false, "a.b", "false");
        assertEquals("false", Resolver.resolve(s, normal, "a.b").value());
        assertEquals("", Resolver.resolve(s, normal, "op.only").value());
        Holder op = player(s, Fixtures.U2, true);
        assertEquals("yes", Resolver.resolve(s, op, "op.only").value());
    }

    @Test
    void asBooleanOnlyAcceptsLowercaseTrue() {
        assertTrue(Resolver.asBoolean("true"));
        assertFalse(Resolver.asBoolean("TRUE"));
        assertFalse(Resolver.asBoolean("false"));
        assertFalse(Resolver.asBoolean(""));
        assertFalse(Resolver.asBoolean("1"));
    }
}
