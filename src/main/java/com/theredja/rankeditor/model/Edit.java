package com.theredja.rankeditor.model;

/**
 * One staged change. All strings are non-null.
 *
 * <ul>
 *   <li>{@link Op#SET}: on holder ({@code player}, {@code target}) set node {@code a} to value {@code b}; an empty
 *       value removes the entry.</li>
 *   <li>{@link Op#SET_COMMENT}: comment of entry {@code a} (or of the section when {@code a} is empty) = {@code b}.</li>
 *   <li>{@link Op#CREATE_RANK}: new rank {@code target} with power {@code power}, entries copied from rank {@code a}
 *       when non-empty (default flags are not copied).</li>
 *   <li>{@link Op#DELETE_RANK}: delete rank {@code target} and remove it from all parent lists.</li>
 *   <li>{@link Op#RENAME_RANK}: rename rank {@code target} to {@code a}, keeping its position and updating all parent
 *       lists.</li>
 * </ul>
 */
public record Edit(Op op, boolean player, String target, String a, String b, int power) {

    public enum Op {
        SET, SET_COMMENT, CREATE_RANK, DELETE_RANK, RENAME_RANK;

        private static final Op[] VALUES = values();

        public static Op byOrdinal(int i) {
            return i >= 0 && i < VALUES.length ? VALUES[i] : null;
        }
    }

    public static Edit set(boolean player, String target, String node, String value) {
        return new Edit(Op.SET, player, target, node, value, 0);
    }

    public static Edit comment(boolean player, String target, String node, String comment) {
        return new Edit(Op.SET_COMMENT, player, target, node, comment, 0);
    }

    public static Edit createRank(String id, String copyFrom, int power) {
        return new Edit(Op.CREATE_RANK, false, id, copyFrom, "", power);
    }

    public static Edit deleteRank(String id) {
        return new Edit(Op.DELETE_RANK, false, id, "", "", 0);
    }

    public static Edit renameRank(String id, String newId) {
        return new Edit(Op.RENAME_RANK, false, id, newId, "", 0);
    }
}
