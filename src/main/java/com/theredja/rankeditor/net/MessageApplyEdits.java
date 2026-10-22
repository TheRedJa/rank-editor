package com.theredja.rankeditor.net;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToServer;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.server.EditorService;

/** A batch of staged edits, applied atomically if the server state still matches {@code baseFingerprint}. */
public class MessageApplyEdits extends MessageToServer {

    private int protocol;
    private long baseFingerprint;
    private List<Edit> edits;

    public MessageApplyEdits() {
    }

    public MessageApplyEdits(long baseFingerprint, List<Edit> edits) {
        this.protocol = WireCodec.PROTOCOL;
        this.baseFingerprint = baseFingerprint;
        this.edits = edits;
    }

    @Override
    public NetworkWrapper getWrapper() {
        return RankEditorNet.NET;
    }

    @Override
    public void writeData(DataOut data) {
        data.writeVarInt(protocol);
        data.writeLong(baseFingerprint);
        WireCodec.writeEdits(data, edits);
    }

    @Override
    public void readData(DataIn data) {
        protocol = data.readVarInt();
        if (protocol == WireCodec.PROTOCOL) {
            baseFingerprint = data.readLong();
            edits = WireCodec.readEdits(data);
        }
    }

    @Override
    public void onMessage(EntityPlayerMP player) {
        if (protocol != WireCodec.PROTOCOL) {
            EditorService.rejectProtocol(player, protocol);
            return;
        }
        EditorService.apply(player, baseFingerprint, edits);
    }
}
