package com.theredja.rankeditor.model;

import java.util.List;

/**
 * A permission node the server knows about, with enough metadata for the editor to show a typed editor and the
 * fallback value used when no rank sets it.
 *
 * @param kind          where the node comes from
 * @param type          {@code boolean} for permissions and commands, otherwise FTBLib's config type id
 *                      ({@code int}, {@code string}, {@code enum}, ...)
 * @param playerDefault value for non-op players when no rank sets the node ("" = unknown)
 * @param opDefault     value for ops when no rank sets the node ("" = unknown)
 * @param description   free text, may be empty
 * @param variants      allowed values for enum-like types, else empty
 * @param min           inclusive lower bound for {@code int}, else {@link Integer#MIN_VALUE}
 * @param max           inclusive upper bound for {@code int}, else {@link Integer#MAX_VALUE}
 */
public record NodeInfo(String node, Kind kind, String type, String playerDefault, String opDefault, String description,
        List<String> variants, int min, int max) {

    public enum Kind {
        /** Registered through Forge's PermissionAPI. */
        PERMISSION,
        /** FTBLib rank config (typed value such as a home limit or chat format). */
        RANK_CONFIG,
        /** {@code command.<mod>.<name>} node created by FTB Utilities' command override. */
        COMMAND,
        /** Custom prefix registered by a mod; matches every node below it. */
        PREFIX;

        private static final Kind[] VALUES = values();

        public static Kind byOrdinal(int i) {
            return i >= 0 && i < VALUES.length ? VALUES[i] : PERMISSION;
        }
    }

    public boolean isBoolean() {
        return type.equals("bool") || type.equals("boolean");
    }

    /** Fallback value for a holder when nothing in the rank chain sets the node. */
    public String defaultFor(boolean op) {
        return op ? opDefault : playerDefault;
    }

    /** Mod id guessed from the node ({@code command.<mod>.x} or {@code <mod>.x}). */
    public String namespace() {
        String n = kind == Kind.COMMAND && node.startsWith("command.") ? node.substring(8) : node;
        int i = n.indexOf('.');
        return i == -1 ? n : n.substring(0, i);
    }
}
