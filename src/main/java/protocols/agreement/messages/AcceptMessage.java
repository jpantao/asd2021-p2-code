package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AcceptMessage extends ProtoMessage {
    public final static short MSG_ID = 113;

    private final int ins;
    private final int n;
    private final byte[] v;

    public AcceptMessage(int ins, int n, byte[] v) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.v = v;
    }

    public int getIns() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public byte[] getV() {
        return v;
    }

    @Override
    public String toString() {
        return "AcceptMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + Hex.encodeHexString(v) +
                '}';
    }

    public static ISerializer<AcceptMessage> serializer = new ISerializer<AcceptMessage>() {
        @Override
        public void serialize(AcceptMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.v.length);
            byteBuf.writeBytes(msg.v);
        }

        @Override
        public AcceptMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            byte[] v = new byte[byteBuf.readInt()];
            byteBuf.readBytes(v);
            return new AcceptMessage(ins, n, v);
        }
    };
}
