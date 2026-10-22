package com.theredja.rankeditor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A permission holder: either a named rank or a player's personal rank (FTB Utilities' PlayerRank).
 * Mirrors the on-disk structure: an ordered map of entries plus a section comment.
 */
public final class Holder {

    /** Rank id, or for players the dashed UUID string. */
    public final String id;
    public final boolean player;
    public String comment;
    public final LinkedHashMap<String, Entry> entries;

    public Holder(String id, boolean player, String comment) {
        this.id = id;
        this.player = player;
        this.comment = comment;
        this.entries = new LinkedHashMap<>();
    }

    public Holder copy() {
        return copyAs(id);
    }

    public Holder copyAs(String newId) {
        Holder h = new Holder(newId, player, comment);
        h.entries.putAll(entries);
        return h;
    }

    public String local(String node) {
        Entry e = entries.get(node);
        return e == null ? "" : e.value();
    }

    /** Sets or (with an empty value) removes an entry, keeping position and comment of an existing entry. */
    public void set(String node, String value) {
        if (value.isEmpty()) {
            entries.remove(node);
        } else {
            Entry e = entries.get(node);
            entries.put(node, e == null ? new Entry(node, value, "") : e.withValue(value));
        }
    }

    /** Parent rank ids as written in the {@code parent} entry, in file order. */
    public List<String> parentIds() {
        List<String> list = new ArrayList<>();
        for (String s : local(RankState.NODE_PARENT).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !list.contains(t)) {
                list.add(t);
            }
        }
        return list;
    }

    public void setParentIds(List<String> ids) {
        set(RankState.NODE_PARENT, String.join(", ", ids));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Holder h && h.id.equals(id) && h.player == player && h.comment.equals(comment)
                && List.copyOf(h.entries.values()).equals(List.copyOf(entries.values()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return (player ? "player " : "rank ") + id + " " + entries.values();
    }

    static Map<String, Holder> deepCopy(Map<String, Holder> map) {
        LinkedHashMap<String, Holder> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(k, v.copy()));
        return out;
    }
}
