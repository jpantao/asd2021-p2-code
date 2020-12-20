package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;

public class MPAcceptMessage extends ProtoMessage {

    public final static short MSG_ID = 115;

    private final int ins;
    private final int n;
    private final int lastn;
    private final byte[] lastv;
    private final byte[] v;


    public MPAcceptMessage(int ins, int n, int lastn, byte[] lastv, byte[] v) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.lastn = lastn;
        this.lastv = lastv;
        this.v = v;
    }

    public int getInstance() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public byte[] getLastV() {
        return lastv;
    }

    public int getLastN() {
        return lastn;
    }

    public byte[] getV() {
        return v;
    }

    @Override
    public String toString() {
        return "MPAcceptMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", lastn=" + lastn +
                ", lastv=" + Arrays.toString(lastv) +
                ", v=" + Arrays.toString(v) +
                '}';
    }

    public static ISerializer<MPAcceptMessage> serializer = new ISerializer<MPAcceptMessage>() {
        @Override
        public void serialize(MPAcceptMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.lastn);
            byteBuf.writeInt(msg.lastv.length);
            byteBuf.writeInt(msg.v.length);
            byteBuf.writeBytes(msg.lastv);
            byteBuf.writeBytes(msg.v);
        }

        @Override
        public MPAcceptMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int ln = byteBuf.readInt();
            byte[] lv = new byte[byteBuf.readInt()];
            byte[] v = new byte[byteBuf.readInt()];
            byteBuf.readBytes(lv);
            byteBuf.readBytes(v);
            return new MPAcceptMessage(ins, n, ln, lv, v);
        }
    };
}
