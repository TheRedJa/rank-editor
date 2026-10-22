package com.theredja.rankeditor.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import com.feed_the_beast.ftblib.events.FTBLibPreInitRegistryEvent;
import com.feed_the_beast.ftblib.lib.data.Action;
import com.feed_the_beast.ftblib.lib.data.AdminPanelAction;
import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.gui.GuiIcons;
import com.theredja.rankeditor.RankEditor;

@Mod.EventBusSubscriber(modid = RankEditor.MOD_ID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** Adds "Rank Editor" to FTBLib's Admin Panel. */
    @SubscribeEvent
    public static void registerFtbLib(FTBLibPreInitRegistryEvent event) {
        event.getRegistry().registerAdminPanelAction(new AdminPanelAction(RankEditor.MOD_ID, "open", GuiIcons.LOCK, 0) {
            @Override
            public Action.Type getType(ForgePlayer player, NBTTagCompound data) {
                return player.isOnline() && EditorService.canView(player.getPlayer()) ? Action.Type.ENABLED : Action.Type.INVISIBLE;
            }

            @Override
            public void onAction(ForgePlayer player, NBTTagCompound data) {
                EditorService.open(player.getPlayer(), true);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EditorService.onLogout(event.player.getUniqueID());
    }
}
