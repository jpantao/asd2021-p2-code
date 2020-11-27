package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class AcceptMessage extends ProtoMessage {
    public final static short MSG_ID = 113;

    private final int ins;
    private final int n;
    private final int v;

    public AcceptMessage(int ins, int n, int v) {
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
        return "AcceptMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + v +
                '}';
    }

    public static ISerializer<AcceptMessage> serializer = new ISerializer<AcceptMessage>() {
        @Override
        public void serialize(AcceptMessage acceptMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(acceptMessage.ins);
            byteBuf.writeInt(acceptMessage.n);
            byteBuf.writeInt(acceptMessage.v);
        }

        @Override
        public AcceptMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int v = byteBuf.readInt();
            return new AcceptMessage(ins, n, v);
        }
    };
}
