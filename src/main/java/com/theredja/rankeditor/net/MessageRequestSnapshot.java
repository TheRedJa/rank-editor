package com.theredja.rankeditor.net;

import net.minecraft.entity.player.EntityPlayerMP;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToServer;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.theredja.rankeditor.server.EditorService;

/** Client asks for a fresh snapshot (editor refresh button). */
public class MessageRequestSnapshot extends MessageToServer {

    private int protocol;

    public MessageRequestSnapshot() {
        this.protocol = WireCodec.PROTOCOL;
    }

    @Override
    public NetworkWrapper getWrapper() {
        return RankEditorNet.NET;
    }

    @Override
    public void writeData(DataOut data) {
        data.writeVarInt(protocol);
    }

    @Override
    public void readData(DataIn data) {
        protocol = data.readVarInt();
    }

    @Override
    public void onMessage(EntityPlayerMP player) {
        if (protocol != WireCodec.PROTOCOL) {
            EditorService.rejectProtocol(player, protocol);
            return;
        }
        EditorService.open(player, false);
    }
}
