package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AcceptedMessage extends ProtoMessage {
    public final static short MSG_ID = 114;

    private final int ins;
    private final int n;
    private final int v;

    public AcceptedMessage(int ins, int n, int v) {
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

    public int getV() {
        return v;
    }

    @Override
    public String toString() {
        return "AcceptedMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + v +
                '}';
    }

    public static ISerializer<AcceptedMessage> serializer = new ISerializer<AcceptedMessage>() {
        @Override
        public void serialize(AcceptedMessage acceptedMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(acceptedMessage.ins);
            byteBuf.writeInt(acceptedMessage.n);
            byteBuf.writeInt(acceptedMessage.v);
        }

        @Override
        public AcceptedMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int v = byteBuf.readInt();
            return new AcceptedMessage(ins, n, v);
        }
    };
}
