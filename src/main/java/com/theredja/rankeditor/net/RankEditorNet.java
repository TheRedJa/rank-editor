package com.theredja.rankeditor.net;

import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;

/** Message ids follow registration order, so client and server must register in exactly this order. */
public final class RankEditorNet {

    public static final NetworkWrapper NET = NetworkWrapper.newWrapper("rankeditor");

    private RankEditorNet() {
    }

    public static void init() {
        NET.register(new MessageRequestSnapshot());
        NET.register(new MessageSnapshot());
        NET.register(new MessageApplyEdits());
        NET.register(new MessageEditResult());
        NET.register(new MessageCloseEditor());
    }
}
