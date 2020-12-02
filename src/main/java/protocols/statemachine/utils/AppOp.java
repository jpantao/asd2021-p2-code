package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.Arrays;
import java.util.UUID;

public class AppOp extends Op{

    UUID opId;
    byte[] op;

    public AppOp(UUID opId, byte[] op) {
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

    public static Serializer<AppOp> serializer = new Serializer<AppOp>() {
        @Override
        public void serialize(AppOp operation, ByteBuf buf) {
            buf.writeLong(operation.opId.getMostSignificantBits());
            buf.writeLong(operation.opId.getLeastSignificantBits());
            buf.writeInt(operation.op.length);
            buf.writeBytes(operation.op);
        }

        @Override
        public AppOp deserialize(ByteBuf buf) {
            return null;
        }
    };


}
