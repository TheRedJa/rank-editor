package com.theredja.rankeditor.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.theredja.rankeditor.model.Edit;
import com.theredja.rankeditor.model.EditApplier;
import com.theredja.rankeditor.model.Entry;
import com.theredja.rankeditor.model.Holder;
import com.theredja.rankeditor.model.NodeInfo;
import com.theredja.rankeditor.model.PlayerInfo;
import com.theredja.rankeditor.model.RankState;
import com.theredja.rankeditor.model.Snapshot;

/**
 * Serialization of the model. Every read is bounded: FTBLib's own {@code readString}/{@code readCollection} allocate
 * whatever length the peer claims, which a malicious client could abuse to exhaust server memory.
 */
public final class WireCodec {

    /** Bumped whenever the wire format changes; both sides refuse mismatched peers. */
    public static final int PROTOCOL = 1;

    private static final int MAX_STRING_BYTES = 4 * EditApplier.MAX_VALUE_LENGTH;
    private static final int MAX_LIST = 1 << 16;

    private WireCodec() {
    }

    public static final class WireException extends RuntimeException {

        public WireException(String message) {
            super(message);
        }
    }

    // --- primitives ---

    public static void writeString(DataOut out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeVarInt(b.length);
        out.writeBytes(b);
    }

    public static String readString(DataIn in) {
        int len = in.readVarInt();
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new WireException("String length " + len + " out of range");
        }
        if (len == 0) {
            return "";
        }
        byte[] b = new byte[len];
        in.readBytes(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    public static int readCount(DataIn in, int max) {
        int n = in.readVarInt();
        if (n < 0 || n > max) {
            throw new WireException("Count " + n + " out of range (max " + max + ")");
        }
        return n;
    }

    // --- edits (client -> server) ---

    public static void writeEdits(DataOut out, List<Edit> edits) {
        out.writeVarInt(edits.size());
        for (Edit e : edits) {
            out.writeByte(e.op().ordinal());
            out.writeBoolean(e.player());
            writeString(out, e.target());
            writeString(out, e.a());
            writeString(out, e.b());
            out.writeInt(e.power());
        }
    }

    public static List<Edit> readEdits(DataIn in) {
        int n = readCount(in, EditApplier.MAX_EDITS);
        List<Edit> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Edit.Op op = Edit.Op.byOrdinal(in.readUnsignedByte());
            if (op == null) {
                throw new WireException("Unknown edit op");
            }
            boolean player = in.readBoolean();
            String target = readString(in);
            String a = readString(in);
            String b = readString(in);
            int power = in.readInt();
            list.add(new Edit(op, player, target, a, b, power));
        }
        return list;
    }

    // --- snapshot (server -> client) ---

    public static void writeSnapshot(DataOut out, Snapshot s) {
        out.writeBoolean(s.canEdit());
        out.writeBoolean(s.ranksActive());
        out.writeLong(s.fingerprint());

        RankState st = s.state();
        out.writeVarInt(st.ranks.size());
        for (Holder h : st.ranks.values()) {
            writeHolder(out, h);
        }
        out.writeVarInt(st.players.size());
        for (Holder h : st.players.values()) {
            writeHolder(out, h);
        }
        out.writeVarInt(st.playerInfo.size());
        for (PlayerInfo p : st.playerInfo.values()) {
            out.writeLong(p.uuid().getMostSignificantBits());
            out.writeLong(p.uuid().getLeastSignificantBits());
            writeString(out, p.name());
            out.writeBoolean(p.online());
            out.writeBoolean(p.op());
        }

        out.writeVarInt(s.nodes().size());
        for (NodeInfo n : s.nodes()) {
            writeString(out, n.node());
            out.writeByte(n.kind().ordinal());
            writeString(out, n.type());
            writeString(out, n.playerDefault());
            writeString(out, n.opDefault());
            writeString(out, n.description());
            out.writeVarInt(n.variants().size());
            for (String v : n.variants()) {
                writeString(out, v);
            }
            out.writeInt(n.min());
            out.writeInt(n.max());
        }
    }

    public static Snapshot readSnapshot(DataIn in) {
        boolean canEdit = in.readBoolean();
        boolean active = in.readBoolean();
        long fingerprint = in.readLong();

        RankState st = new RankState();
        int n = readCount(in, MAX_LIST);
        for (int i = 0; i < n; i++) {
            Holder h = readHolder(in, false);
            st.ranks.put(h.id, h);
        }
        n = readCount(in, MAX_LIST);
        for (int i = 0; i < n; i++) {
            Holder h = readHolder(in, true);
            st.players.put(h.id, h);
        }
        n = readCount(in, MAX_LIST);
        for (int i = 0; i < n; i++) {
            UUID id = new UUID(in.readLong(), in.readLong());
            st.playerInfo.put(id, new PlayerInfo(id, readString(in), in.readBoolean(), in.readBoolean()));
        }

        n = readCount(in, MAX_LIST);
        List<NodeInfo> nodes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String node = readString(in);
            NodeInfo.Kind kind = NodeInfo.Kind.byOrdinal(in.readUnsignedByte());
            String type = readString(in);
            String pd = readString(in);
            String od = readString(in);
            String desc = readString(in);
            int vn = readCount(in, MAX_LIST);
            List<String> variants = new ArrayList<>(vn);
            for (int j = 0; j < vn; j++) {
                variants.add(readString(in));
            }
            nodes.add(new NodeInfo(node, kind, type, pd, od, desc, List.copyOf(variants), in.readInt(), in.readInt()));
        }
        return new Snapshot(canEdit, active, fingerprint, st, List.copyOf(nodes));
    }

    private static void writeHolder(DataOut out, Holder h) {
        writeString(out, h.id);
        writeString(out, h.comment);
        out.writeVarInt(h.entries.size());
        for (Entry e : h.entries.values()) {
            writeString(out, e.node());
            writeString(out, e.value());
            writeString(out, e.comment());
        }
    }

    private static Holder readHolder(DataIn in, boolean player) {
        Holder h = new Holder(readString(in), player, readString(in));
        int n = readCount(in, MAX_LIST);
        for (int i = 0; i < n; i++) {
            Entry e = new Entry(readString(in), readString(in), readString(in));
            h.entries.put(e.node(), e);
        }
        return h;
    }
}
