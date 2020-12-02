package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public abstract class Op {

    public enum Type {
        NOP(0, null),
        ADD_REP(1, null),
        REM_REP(2, null),
        APP_OP(3, null);

        public final int opcode;
        private final Serializer<Op> serializer;

        private static final Op.Type[] opcodeIdx;

        static {
            int maxOpcode = -1;
            for (Op.Type type : Op.Type.values())
                maxOpcode = Math.max(maxOpcode, type.opcode);
            opcodeIdx = new Op.Type[maxOpcode + 1];
            for (Op.Type type : Op.Type.values()) {
                if (opcodeIdx[type.opcode] != null)
                    throw new IllegalStateException("Duplicate opcode");
                opcodeIdx[type.opcode] = type;
            }
        }

        Type(int opcode, Serializer<Op> serializer) {
            this.opcode = opcode;
            this.serializer = serializer;
        }

        public static Op.Type fromOpcode(int opcode) {
            if (opcode >= opcodeIdx.length || opcode < 0)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            Op.Type t = opcodeIdx[opcode];
            if (t == null)
                throw new AssertionError(String.format("Unknown opcode %d", opcode));
            return t;
        }
    }

    private final Type type;

    public Op(Type type) {
        this.type = type;
    }

    public static byte[] serialize(Op op) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(op.type.opcode);
        op.type.serializer.serialize(op, buf);
        return buf.array();
    }

    public static Op deserialize(byte[] op) {
        ByteBuf buf = Unpooled.buffer().writeBytes(op);
        Type type = Type.fromOpcode(buf.readInt());
        return type.serializer.deserialize(buf);
    }

}
