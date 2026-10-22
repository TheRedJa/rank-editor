package com.theredja.rankeditor.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.server.permission.DefaultPermissionHandler;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import com.feed_the_beast.ftblib.lib.config.ConfigInt;
import com.feed_the_beast.ftblib.lib.config.ConfigValue;
import com.feed_the_beast.ftblib.lib.config.RankConfigAPI;
import com.feed_the_beast.ftblib.lib.config.RankConfigValueInfo;
import com.feed_the_beast.ftbutilities.FTBUtilitiesCommon;
import com.feed_the_beast.ftbutilities.data.NodeEntry;
import com.feed_the_beast.ftbutilities.ranks.CommandOverride;
import com.feed_the_beast.ftbutilities.ranks.Ranks;
import com.theredja.rankeditor.model.NodeInfo;

/**
 * Collects every permission node the server knows, with defaults and descriptions. Mirrors the sources FTB
 * Utilities' own {@code Ranks.getPermissionNodes()} and its Aurora permission list use.
 */
public final class NodeCatalog {

    private static final int MAX_DESCRIPTION = 1000;

    private NodeCatalog() {
    }

    public static List<NodeInfo> build(Ranks ranks) {
        Map<String, NodeInfo> out = new LinkedHashMap<>();

        for (String node : PermissionAPI.getPermissionHandler().getRegisteredNodes()) {
            DefaultPermissionLevel level = DefaultPermissionHandler.INSTANCE.getDefaultPermissionLevel(node);
            String desc = PermissionAPI.getPermissionHandler().getNodeDescription(node);
            if (hiddenByPrefix(node, level, desc)) {
                continue;
            }
            out.put(node, new NodeInfo(node, NodeInfo.Kind.PERMISSION, "bool", Boolean.toString(level == DefaultPermissionLevel.ALL),
                    Boolean.toString(level != DefaultPermissionLevel.NONE), clip(desc), List.of(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        }

        for (NodeEntry e : FTBUtilitiesCommon.CUSTOM_PERM_PREFIX_REGISTRY) {
            out.putIfAbsent(e.node, new NodeInfo(e.node, NodeInfo.Kind.PREFIX, e.player.getId(), e.player.getString(), e.op.getString(),
                    clip(e.desc), List.of(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        }

        int opLevel = ranks.universe.server.getOpPermissionLevel();
        for (Map.Entry<String, CommandOverride> e : ranks.commands.entrySet()) {
            int level = e.getValue().getRequiredPermissionLevel();
            String mod = e.getValue().modContainer == null ? "" : e.getValue().modContainer.getName();
            String usage = e.getValue().usage.getUnformattedText();
            String desc = mod.isEmpty() ? usage : "[" + mod + "] " + usage;
            out.putIfAbsent(e.getKey(), new NodeInfo(e.getKey(), NodeInfo.Kind.COMMAND, "bool", Boolean.toString(level <= 0),
                    Boolean.toString(level <= opLevel), clip(desc), List.of(), Integer.MIN_VALUE, Integer.MAX_VALUE));
        }

        for (RankConfigValueInfo info : RankConfigAPI.getHandler().getRegisteredConfigs()) {
            ConfigValue def = info.defaultValue;
            String key = "permission." + info.node;
            String desc = new TextComponentTranslation(key).getUnformattedText();
            int min = Integer.MIN_VALUE;
            int max = Integer.MAX_VALUE;
            if (def instanceof ConfigInt ci) {
                min = ci.getMin();
                max = ci.getMax();
            }
            out.put(info.node, new NodeInfo(info.node, NodeInfo.Kind.RANK_CONFIG, def.getId(), def.getString(), info.defaultOPValue.getString(),
                    clip(desc.equals(key) ? "" : desc), List.copyOf(def.getVariants()), min, max));
        }

        List<NodeInfo> list = new ArrayList<>(out.values());
        list.sort((a, b) -> a.node().compareTo(b.node()));
        return list;
    }

    /** Same filter as {@code Ranks.getPermissionNodes()}: per-block/item nodes covered by a prefix with the same default. */
    private static boolean hiddenByPrefix(String node, DefaultPermissionLevel level, String desc) {
        for (NodeEntry e : FTBUtilitiesCommon.CUSTOM_PERM_PREFIX_REGISTRY) {
            if (node.startsWith(e.getNode())) {
                return e.level != null && level == e.level && desc.isEmpty();
            }
        }
        return false;
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        s = s.replaceAll("\\p{Cntrl}", " ");
        return s.length() > MAX_DESCRIPTION ? s.substring(0, MAX_DESCRIPTION) : s;
    }
}
