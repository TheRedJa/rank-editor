package com.theredja.rankeditor.server;

import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

public final class RankEditorPermissions {

    /** Open the editor read-only. */
    public static final String VIEW = "rankeditor.view";
    /**
     * Apply changes. Anyone holding this can grant themselves any permission, so treat it like OP.
     */
    public static final String EDIT = "rankeditor.edit";

    private RankEditorPermissions() {
    }

    public static void register() {
        PermissionAPI.registerNode(VIEW, DefaultPermissionLevel.OP, "Open the Rank Editor (read-only without rankeditor.edit)");
        PermissionAPI.registerNode(EDIT, DefaultPermissionLevel.OP, "Change ranks and permissions in the Rank Editor; effectively grants every permission");
    }
}
