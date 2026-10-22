package com.theredja.rankeditor.net;

import net.minecraft.entity.player.EntityPlayerMP;

import com.feed_the_beast.ftblib.lib.net.MessageToServer;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.theredja.rankeditor.server.EditorService;

/** Client closed the editor; stop pushing snapshots to it. */
public class MessageCloseEditor extends MessageToServer {

    @Override
    public NetworkWrapper getWrapper() {
        return RankEditorNet.NET;
    }

    @Override
    public void onMessage(EntityPlayerMP player) {
        EditorService.close(player);
    }
}
