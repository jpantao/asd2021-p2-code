package protocols.agreement.messages;


import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class PrepareMessage extends ProtoMessage {
    public final static short MSG_ID = 111;

    private final int ins;
    private final int n;
    private final byte[] v;

    public PrepareMessage(int ins, int n, byte[] v) {
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
        return "PrepareMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + Hex.encodeHexString(v) +
                '}';
    }

    public static ISerializer<PrepareMessage> serializer = new ISerializer<PrepareMessage>() {
        @Override
        public void serialize(PrepareMessage msg, ByteBuf byteBuf) throws IOException {
            if (msg.v != null) {
                byteBuf.writeBoolean(true);
                byteBuf.writeInt(msg.v.length);
                byteBuf.writeBytes(msg.v);
            } else {
                byteBuf.writeBoolean(false);
            }

            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
        }

        @Override
        public PrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
            byte[] v = null;
            if(byteBuf.readBoolean()){
                v = new byte[byteBuf.readInt()];
                byteBuf.readBytes(v);
            }

            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            return new PrepareMessage(ins, n, v);
        }
    };

}
