package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.Pair;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class PromiseMessage extends ProtoMessage {
    public final static short MSG_ID = 112;

    private final int ins;
    private final int n;
    private final UUID opId;
    private final byte[] op;

    public PromiseMessage(int ins, int n, UUID opId, byte[] op) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.opId = opId;
        this.op = op;
    }

    public int getIns() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public UUID getOpId() {
        return opId;
    }

    public byte[] getOp() {
        return op;
    }

    @Override
    public String toString() {
        return "PromiseMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", opId=" + opId +
                ", op=" + Arrays.toString(op) +
                '}';
    }

    public static ISerializer<PromiseMessage> serializer = new ISerializer<PromiseMessage>() {
        @Override
        public void serialize(PromiseMessage msg, ByteBuf byteBuf) throws IOException {
            if (msg.opId != null) {
                byteBuf.writeBoolean(true);
                byteBuf.writeLong(msg.opId.getMostSignificantBits());
                byteBuf.writeLong(msg.opId.getLeastSignificantBits());
                byteBuf.writeInt(msg.op.length);
                byteBuf.writeBytes(msg.op);
            } else {
                byteBuf.writeBoolean(false);
            }

            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
        }

        @Override
        public PromiseMessage deserialize(ByteBuf byteBuf) throws IOException {
            UUID opId = null;
            byte[] op = null;

            if(byteBuf.readBoolean()){
                long highBytes = byteBuf.readLong();
                long lowBytes = byteBuf.readLong();
                opId = new UUID(highBytes, lowBytes);
                op = new byte[byteBuf.readInt()];
                byteBuf.readBytes(op);
            }

            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            return new PromiseMessage(ins, n, opId, op);
        }
    };
}
