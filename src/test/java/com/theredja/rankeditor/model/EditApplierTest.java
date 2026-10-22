package com.theredja.rankeditor.model;

import static com.theredja.rankeditor.model.Fixtures.U1;
import static com.theredja.rankeditor.model.Fixtures.U2;
import static com.theredja.rankeditor.model.Fixtures.player;
import static com.theredja.rankeditor.model.Fixtures.rank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theredja.rankeditor.model.Edit.Op;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EditApplierTest {

    private static RankState apply(RankState base, Edit... edits) throws EditException {
        return EditApplier.apply(base, List.of(edits));
    }

    private static String fail(RankState base, Edit... edits) {
        return assertThrows(EditException.class, () -> apply(base, edits)).getMessage();
    }

    private static String keys(Holder h) {
        return String.join(",", h.entries.keySet());
    }

    // ---- SET

    @Test
    void setAddsNewEntryAtTheEnd() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.set(false, "low", "z.new", "false"));
        Holder low = s.ranks.get("low");
        assertEquals("power,a.b,z.new", keys(low));
        assertEquals("false", low.local("z.new"));
        assertEquals("", low.entries.get("z.new").comment());
    }

    @Test
    void setExistingKeepsPositionAndComment() throws Exception {
        RankState base = Fixtures.ladder();
        Holder low = base.ranks.get("low");
        low.entries.put("a.b", low.entries.get("a.b").withComment("why"));
        low.set("c.d", "1");
        RankState s = apply(base, Edit.set(false, "low", "a.b", "false"));
        Holder after = s.ranks.get("low");
        assertEquals("power,a.b,c.d", keys(after));
        assertEquals("false", after.entries.get("a.b").value());
        assertEquals("why", after.entries.get("a.b").comment());
    }

    @Test
    void setEmptyRemovesAndKeepsOthersIntact() throws Exception {
        RankState base = Fixtures.ladder();
        Holder low = base.ranks.get("low");
        low.set("b", "1");
        low.set("c", "2");
        low.entries.put("c", low.entries.get("c").withComment("keep me"));
        RankState s = apply(base, Edit.set(false, "low", "a.b", ""));
        Holder after = s.ranks.get("low");
        assertEquals("power,b,c", keys(after));
        assertEquals("keep me", after.entries.get("c").comment());
        // removing something that isn't there is a no-op
        RankState again = apply(s, Edit.set(false, "low", "not.there", ""));
        assertEquals(s.fingerprint(), again.fingerprint());
    }

    @Test
    void setOnPlayerWorks() throws Exception {
        RankState base = Fixtures.ladder();
        player(base, U1, false);
        RankState s = apply(base, Edit.set(true, U1.toString(), "x.y", "true"));
        assertEquals("true", s.players.get(U1.toString()).local("x.y"));
        // the player holder may be emptied again
        RankState back = apply(s, Edit.set(true, U1.toString(), "x.y", ""));
        assertTrue(back.players.get(U1.toString()).isEmpty());
    }

    @Test
    void setOnUnknownHolderFails() {
        assertTrue(fail(Fixtures.ladder(), Edit.set(false, "nope", "a", "b")).contains("Unknown rank nope"));
        assertTrue(fail(Fixtures.ladder(), Edit.set(true, U1.toString(), "a", "b")).contains("Unknown player"));
        // a player id is not a rank id and vice versa
        RankState s = Fixtures.ladder();
        player(s, U1, false);
        assertTrue(fail(s, Edit.set(false, U1.toString(), "a", "b")).contains("Unknown rank"));
        assertTrue(fail(s, Edit.set(true, "low", "a", "b")).contains("Unknown player"));
    }

    // ---- SET_COMMENT

    @Test
    void entryCommentIsSetAndCleared() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.comment(false, "low", "a.b", "hello"));
        assertEquals("hello", s.ranks.get("low").entries.get("a.b").comment());
        assertEquals("true", s.ranks.get("low").local("a.b"));
        RankState cleared = apply(s, Edit.comment(false, "low", "a.b", ""));
        assertEquals("", cleared.ranks.get("low").entries.get("a.b").comment());
        assertEquals("power,a.b", keys(cleared.ranks.get("low")));
    }

    @Test
    void sectionCommentOnRank() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.comment(false, "mid", "", "the middle"));
        assertEquals("the middle", s.ranks.get("mid").comment);
    }

    @Test
    void sectionCommentOnPlayerIsRejected() {
        RankState base = Fixtures.ladder();
        player(base, U1, false);
        assertTrue(fail(base, Edit.comment(true, U1.toString(), "", "x")).contains("Player section comments"));
        // entry comments on players are fine
        player(base, U2, false, "n", "1");
        assertDoesNotThrow(() -> apply(base, Edit.comment(true, U2.toString(), "n", "x")));
    }

    private static void assertDoesNotThrow(org.junit.jupiter.api.function.Executable e) {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(e);
    }

    @Test
    void commentOnMissingEntryFails() {
        assertTrue(fail(Fixtures.ladder(), Edit.comment(false, "low", "nope", "x")).contains("not set"));
    }

    @Test
    void commentTextRules() {
        RankState s = Fixtures.ladder();
        assertDoesNotThrow(() -> apply(s, Edit.comment(false, "low", "a.b", "x".repeat(EditApplier.MAX_COMMENT_LENGTH))));
        assertTrue(fail(s, Edit.comment(false, "low", "a.b", "x".repeat(EditApplier.MAX_COMMENT_LENGTH + 1))).contains("longer"));
        assertTrue(fail(s, Edit.comment(false, "low", "a.b", "a\nb")).contains("control"));
        assertTrue(fail(s, Edit.comment(false, "low", "a.b", "a\rb")).contains("control"));
        assertTrue(fail(s, Edit.comment(false, "low", "", "tab\there")).contains("control"));
        assertDoesNotThrow(() -> apply(s, Edit.comment(false, "low", "a.b", "  spaces and \"quotes\" are fine ")));
    }

    // ---- CREATE_RANK

    @Test
    void createWithoutCopyHasOnlyPower() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.createRank("newbie", "", 3));
        assertEquals("low,mid,high,newbie", String.join(",", s.ranks.keySet()));
        Holder n = s.ranks.get("newbie");
        assertEquals("power", keys(n));
        assertEquals("3", n.local("power"));
        assertEquals("", n.comment);
        assertFalse(n.player);
    }

    @Test
    void createWithPowerZeroStillWritesPower() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.createRank("zero", "", 0));
        assertEquals("0", s.ranks.get("zero").local("power"));
        assertEquals(1, s.ranks.get("zero").entries.size());
    }

    @Test
    void createWithCopyCopiesEntriesButNotDefaultFlags() throws Exception {
        RankState base = new RankState();
        Holder src = rank(base, "src", "default_player_rank", "true", "power", "7", "default_op_rank", "true", "a.b", "true",
                "parent", "other");
        src.comment = "source comment";
        src.entries.put("a.b", src.entries.get("a.b").withComment("entry comment"));
        rank(base, "other", "power", "1");
        RankState s = apply(base, Edit.createRank("clone", "src", 40));
        Holder c = s.ranks.get("clone");
        assertEquals("power,a.b,parent", keys(c), "flags dropped; power keeps source position");
        assertEquals("40", c.local("power"), "power is overwritten by the requested one");
        assertEquals("true", c.local("a.b"));
        assertEquals("entry comment", c.entries.get("a.b").comment());
        assertEquals("other", c.local("parent"));
        // source untouched
        assertEquals("true", s.ranks.get("src").local("default_player_rank"));
        assertEquals("7", s.ranks.get("src").local("power"));
        assertEquals("clone", s.ranks.lastEntry().getKey());
    }

    @Test
    void createWithCopyWritesPowerEvenWhenSourceHasNone() throws Exception {
        RankState base = new RankState();
        rank(base, "src", "a", "true");
        RankState s = apply(base, Edit.createRank("c", "src", 2));
        assertEquals("a,power", keys(s.ranks.get("c")));
    }

    @Test
    void createFromMissingSourceFails() {
        assertTrue(fail(Fixtures.ladder(), Edit.createRank("x", "ghost", 1)).contains("Unknown rank ghost"));
    }

    @Test
    void createPowerRange() throws Exception {
        RankState s = Fixtures.ladder();
        assertTrue(fail(s, Edit.createRank("x", "", -1)).contains("Power must be between"));
        assertTrue(fail(s, Edit.createRank("x", "", RankState.MAX_POWER + 1)).contains("Power must be between"));
        assertEquals(Integer.toString(RankState.MAX_POWER),
                apply(s, Edit.createRank("x", "", RankState.MAX_POWER)).ranks.get("x").local("power"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "none", "Upper", "has space", "dash-name", "dot.name", "ümlaut", "0123456789abcdef0123456789abcdef",
            "ffffffffffffffffffffffffffffffff", "low" })
    void createRejectsBadNames(String name) {
        String msg = fail(Fixtures.ladder(), Edit.createRank(name, "", 1));
        assertNotNull(msg);
    }

    @Test
    void createNameMessages() {
        RankState s = Fixtures.ladder();
        assertTrue(fail(s, Edit.createRank("none", "", 1)).contains("Invalid rank name"));
        assertTrue(fail(s, Edit.createRank("low", "", 1)).contains("already exists"));
        assertTrue(fail(s, Edit.createRank("0123456789abcdef0123456789abcdef", "", 1)).contains("UUID"));
    }

    @Test
    void createNameEdgeCasesThatAreFine() throws Exception {
        RankState s = Fixtures.ladder();
        // 31 hex chars, 32 non-hex chars, 33 hex chars and uppercase hex (invalid anyway) are not UUID look-alikes
        for (String ok : new String[] { "0123456789abcdef0123456789abcde", "g123456789abcdef0123456789abcdef",
                "0123456789abcdef0123456789abcdef0", "none1", "_", "0" }) {
            assertTrue(apply(s, Edit.createRank(ok, "", 1)).ranks.containsKey(ok), ok);
        }
    }

    // ---- DELETE_RANK

    @Test
    void deleteRemovesRankAndStripsParentReferencesEverywhere() throws Exception {
        RankState base = new RankState();
        rank(base, "base", "power", "1", "a", "1");
        rank(base, "x", "power", "2", "parent", "base");
        rank(base, "y", "power", "3", "parent", "x, base, x");
        rank(base, "z", "power", "4", "parent", "y");
        player(base, U1, false, "parent", "base, z");
        player(base, U2, false, "parent", "base");
        RankState s = apply(base, Edit.deleteRank("base"));
        assertEquals("x,y,z", String.join(",", s.ranks.keySet()));
        assertEquals("", s.ranks.get("x").local("parent"));
        assertEquals("power", keys(s.ranks.get("x")), "emptied parent entry is removed");
        assertEquals("x", s.ranks.get("y").local("parent"));
        assertEquals("y", s.ranks.get("z").local("parent"));
        assertEquals("z", s.players.get(U1.toString()).local("parent"));
        assertTrue(s.players.get(U2.toString()).isEmpty());
        assertTrue(EditApplier.problems(s).isEmpty(), EditApplier.problems(s).toString());
    }

    @Test
    void deleteMissingRankFails() {
        assertTrue(fail(Fixtures.ladder(), Edit.deleteRank("ghost")).contains("Unknown rank"));
    }

    @Test
    void deleteLastRank() throws Exception {
        RankState base = new RankState();
        rank(base, "only", "power", "1");
        assertTrue(apply(base, Edit.deleteRank("only")).ranks.isEmpty());
    }

    @Test
    void deleteCanLeaveRankWithoutEntriesWhichIsRejected() {
        // rank b has nothing but a parent entry; stripping it leaves an entry-less rank FTBU would drop on save
        RankState base = new RankState();
        rank(base, "a", "power", "1");
        rank(base, "b", "parent", "a");
        String msg = fail(base, Edit.deleteRank("a"));
        assertTrue(msg.contains("no entries"), msg);
    }

    // ---- RENAME_RANK

    @Test
    void renameKeepsPositionAndRewritesParents() throws Exception {
        RankState base = new RankState();
        rank(base, "first", "power", "1");
        rank(base, "second", "power", "2", "parent", "first");
        rank(base, "third", "power", "3", "parent", "second, first");
        rank(base, "fourth", "power", "4");
        player(base, U1, false, "parent", "first, third");
        RankState s = apply(base, Edit.renameRank("second", "renamed"));
        assertEquals("first,renamed,third,fourth", String.join(",", s.ranks.keySet()));
        assertEquals("renamed", s.ranks.get("renamed").id);
        assertEquals("2", s.ranks.get("renamed").local("power"));
        assertEquals("first", s.ranks.get("renamed").local("parent"));
        assertEquals("renamed, first", s.ranks.get("third").local("parent"), "position inside the list is kept");
        assertEquals("first, third", s.players.get(U1.toString()).local("parent"));
        assertNull(s.ranks.get("second"));
        // base untouched
        assertTrue(base.ranks.containsKey("second"));
        assertEquals("second, first", base.ranks.get("third").local("parent"));
    }

    @Test
    void renamePlayerParentReference() throws Exception {
        RankState base = Fixtures.ladder();
        player(base, U1, false, "parent", "low, mid");
        RankState s = apply(base, Edit.renameRank("mid", "middle"));
        assertEquals("low, middle", s.players.get(U1.toString()).local("parent"));
    }

    @Test
    void renameToSameNameIsNoop() throws Exception {
        RankState base = Fixtures.ladder();
        RankState s = apply(base, Edit.renameRank("mid", "mid"));
        assertEquals(base.fingerprint(), s.fingerprint());
    }

    @Test
    void renameValidation() {
        RankState s = Fixtures.ladder();
        assertTrue(fail(s, Edit.renameRank("ghost", "x")).contains("Unknown rank"));
        assertTrue(fail(s, Edit.renameRank("mid", "")).contains("Invalid rank name"));
        assertTrue(fail(s, Edit.renameRank("mid", "none")).contains("Invalid rank name"));
        assertTrue(fail(s, Edit.renameRank("mid", "Mid")).contains("Invalid rank name"));
        assertTrue(fail(s, Edit.renameRank("mid", "high")).contains("already exists"));
        assertTrue(fail(s, Edit.renameRank("mid", "0123456789abcdef0123456789abcdef")).contains("UUID"));
    }

    @Test
    void renameOntoDanglingParentNameCreatesLoopAndIsRejected() {
        // c's parent 'b' doesn't exist (pre-existing problem). Renaming a to b makes it resolve: a -> c -> b(=a)
        RankState base = new RankState();
        rank(base, "a", "power", "1", "parent", "c");
        rank(base, "c", "power", "2", "parent", "b");
        assertFalse(EditApplier.problems(base).isEmpty());
        String msg = fail(base, Edit.renameRank("a", "b"));
        assertTrue(msg.startsWith("Parent loop"), msg);
    }

    // ---- validation of SET

    @Test
    void nodeRules() {
        RankState s = Fixtures.ladder();
        for (String bad : new String[] { "", " ", "a b", "a\tb", "a:b", ":", "a\nb", "[x", "//x", "//", "x\u0000" }) {
            assertThrows(EditException.class, () -> apply(s, Edit.set(false, "low", bad, "v")), "node '" + bad + "'");
        }
        for (String ok : new String[] { "a", "*", "a.b.c", "x[y", "/x", "a/b", "a//b", "a-b", "ä" }) {
            assertDoesNotThrow(() -> apply(s, Edit.set(false, "low", ok, "v")));
        }
        assertDoesNotThrow(() -> apply(s, Edit.set(false, "low", "n".repeat(EditApplier.MAX_NODE_LENGTH), "v")));
        assertTrue(fail(s, Edit.set(false, "low", "n".repeat(EditApplier.MAX_NODE_LENGTH + 1), "v")).contains("longer"));
    }

    @Test
    void valueRules() {
        RankState s = Fixtures.ladder();
        for (String bad : new String[] { " x", "x ", "a\nb", "a\tb", "\"x\"", "\"abc\"", "\"" + "a b" + "\"" }) {
            assertThrows(EditException.class, () -> apply(s, Edit.set(false, "low", "n", bad)), "value '" + bad + "'");
        }
        for (String ok : new String[] { "x y", "\"", "\"\"", "\"a", "a\"", "'x'", "<&b{name}&r>", "ü", "" }) {
            assertDoesNotThrow(() -> apply(s, Edit.set(false, "low", "n", ok)));
        }
        assertDoesNotThrow(() -> apply(s, Edit.set(false, "low", "n", "v".repeat(EditApplier.MAX_VALUE_LENGTH))));
        assertTrue(fail(s, Edit.set(false, "low", "n", "v".repeat(EditApplier.MAX_VALUE_LENGTH + 1))).contains("longer"));
    }

    @Test
    void textRulesAreAlsoCheckedByThePublicHelpers() {
        assertThrows(EditException.class, () -> EditApplier.checkNode("a b"));
        assertThrows(EditException.class, () -> EditApplier.checkValue("\"q\""));
        assertThrows(EditException.class, () -> EditApplier.checkComment("a\nb"));
    }

    // ---- global invariants

    @Test
    void parentLoopViaSetIsRejected() {
        RankState s = Fixtures.ladder(); // high -> mid -> low
        String msg = fail(s, Edit.set(false, "low", "parent", "high"));
        assertTrue(msg.startsWith("Parent loop"), msg);
        assertTrue(msg.contains("low") && msg.contains("high"), msg);
    }

    @Test
    void selfParentIsRejected() {
        String msg = fail(Fixtures.ladder(), Edit.set(false, "low", "parent", "low"));
        assertTrue(msg.contains("own parent") || msg.startsWith("Parent loop"), msg);
    }

    @Test
    void missingParentIsRejected() {
        assertTrue(fail(Fixtures.ladder(), Edit.set(false, "low", "parent", "ghost")).contains("doesn't exist"));
        assertTrue(fail(Fixtures.ladder(), Edit.set(false, "low", "parent", "none")).contains("doesn't exist"));
        RankState s = Fixtures.ladder();
        player(s, U1, false);
        assertTrue(fail(s, Edit.set(true, U1.toString(), "parent", "ghost")).contains("doesn't exist"));
    }

    @Test
    void playerMayNotHaveRankOnlyNodes() {
        RankState s = Fixtures.ladder();
        player(s, U1, false);
        for (String n : new String[] { "power", "default_player_rank", "default_op_rank" }) {
            assertTrue(fail(s, Edit.set(true, U1.toString(), n, "true")).contains("no effect on a player"), n);
        }
    }

    @Test
    void secondExplicitDefaultIsRejected() throws Exception {
        RankState s = Fixtures.ladder();
        RankState one = apply(s, Edit.set(false, "low", "default_player_rank", "true"));
        String msg = fail(one, Edit.set(false, "mid", "default_player_rank", "true"));
        assertTrue(msg.contains("Only one rank can be default_player_rank"), msg);
        // flag false on another one is fine, and moving the flag in one batch is fine
        assertDoesNotThrow(() -> apply(one, Edit.set(false, "mid", "default_player_rank", "false")));
        assertDoesNotThrow(() -> apply(one, Edit.set(false, "low", "default_player_rank", ""),
                Edit.set(false, "mid", "default_player_rank", "true")));
        String op = fail(apply(s, Edit.set(false, "low", "default_op_rank", "true")), Edit.set(false, "high", "default_op_rank", "true"));
        assertTrue(op.contains("default_op_rank"), op);
        // the same rank may be both defaults
        assertDoesNotThrow(() -> apply(s, Edit.set(false, "low", "default_op_rank", "true"),
                Edit.set(false, "low", "default_player_rank", "true")));
    }

    @Test
    void flagMustBeBoolean() {
        assertTrue(fail(Fixtures.ladder(), Edit.set(false, "low", "default_op_rank", "yes")).contains("must be true or false"));
    }

    @Test
    void powerMustBeAnInt() throws Exception {
        RankState s = Fixtures.ladder();
        assertTrue(fail(s, Edit.set(false, "low", "power", "abc")).contains("whole number"));
        assertTrue(fail(s, Edit.set(false, "low", "power", "1.5")).contains("whole number"));
        assertTrue(fail(s, Edit.set(false, "low", "power", "99999999999")).contains("whole number"));
        assertTrue(fail(s, Edit.set(false, "low", "power", "-1")).contains("between 0 and"));
        assertTrue(fail(s, Edit.set(false, "low", "power", "2147483647")).contains("between 0 and"));
        assertEquals("2147483646", apply(s, Edit.set(false, "low", "power", "2147483646")).ranks.get("low").local("power"));
        assertEquals("0", apply(s, Edit.set(false, "low", "power", "0")).ranks.get("low").local("power"));
    }

    @Test
    void clearingAllEntriesOfARankIsRejected() throws Exception {
        RankState s = Fixtures.ladder();
        // 'high' has power + parent
        String msg = fail(s, Edit.set(false, "high", "power", ""), Edit.set(false, "high", "parent", ""));
        assertTrue(msg.contains("no entries"), msg);
        // removing only one of them is fine
        assertDoesNotThrow(() -> apply(s, Edit.set(false, "high", "power", "")));
        // clearing and re-adding in the same batch is fine (checked on the final state)
        assertDoesNotThrow(() -> apply(s, Edit.set(false, "high", "power", ""), Edit.set(false, "high", "parent", ""),
                Edit.set(false, "high", "x", "1")));
        // players may be empty
        RankState p = Fixtures.ladder();
        player(p, U1, false, "x", "1");
        assertTrue(apply(p, Edit.set(true, U1.toString(), "x", "")).players.get(U1.toString()).isEmpty());
    }

    @Test
    void preExistingEmptyRankDoesNotBlockUnrelatedEdits() {
        RankState base = Fixtures.ladder();
        base.ranks.put("empty", new Holder("empty", false, ""));
        assertDoesNotThrow(() -> apply(base, Edit.set(false, "low", "n", "1")));
    }

    // ---- pre-existing problems vs new ones

    private static RankState broken() {
        RankState s = new RankState();
        rank(s, "a", "power", "x1", "parent", "ghost", "default_player_rank", "true");
        rank(s, "b", "power", "2", "default_player_rank", "true");
        return s;
    }

    @Test
    void preExistingProblemsDoNotBlockUnrelatedEdits() throws Exception {
        RankState base = broken();
        assertFalse(EditApplier.problems(base).isEmpty());
        RankState s = apply(base, Edit.set(false, "b", "some.node", "true"), Edit.comment(false, "b", "", "hi"));
        assertEquals("true", s.ranks.get("b").local("some.node"));
        assertEquals(EditApplier.problems(base), EditApplier.problems(s));
    }

    @Test
    void newProblemsStillBlockOnTopOfBrokenBase() {
        RankState base = broken();
        assertTrue(fail(base, Edit.set(false, "b", "parent", "other")).contains("'other'"));
        assertTrue(fail(base, Edit.set(false, "b", "power", "bad")).contains("Power of b"));
        assertTrue(fail(base, Edit.createRank("a_new", "", 1), Edit.set(false, "a_new", "parent", "a_new")).length() > 0);
    }

    @Test
    void fixingAProblemIsAllowed() throws Exception {
        RankState base = broken();
        RankState s = apply(base, Edit.set(false, "a", "parent", ""), Edit.set(false, "a", "power", "1"),
                Edit.set(false, "a", "default_player_rank", ""));
        assertTrue(EditApplier.problems(s).isEmpty(), EditApplier.problems(s).toString());
    }

    // ---- batch behaviour

    @Test
    void editsApplyInOrder() throws Exception {
        RankState s = apply(Fixtures.ladder(), Edit.createRank("n", "", 5), Edit.set(false, "n", "x", "1"),
                Edit.renameRank("n", "m"), Edit.set(false, "m", "y", "2"), Edit.createRank("n", "m", 6), Edit.deleteRank("m"));
        assertEquals("low,mid,high,n", String.join(",", s.ranks.keySet()));
        assertEquals("power,x,y", keys(s.ranks.get("n")));
        assertEquals("6", s.ranks.get("n").local("power"));
    }

    @Test
    void emptyBatchReturnsEqualCopy() throws Exception {
        RankState base = Fixtures.ladder();
        RankState s = apply(base);
        assertEquals(base.fingerprint(), s.fingerprint());
        assertTrue(s != base);
    }

    @Test
    void maxEditsLimit() throws Exception {
        List<Edit> ok = new ArrayList<>();
        for (int i = 0; i < EditApplier.MAX_EDITS; i++) {
            ok.add(Edit.set(false, "low", "n", Integer.toString(i)));
        }
        assertEquals("1023", EditApplier.apply(Fixtures.ladder(), ok).ranks.get("low").local("n"));
        ok.add(Edit.set(false, "low", "n", "x"));
        assertTrue(assertThrows(EditException.class, () -> EditApplier.apply(Fixtures.ladder(), ok)).getMessage().contains("Too many"));
    }

    @Test
    void failingBatchLeavesInputUnchanged() {
        RankState base = Fixtures.ladder();
        player(base, U1, false, "parent", "mid");
        RankState snapshot = base.copy();
        long fp = base.fingerprint();
        List<Edit> batch = List.of(Edit.set(false, "low", "x", "1"), Edit.deleteRank("mid"), Edit.renameRank("high", "top"),
                Edit.createRank("new", "low", 3), Edit.set(false, "ghost", "x", "y"));
        assertThrows(EditException.class, () -> EditApplier.apply(base, batch));
        assertEquals(fp, base.fingerprint());
        assertEquals(snapshot.ranks, base.ranks);
        assertEquals(snapshot.players, base.players);
        assertEquals(snapshot.playerInfo, base.playerInfo);
        assertEquals(List.copyOf(snapshot.ranks.keySet()), List.copyOf(base.ranks.keySet()));
    }

    @Test
    void failingBecauseOfFinalValidationAlsoLeavesInputUnchanged() {
        RankState base = Fixtures.ladder();
        long fp = base.fingerprint();
        assertThrows(EditException.class, () -> apply(base, Edit.set(false, "low", "x", "1"), Edit.set(false, "low", "parent", "high")));
        assertEquals(fp, base.fingerprint());
    }

    @Test
    void successfulApplyDoesNotMutateInput() throws Exception {
        RankState base = Fixtures.ladder();
        player(base, U1, false, "parent", "mid");
        long fp = base.fingerprint();
        apply(base, Edit.deleteRank("mid"), Edit.renameRank("high", "top"), Edit.set(false, "low", "z", "1"));
        assertEquals(fp, base.fingerprint());
        assertEquals("mid", base.players.get(U1.toString()).local("parent"));
    }

    @Test
    void appliedStateFingerprintDiffersFromBaseOnlyWhenChanged() throws Exception {
        RankState base = Fixtures.ladder();
        assertEquals(base.fingerprint(), apply(base, Edit.set(false, "low", "a.b", "true")).fingerprint(), "same value");
        assertTrue(base.fingerprint() != apply(base, Edit.set(false, "low", "a.b", "false")).fingerprint());
        assertTrue(base.fingerprint() != apply(base, Edit.comment(false, "low", "a.b", "c")).fingerprint());
    }

    @Test
    void editFactoriesAndOpLookup() {
        assertEquals(Op.SET, Edit.set(false, "t", "n", "v").op());
        assertEquals(Op.SET_COMMENT, Edit.comment(true, "t", "n", "v").op());
        Edit c = Edit.createRank("id", "src", 4);
        assertEquals(Op.CREATE_RANK, c.op());
        assertEquals("src", c.a());
        assertEquals(4, c.power());
        assertEquals(Op.DELETE_RANK, Edit.deleteRank("id").op());
        assertEquals("new", Edit.renameRank("id", "new").a());
        assertEquals(Op.RENAME_RANK, Op.byOrdinal(Op.RENAME_RANK.ordinal()));
        assertNull(Op.byOrdinal(-1));
        assertNull(Op.byOrdinal(99));
    }

    @Test
    void findCycleDescribesThePath() {
        RankState s = new RankState();
        rank(s, "a", "power", "1", "parent", "b");
        rank(s, "b", "power", "1", "parent", "c");
        rank(s, "c", "power", "1", "parent", "a");
        assertEquals("a -> b -> c -> a", EditApplier.findCycle(s));
        assertNull(EditApplier.findCycle(Fixtures.ladder()));
        RankState diamond = new RankState();
        rank(diamond, "t", "power", "1");
        rank(diamond, "l", "power", "1", "parent", "t");
        rank(diamond, "r", "power", "1", "parent", "t");
        rank(diamond, "b", "power", "1", "parent", "l, r");
        assertNull(EditApplier.findCycle(diamond));
    }
}
