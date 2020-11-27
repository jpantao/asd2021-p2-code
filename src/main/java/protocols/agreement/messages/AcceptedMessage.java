package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class AcceptedMessage extends ProtoMessage {
    public final static short MSG_ID = 114;

    private final int ins;
    private final int n;
    private final UUID opId;
    private final byte[] op;

    public AcceptedMessage(int ins, int n, UUID opId, byte[] op) {
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
        return "AcceptedMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", opId=" + opId +
                ", op=" + Arrays.toString(op) +
                '}';
    }

    public static ISerializer<AcceptedMessage> serializer = new ISerializer<AcceptedMessage>() {
        @Override
        public void serialize(AcceptedMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeLong(msg.opId.getMostSignificantBits());
            byteBuf.writeLong(msg.opId.getLeastSignificantBits());
            byteBuf.writeInt(msg.op.length);
            byteBuf.writeBytes(msg.op);
        }

        @Override
        public AcceptedMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            long highBytes = byteBuf.readLong();
            long lowBytes = byteBuf.readLong();
            UUID opId = new UUID(highBytes, lowBytes);
            byte[] op = new byte[byteBuf.readInt()];
            byteBuf.readBytes(op);
            return new AcceptedMessage(ins, n, opId, op);
        }
    };
}
