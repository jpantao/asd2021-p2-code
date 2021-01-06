package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class MPPrepareOkMessage extends ProtoMessage {
    public final static short MSG_ID = 112;

    private final int ins;
    private final int n;
    private final List<PrepareOkMessage> futurePrepareOks;


    public MPPrepareOkMessage(int ins, int n, List<PrepareOkMessage> futurePrepareOks) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.futurePrepareOks = futurePrepareOks;
    }

    public int getInstance() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public List<PrepareOkMessage> getFuturePrepareOks() {
        return futurePrepareOks;
    }

    @Override
    public String toString() {
        return "PrepareOkMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", futurePrepareOks=" + futurePrepareOks.hashCode() +
                '}';
    }

    public static ISerializer<MPPrepareOkMessage> serializer = new ISerializer<MPPrepareOkMessage>() {
        @Override
        public void serialize(MPPrepareOkMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.futurePrepareOks.size());
            for (PrepareOkMessage m : msg.futurePrepareOks)
                PrepareOkMessage.serializer.serialize(m, byteBuf);
        }

        @Override
        public MPPrepareOkMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int pLength = byteBuf.readInt();
            List<PrepareOkMessage> fPrepOks = new LinkedList<>();
            for (int i = 0; i < pLength; i++)
                fPrepOks.add(PrepareOkMessage.serializer.deserialize(byteBuf));
            return new MPPrepareOkMessage(ins, n, fPrepOks);
        }
    };
}
