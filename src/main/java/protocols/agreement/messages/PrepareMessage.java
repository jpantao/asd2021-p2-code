package protocols.agreement.messages;


import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class PrepareMessage extends ProtoMessage {
    public final static short MSG_ID = 111;

    private final int ins;
    private final int n;

    public PrepareMessage(int ins, int n) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
    }

    public int getIns() {
        return ins;
    }

    public int getN() {
        return n;
    }

    @Override
    public String toString() {
        return "PrepareMessage{" +
                "ins=" + ins +
                ", n=" + n +
                '}';
    }

    public static ISerializer<PrepareMessage> serializer = new ISerializer<PrepareMessage>() {
        @Override
        public void serialize(PrepareMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
        }

        @Override
        public PrepareMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            return new PrepareMessage(ins, n);
        }
    };
}
