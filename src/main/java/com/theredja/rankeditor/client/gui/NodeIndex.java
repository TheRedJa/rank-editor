package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.NodeInfo;
import com.theredja.rankeditor.model.RankState;

/**
 * Every node the table can show: the server catalog plus any node used in the rank files, minus the special entries
 * (parent, power, default flags). Rows are cached; the sorted list is only rebuilt when the catalog or the set of
 * unknown nodes actually changes, so ordinary edits cost one pass over the entries.
 */
@SideOnly(Side.CLIENT)
final class NodeIndex {

    private static final Set<String> SPECIAL = Set.of(RankState.NODE_PARENT, RankState.NODE_POWER, RankState.NODE_DEFAULT_PLAYER,
            RankState.NODE_DEFAULT_OP);

    private final Map<String, PermRow> rows = new HashMap<>();
    private List<NodeInfo> catalog;
    private List<PermRow> catalogRows = List.of();
    private final Set<String> catalogNodes = new HashSet<>();
    private Set<String> unknown = Set.of();
    private List<PermRow> sorted = List.of();

    static boolean isSpecial(String node) {
        return SPECIAL.contains(node);
    }

    /** @param extra additional holder whose nodes must stay listed (the selected holder as saved on the server) */
    void update(List<NodeInfo> nodes, RankState state, Holder extra) {
        boolean changed = false;
        if (nodes != catalog) {
            catalog = nodes;
            rows.clear();
            catalogNodes.clear();
            List<PermRow> list = new ArrayList<>(nodes.size());
            for (NodeInfo info : nodes) {
                if (!isSpecial(info.node()) && !rows.containsKey(info.node())) {
                    PermRow row = PermRow.of(info);
                    rows.put(info.node(), row);
                    catalogNodes.add(info.node());
                    list.add(row);
                }
            }
            catalogRows = list;
            unknown = Set.of();
            changed = true;
        }
        Set<String> found = new TreeSet<>();
        collect(state.ranks.values(), found);
        collect(state.players.values(), found);
        if (extra != null) {
            collect(List.of(extra), found);
        }
        if (changed || !found.equals(unknown)) {
            unknown = found;
            List<PermRow> list = new ArrayList<>(catalogRows);
            for (String node : found) {
                list.add(rows.computeIfAbsent(node, PermRow::unknown));
            }
            list.sort(Comparator.comparing(row -> row.node));
            sorted = list;
        }
    }

    private void collect(Iterable<Holder> holders, Set<String> out) {
        for (Holder holder : holders) {
            for (String node : holder.entries.keySet()) {
                if (!isSpecial(node) && !catalogNodes.contains(node)) {
                    out.add(node);
                }
            }
        }
    }

    /** All rows ordered by node. */
    List<PermRow> all() {
        return sorted;
    }

    /** True when the table lists {@code node}. */
    boolean contains(String node) {
        return catalogNodes.contains(node) || unknown.contains(node);
    }

    /** Nodes (including prefixes and "*") that carry a value on {@code holder} or anything it inherits from. */
    static Set<String> chainNodes(RankState state, Holder holder) {
        Set<String> nodes = new HashSet<>();
        visit(state, holder, true, new HashSet<>(), nodes);
        return nodes;
    }

    private static void visit(RankState state, Holder holder, boolean top, Set<String> seen, Set<String> out) {
        if (!seen.add(holder.player ? "p:" + holder.id : "r:" + holder.id)) {
            return;
        }
        holder.entries.forEach((node, entry) -> {
            if (!entry.value().isEmpty()) {
                out.add(node);
            }
        });
        for (Holder parent : top ? state.actualParents(holder) : state.parents(holder)) {
            visit(state, parent, false, seen, out);
        }
    }

    /** True when {@code node}, one of its dotted prefixes, or "*" is in {@code chain}: the lookup order of Resolver. */
    static boolean inChain(Set<String> chain, String node) {
        String n = node;
        while (true) {
            if (chain.contains(n)) {
                return true;
            }
            int dot = n.lastIndexOf('.');
            if (dot != -1) {
                n = n.substring(0, dot);
            } else if (!n.equals("*")) {
                n = "*";
            } else {
                return false;
            }
        }
    }
}
