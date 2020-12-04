package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AcceptOkMessage extends ProtoMessage {
    public final static short MSG_ID = 114;

    private final int ins;
    private final int n;
    private final byte[] v;

    public AcceptOkMessage(int ins, int n, byte[] v) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.v = v;
    }

    public int getInstance() {
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
        return "AcceptedMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + (v != null ? Hex.encodeHexString(v) : null) +
                '}';
    }

    public static ISerializer<AcceptOkMessage> serializer = new ISerializer<AcceptOkMessage>() {
        @Override
        public void serialize(AcceptOkMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.v.length);
            byteBuf.writeBytes(msg.v);
        }

        @Override
        public AcceptOkMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            byte[] v = new byte[byteBuf.readInt()];
            byteBuf.readBytes(v);
            return new AcceptOkMessage(ins, n, v);
        }
    };
}
