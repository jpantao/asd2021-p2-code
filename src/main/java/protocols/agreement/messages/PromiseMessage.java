package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Objects;

public class PromiseMessage extends ProtoMessage {
    public final static short MSG_ID = 112;

    private final int ins;
    private final int n;
    private final Integer v;

    public PromiseMessage(int ins, int n, Integer v) {
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

    public Integer getV() {
        return v;
    }

    @Override
    public String toString() {
        return "PromiseMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", v=" + v +
                '}';
    }

    public static ISerializer<PromiseMessage> serializer = new ISerializer<PromiseMessage>() {
        @Override
        public void serialize(PromiseMessage promiseMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(Objects.requireNonNullElse(promiseMessage.v, -1));
            byteBuf.writeInt(promiseMessage.ins);
            byteBuf.writeInt(promiseMessage.n);
        }

        @Override
        public PromiseMessage deserialize(ByteBuf byteBuf) throws IOException {
            Integer v = byteBuf.readInt();
            if (v == -1) v = null;
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            return new PromiseMessage(ins, n, v);
        }
    };
}
