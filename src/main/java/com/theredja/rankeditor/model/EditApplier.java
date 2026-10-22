package com.theredja.rankeditor.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies an edit batch to a copy of a {@link RankState} and validates the result. Pure and side-effect free: the
 * client uses it to preview staged edits, the server uses it to apply them atomically (all or nothing).
 *
 * <p>The text rules guarantee that what FTB Utilities writes to ranks.txt / players.txt reads back identically.
 */
public final class EditApplier {

    public static final int MAX_EDITS = 1024;
    public static final int MAX_NODE_LENGTH = 256;
    public static final int MAX_VALUE_LENGTH = 2048;
    public static final int MAX_COMMENT_LENGTH = 512;

    private EditApplier() {
    }

    public static RankState apply(RankState base, List<Edit> edits) throws EditException {
        if (edits.size() > MAX_EDITS) {
            throw new EditException("Too many changes in one batch (" + edits.size() + ", max " + MAX_EDITS + ")");
        }
        RankState s = base.copy();
        for (Edit e : edits) {
            applyOne(s, e);
        }
        // Problems already present in the loaded files must not block unrelated edits; only new ones are rejected.
        List<String> before = problems(base);
        for (String p : problems(s)) {
            if (!before.contains(p)) {
                throw new EditException(p);
            }
        }
        return s;
    }

    private static void applyOne(RankState s, Edit e) throws EditException {
        switch (e.op()) {
            case SET -> {
                Holder h = requireHolder(s, e.player(), e.target());
                checkNode(e.a());
                checkValue(e.b());
                h.set(e.a(), e.b());
            }
            case SET_COMMENT -> {
                Holder h = requireHolder(s, e.player(), e.target());
                checkComment(e.b());
                if (e.a().isEmpty()) {
                    if (h.player) {
                        throw new EditException("Player section comments hold the player name and can't be edited");
                    }
                    h.comment = e.b();
                } else {
                    Entry entry = h.entries.get(e.a());
                    if (entry == null) {
                        throw new EditException("Can't comment '" + e.a() + "': not set on " + s.displayName(h));
                    }
                    h.entries.put(e.a(), entry.withComment(e.b()));
                }
            }
            case CREATE_RANK -> {
                checkNewRankName(s, e.target());
                if (e.power() < 0 || e.power() > RankState.MAX_POWER) {
                    throw new EditException("Power must be between 0 and " + RankState.MAX_POWER);
                }
                Holder h = new Holder(e.target(), false, "");
                if (!e.a().isEmpty()) {
                    Holder from = requireHolder(s, false, e.a());
                    h.entries.putAll(from.entries);
                    h.entries.remove(RankState.NODE_DEFAULT_PLAYER);
                    h.entries.remove(RankState.NODE_DEFAULT_OP);
                    h.comment = from.comment;
                }
                // FTB Utilities drops entry-less ranks on save, so power is always written.
                h.set(RankState.NODE_POWER, Integer.toString(e.power()));
                s.ranks.put(h.id, h);
            }
            case DELETE_RANK -> {
                Holder h = requireHolder(s, false, e.target());
                s.ranks.remove(h.id);
                forEachHolder(s, other -> {
                    List<String> parents = other.parentIds();
                    if (parents.remove(h.id)) {
                        other.setParentIds(parents);
                    }
                });
            }
            case RENAME_RANK -> {
                Holder h = requireHolder(s, false, e.target());
                if (h.id.equals(e.a())) {
                    return;
                }
                checkNewRankName(s, e.a());
                Map<String, Holder> reordered = new LinkedHashMap<>();
                for (Holder r : s.ranks.values()) {
                    Holder put = r == h ? h.copyAs(e.a()) : r;
                    reordered.put(put.id, put);
                }
                s.ranks.clear();
                s.ranks.putAll(reordered);
                forEachHolder(s, other -> {
                    List<String> parents = other.parentIds();
                    int i = parents.indexOf(h.id);
                    if (i != -1) {
                        parents.set(i, e.a());
                        other.setParentIds(parents);
                    }
                });
            }
        }
    }

    private interface HolderAction {
        void run(Holder h);
    }

    private static void forEachHolder(RankState s, HolderAction action) {
        s.ranks.values().forEach(action::run);
        s.players.values().forEach(action::run);
    }

    private static Holder requireHolder(RankState s, boolean player, String id) throws EditException {
        Holder h = s.holder(player, id);
        if (h == null) {
            throw new EditException((player ? "Unknown player " : "Unknown rank ") + id);
        }
        return h;
    }

    private static void checkNewRankName(RankState s, String id) throws EditException {
        if (!RankState.isValidRankName(id)) {
            throw new EditException("Invalid rank name '" + id + "': use lowercase letters, digits and _ ('none' is reserved)");
        }
        if (s.ranks.containsKey(id)) {
            throw new EditException("Rank '" + id + "' already exists");
        }
        if (looksLikePlayerSection(id)) {
            throw new EditException("Rank name '" + id + "' would be read back as a player UUID");
        }
    }

    /** FTB Utilities reads a section whose name parses as an undashed UUID of a known player as that player. */
    private static boolean looksLikePlayerSection(String id) {
        return id.length() == 32 && id.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }

