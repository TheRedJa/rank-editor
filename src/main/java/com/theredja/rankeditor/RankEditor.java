package com.theredja.rankeditor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import com.theredja.rankeditor.net.RankEditorNet;
import com.theredja.rankeditor.server.CommandRankEditor;
import com.theredja.rankeditor.server.RankEditorPermissions;

@Mod(
        modid = RankEditor.MOD_ID,
        name = "@{MOD_NAME}",
        version = "@{MOD_VERSION}",
        dependencies = "required-after:ftblib;required-after:ftbutilities")
public class RankEditor {

    public static final String MOD_ID = "@{MOD_ID}";
    public static final Logger LOGGER = LogManager.getLogger("@{MOD_NAME}");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RankEditorNet.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RankEditorPermissions.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandRankEditor());
    }
}
