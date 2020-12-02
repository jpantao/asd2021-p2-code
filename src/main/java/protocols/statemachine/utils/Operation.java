package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;

public abstract class Operation {

    public enum Type {
        NOP(0, Nop.serializer),
        ADD_REP(1, AddReplica.serializer),
        REM_REP(2, RemReplica.serializer),
        APP_OP(3, AppOperation.serializer);

        public final int opcode;
        private final Serializer<Operation> serializer;

        private static final Operation.Type[] opcodeIdx;

        static {
            int maxOpcode = -1;
            for (Operation.Type type : Operation.Type.values())
                maxOpcode = Math.max(maxOpcode, type.opcode);
            opcodeIdx = new Operation.Type[maxOpcode + 1];
            for (Operation.Type type : Operation.Type.values()) {
                if (opcodeIdx[type.opcode] != null)
                    throw new IllegalStateException("Duplicate opcode");
                opcodeIdx[type.opcode] = type;
            }
        }

        Type(int opcode, Serializer<Operation> serializer) {
            this.opcode = opcode;
            this.serializer = serializer;
        }

        public static Operation.Type fromOpcode(int opcode) {
            if (opcode >= opcodeIdx.length || opcode < 0)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            Operation.Type t = opcodeIdx[opcode];
            if (t == null)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            return t;
        }
    }

    private final Type type;

    public Operation(Type type) {
        this.type = type;
    }

    public static byte[] serialize(Operation operation) {
        try {
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(operation.type.opcode);
            operation.type.serializer.serialize(operation, buf);
            return buf.array();
        } catch (IOException e) {
            e.printStackTrace();
            throw new AssertionError();
        }
    }

    public static Operation deserialize(byte[] op) {
        try {
            ByteBuf buf = Unpooled.buffer().writeBytes(op);
            Type type = Type.fromOpcode(buf.readInt());
            return type.serializer.deserialize(buf);
        } catch (IOException e) {
            e.printStackTrace();
            throw new AssertionError();
        }
    }

}
