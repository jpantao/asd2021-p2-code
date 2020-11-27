package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class AcceptMessage extends ProtoMessage {
    public final static short MSG_ID = 113;

    private final int ins;
    private final int n;
    private final UUID opId;
    private final byte[] op;

    public AcceptMessage(int ins, int n, UUID opId, byte[] op) {
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
        return "AcceptMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", opId=" + opId +
                ", op=" + Arrays.toString(op) +
                '}';
    }

    public static ISerializer<AcceptMessage> serializer = new ISerializer<AcceptMessage>() {
        @Override
        public void serialize(AcceptMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeLong(msg.opId.getMostSignificantBits());
            byteBuf.writeLong(msg.opId.getLeastSignificantBits());
            byteBuf.writeInt(msg.op.length);
            byteBuf.writeBytes(msg.op);
        }

        @Override
        public AcceptMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            long highBytes = byteBuf.readLong();
            long lowBytes = byteBuf.readLong();
            UUID opId = new UUID(highBytes, lowBytes);
            byte[] op = new byte[byteBuf.readInt()];
            byteBuf.readBytes(op);
            return new AcceptMessage(ins, n, opId, op);
        }
    };
}
