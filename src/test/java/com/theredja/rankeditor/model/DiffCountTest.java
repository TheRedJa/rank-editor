package com.theredja.rankeditor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiffCountTest {

    private static RankState apply(RankState base, Edit... edits) throws EditException {
        return EditApplier.apply(base, List.of(edits));
    }

    @Test
    void togglingBackToTheOriginalValueIsNoChange() throws EditException {
        RankState base = Fixtures.ladder();
        RankState after = apply(base,
                Edit.set(false, "high", "a.b", "true"),
                Edit.set(false, "high", "a.b", "false"),
                Edit.set(false, "high", "a.b", ""));
        assertEquals(0, RankState.diffCount(base, after));
        assertEquals(base.fingerprint(), after.fingerprint());
    }

    @Test
    void repeatedEditsOfOneNodeCountOnce() throws EditException {
        RankState base = Fixtures.ladder();
        RankState after = apply(base,
                Edit.set(false, "high", "a.b", "true"),
                Edit.set(false, "high", "a.b", "false"),
                Edit.set(false, "high", "a.b", "true"));
        assertEquals(1, RankState.diffCount(base, after));
    }

    @Test
    void countsEntriesCommentsAndSections() throws EditException {
        RankState base = Fixtures.ladder();
        RankState after = apply(base,
                Edit.set(false, "high", "x.y", "true"),
                Edit.set(false, "high", "z", "1"),
                Edit.comment(false, "high", "", "hello"),
                Edit.createRank("mod", "", 50));
        assertEquals(4, RankState.diffCount(base, after));
    }

    @Test
    void reorderOnlyStillCountsAsAChange() {
        RankState base = new RankState();
        Fixtures.rank(base, "a", "power", "1");
        Fixtures.rank(base, "b", "power", "2");
        RankState after = new RankState();
        after.ranks.put("b", base.ranks.get("b").copy());
        after.ranks.put("a", base.ranks.get("a").copy());
        assertEquals(1, RankState.diffCount(base, after));
    }
}