    public static void checkNode(String node) throws EditException {
        if (node.isEmpty()) {
            throw new EditException("Permission node is empty");
        }
        if (node.length() > MAX_NODE_LENGTH) {
            throw new EditException("Permission node is longer than " + MAX_NODE_LENGTH + " characters");
        }
        for (int i = 0; i < node.length(); i++) {
            char c = node.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || c == ':') {
                throw new EditException("Permission node '" + node + "' contains a space, ':' or control character");
            }
        }
        if (node.startsWith("[") || node.startsWith("//")) {
            throw new EditException("Permission node can't start with '[' or '//'");
        }
    }

    public static void checkValue(String value) throws EditException {
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new EditException("Value is longer than " + MAX_VALUE_LENGTH + " characters");
        }
        if (hasControlChars(value)) {
            throw new EditException("Value contains a line break or control character");
        }
        if (!value.equals(value.trim())) {
            throw new EditException("Value can't start or end with spaces (FTB Utilities trims them)");
        }
        if (value.length() > 2 && value.startsWith("\"") && value.endsWith("\"")) {
            throw new EditException("Value can't be wrapped in quotes (FTB Utilities strips them)");
        }
    }

    public static void checkComment(String comment) throws EditException {
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw new EditException("Comment is longer than " + MAX_COMMENT_LENGTH + " characters");
        }
        if (hasControlChars(comment)) {
            throw new EditException("Comment contains a line break or control character");
        }
    }

    private static boolean hasControlChars(String s) {
        return s.chars().anyMatch(Character::isISOControl);
    }

    /** Global invariants that FTB Utilities relies on but does not check itself. Empty when the state is sound. */
    public static List<String> problems(RankState s) {
        List<String> out = new ArrayList<>();
        for (Holder r : s.ranks.values()) {
            if (r.isEmpty()) {
                out.add("Rank " + r.id + " has no entries; FTB Utilities drops such ranks when saving (set a power)");
            }
            String power = r.local(RankState.NODE_POWER);
            if (!power.isEmpty()) {
                // Rank.getPower() calls Integer.parseInt without a catch; a bad value breaks every lookup.
                try {
                    int p = Integer.parseInt(power);
                    if (p < 0 || p > RankState.MAX_POWER) {
                        out.add("Power of " + r.id + " must be between 0 and " + RankState.MAX_POWER);
                    }
                } catch (NumberFormatException ex) {
                    out.add("Power of " + r.id + " must be a whole number, got '" + power + "'");
                }
            }
            for (String flag : List.of(RankState.NODE_DEFAULT_PLAYER, RankState.NODE_DEFAULT_OP)) {
                String v = r.local(flag);
                if (!v.isEmpty() && !v.equals("true") && !v.equals("false")) {
                    out.add(flag + " of " + r.id + " must be true or false");
                }
            }
            parentProblems(s, r, out);
        }
        for (String flag : List.of(RankState.NODE_DEFAULT_PLAYER, RankState.NODE_DEFAULT_OP)) {
            List<String> flagged = new ArrayList<>();
            for (Holder r : s.ranks.values()) {
                if (r.local(flag).equals("true")) {
                    flagged.add(r.id);
                }
            }
            if (flagged.size() > 1) {
                out.add("Only one rank can be " + flag + ", but " + String.join(", ", flagged) + " are");
            }
        }
        for (Holder h : s.players.values()) {
            parentProblems(s, h, out);
            for (String flag : List.of(RankState.NODE_DEFAULT_PLAYER, RankState.NODE_DEFAULT_OP, RankState.NODE_POWER)) {
                if (!h.local(flag).isEmpty()) {
                    out.add("'" + flag + "' has no effect on a player; remove it from " + s.displayName(h));
                }
            }
        }
        String cycle = findCycle(s);
        if (cycle != null) {
            out.add("Parent loop: " + cycle + " (FTB Utilities would crash on permission checks)");
        }
        return out;
    }

    private static void parentProblems(RankState s, Holder h, List<String> out) {
        for (String p : h.parentIds()) {
            if (!s.ranks.containsKey(p)) {
                out.add("Parent '" + p + "' of " + s.displayName(h) + " doesn't exist");
            } else if (!h.player && p.equals(h.id)) {
                out.add("Rank " + h.id + " can't be its own parent");
            }
        }
    }

    /** Returns a readable cycle like {@code a -> b -> a}, or null. */
    public static String findCycle(RankState s) {
        Set<String> done = new HashSet<>();
        for (String id : s.ranks.keySet()) {
            List<String> path = new ArrayList<>();
            String c = dfs(s, id, path, new HashSet<>(), done);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static String dfs(RankState s, String id, List<String> path, Set<String> onPath, Set<String> done) {
        if (onPath.contains(id)) {
            List<String> loop = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            loop.add(id);
            return String.join(" -> ", loop);
        }
        if (done.contains(id)) {
            return null;
        }
        Holder h = s.ranks.get(id);
        if (h == null) {
            return null;
        }
        path.add(id);
        onPath.add(id);
        for (String p : h.parentIds()) {
            String c = dfs(s, p, path, onPath, done);
            if (c != null) {
                return c;
            }
        }
        onPath.remove(id);
        path.removeLast();
        done.add(id);
        return null;
    }
}
