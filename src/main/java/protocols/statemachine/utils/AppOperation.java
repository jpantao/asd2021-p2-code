package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class AppOperation extends Operation {

    private final UUID opId;
    private final byte[] op;

    public AppOperation(UUID opId, byte[] op) {
        super(Type.APP_OP);
        this.opId = opId;
        this.op = op;
    }

    public UUID getOpId() {
        return opId;
    }

    public byte[] getOp() {
        return op;
    }

    @Override
    public String toString() {
        return "AppOp{" +
                "opId=" + opId +
                ", op=" + Arrays.toString(op) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppOperation)) return false;
        AppOperation that = (AppOperation) o;
        return opId.equals(that.opId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opId);
    }

    public static Serializer<Operation> serializer = new Serializer<Operation>() {
        @Override
        public void serialize(Operation operation, ByteBuf buf) {
            buf.writeLong(((AppOperation) operation).opId.getMostSignificantBits());
            buf.writeLong(((AppOperation) operation).opId.getLeastSignificantBits());
            buf.writeInt(((AppOperation) operation).op.length);
            buf.writeBytes(((AppOperation) operation).op);
        }

        @Override
        public AppOperation deserialize(ByteBuf buf) {
            UUID opId = new UUID(buf.readLong(), buf.readLong());
            byte[] op = new byte[buf.readInt()];
            buf.readBytes(op);
            return new AppOperation(opId, op);
        }
    };
}
