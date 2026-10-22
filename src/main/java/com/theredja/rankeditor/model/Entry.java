package com.theredja.rankeditor.model;

/**
 * One line of a rank section in ranks.txt / players.txt: {@code node: value}, optionally preceded by a {@code // comment}.
 */
public record Entry(String node, String value, String comment) {

    public Entry withValue(String newValue) {
        return new Entry(node, newValue, comment);
    }

    public Entry withComment(String newComment) {
        return new Entry(node, value, newComment);
    }
}
