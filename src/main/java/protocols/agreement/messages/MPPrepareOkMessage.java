package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class MPPrepareOkMessage extends ProtoMessage {
    public final static short MSG_ID = 112;

    private final int ins;

    private final int n;

    private final int na;
    private final byte[] va;
    private final List<byte[]> futureValues;


    public MPPrepareOkMessage(int ins, int n, int na, byte[] v, List<byte[]> futureValues) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.na = na;
        this.va = v;
        this.futureValues = futureValues;
    }

    public int getInstance() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public int getNa() {
        return na;
    }

    public byte[] getVa() {
        return va;
    }

    public List<byte[]> getFutureValues() {
        return futureValues;
    }

    @Override
    public String toString() {
        return "PrepareOkMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", na=" + na +
                ", va=" + Arrays.hashCode(va) +
                ", futureValues=" + futureValues.hashCode() +
                '}';
    }

    public static ISerializer<MPPrepareOkMessage> serializer = new ISerializer<MPPrepareOkMessage>() {
        @Override
        public void serialize(MPPrepareOkMessage msg, ByteBuf byteBuf) throws IOException {
            if (msg.va != null) {
                byteBuf.writeBoolean(true);
                byteBuf.writeInt(msg.va.length);
                byteBuf.writeBytes(msg.va);
            } else {
                byteBuf.writeBoolean(false);
            }
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.na);
            byteBuf.writeInt(msg.futureValues.size());
            if (msg.futureValues.size() > 0) {
                for (byte[] v : msg.futureValues) {
                    byteBuf.writeInt(v.length);
                    byteBuf.writeBytes(v);
                }
            }
        }

        @Override
        public MPPrepareOkMessage deserialize(ByteBuf byteBuf) throws IOException {

            byte[] v = null;
            if (byteBuf.readBoolean()) {
                v = new byte[byteBuf.readInt()];
                byteBuf.readBytes(v);
            }

            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int na = byteBuf.readInt();
            List<byte[]> future_values = new LinkedList<>();
            int future_values_size = byteBuf.readInt();
            for (int i = 0; i < future_values_size; i++) {
                byte[] value = new byte[byteBuf.readInt()];
                byteBuf.readBytes(value);
                future_values.add(value);
            }
            return new MPPrepareOkMessage(ins, n, na, v, future_values);
        }
    };
}
