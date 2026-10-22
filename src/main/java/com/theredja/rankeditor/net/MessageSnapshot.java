package com.theredja.rankeditor.net;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToClient;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.theredja.rankeditor.client.ClientEditorSession;
import com.theredja.rankeditor.model.Snapshot;

/** Full editor state. {@code open} = open the editor GUI; otherwise only update it if it is already open. */
public class MessageSnapshot extends MessageToClient {

    private int protocol;
    private boolean open;
    private Snapshot snapshot;

    public MessageSnapshot() {
    }

    public MessageSnapshot(Snapshot snapshot, boolean open) {
        this.protocol = WireCodec.PROTOCOL;
        this.snapshot = snapshot;
        this.open = open;
    }

    @Override
    public NetworkWrapper getWrapper() {
        return RankEditorNet.NET;
    }

    @Override
    public void writeData(DataOut data) {
        data.writeVarInt(protocol);
        data.writeBoolean(open);
        WireCodec.writeSnapshot(data, snapshot);
    }

    @Override
    public void readData(DataIn data) {
        protocol = data.readVarInt();
        if (protocol == WireCodec.PROTOCOL) {
            open = data.readBoolean();
            snapshot = WireCodec.readSnapshot(data);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onMessage() {
        if (protocol != WireCodec.PROTOCOL) {
            ClientEditorSession.protocolMismatch(protocol);
            return;
        }
        ClientEditorSession.onSnapshot(snapshot, open);
    }
}
