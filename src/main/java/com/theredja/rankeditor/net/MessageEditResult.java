package com.theredja.rankeditor.net;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToClient;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.theredja.rankeditor.client.ClientEditorSession;

/** Outcome of an apply / open request, shown in the editor's status line. */
public class MessageEditResult extends MessageToClient {

    private boolean success;
    private String message;

    public MessageEditResult() {
    }

    public MessageEditResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    @Override
    public NetworkWrapper getWrapper() {
        return RankEditorNet.NET;
    }

    @Override
    public void writeData(DataOut data) {
        data.writeBoolean(success);
        WireCodec.writeString(data, message);
    }

    @Override
    public void readData(DataIn data) {
        success = data.readBoolean();
        message = WireCodec.readString(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onMessage() {
        ClientEditorSession.onResult(success, message);
    }
}
