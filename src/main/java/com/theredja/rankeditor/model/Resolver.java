package com.theredja.rankeditor.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Computes the effective value of a node for a holder exactly like FTB Utilities' {@code Rank.getPermission(original,
 * node, recursive=true)}, and also reports where the value came from.
 *
 * <p>Lookup order: the holder's own entry, then each actual parent depth-first (parents sorted by power, highest
 * first). If nothing is found, the same is repeated for the parent node ({@code a.b.c} → {@code a.b} → {@code a}),
 * and finally for {@code *}.
 */
public final class Resolver {

    /**
     * @param value     the raw string, empty when unset everywhere
     * @param source    holder that defines it, or null
     * @param matchedNode the node that matched (differs from the queried node for wildcard matches)
     * @param cycle     true when a parent cycle was hit (FTB Utilities would overflow the stack here)
     */
    public record Resolution(String value, Holder source, String matchedNode, boolean cycle) {

        public boolean isSet() {
            return !value.isEmpty();
        }

        public boolean inherited(Holder from) {
            return source != null && source != from;
        }

        public boolean wildcard(String queried) {
            return matchedNode != null && !matchedNode.equals(queried);
        }
    }

    private static final Resolution NONE = new Resolution("", null, null, false);

    private Resolver() {
    }

    public static Resolution resolve(RankState state, Holder holder, String node) {
        String n = node;
        while (true) {
            Resolution r = lookup(state, holder, n, true, new HashSet<>());
            if (r.isSet() || r.cycle()) {
                return r;
            }
            int i = n.lastIndexOf('.');
            if (i != -1) {
                n = n.substring(0, i);
            } else if (!n.equals("*")) {
                n = "*";
            } else {
                return NONE;
            }
        }
    }

    /** Only the holder's own entry and its parents, no wildcard fallback. */
    public static Resolution resolveExact(RankState state, Holder holder, String node) {
        return lookup(state, holder, node, true, new HashSet<>());
    }

    private static Resolution lookup(RankState state, Holder h, String node, boolean top, Set<String> visiting) {
        String s = h.local(node);
        if (!s.isEmpty()) {
            return new Resolution(s, h, node, false);
        }
        // Only named ranks can be revisited; the top holder may be a player (players are never parents).
        String key = (h.player ? "p:" : "r:") + h.id;
        if (!visiting.add(key)) {
            return new Resolution("", null, null, true);
        }
        try {
            for (Holder p : top ? state.actualParents(h) : state.parents(h)) {
                Resolution r = lookup(state, p, node, false, visiting);
                if (r.isSet() || r.cycle()) {
                    return r;
                }
            }
            return NONE;
        } finally {
            visiting.remove(key);
        }
    }

    /** FTB Utilities treats exactly {@code "true"} as allow; any other non-empty value denies. */
    public static boolean asBoolean(String value) {
        return value.equals("true");
    }
}
