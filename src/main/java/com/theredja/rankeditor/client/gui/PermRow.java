package com.theredja.rankeditor.client.gui;

import java.util.Locale;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.theredja.rankeditor.model.NodeInfo;

/**
 * One permission node in the table. Immutable and shared between refreshes; it carries only the node, its catalog
 * entry (null for nodes seen in the rank files but unknown to the server) and a pre-lowercased search key.
 */
@SideOnly(Side.CLIENT)
final class PermRow {

    final String node;
    final NodeInfo info;
    /** True for the synthetic "+ Add 'text'" row. */
    final boolean adder;
    private final String key;

    private PermRow(String node, NodeInfo info, boolean adder) {
        this.node = node;
        this.info = info;
        this.adder = adder;
        this.key = (node + '\n' + (info == null ? "" : info.description())).toLowerCase(Locale.ROOT);
    }

    static PermRow of(NodeInfo info) {
        return new PermRow(info.node(), info, false);
    }

    static PermRow unknown(String node) {
        return new PermRow(node, null, false);
    }

    static PermRow adder(String node) {
        return new PermRow(node, null, true);
    }

    /** True when every lowercase term occurs in the node or its description. */
    boolean matches(String[] terms) {
        for (String term : terms) {
            if (!key.contains(term)) {
                return false;
            }
        }
        return true;
    }

    String typeLabel() {
        return info == null ? "?" : info.type();
    }
}
