package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;

public class Nop extends Operation {

    public Nop() {
        super(Type.NOP);
    }

    @Override
    public String toString() {
        return "Nop{}";
    }

    public static Serializer<Operation> serializer = new Serializer<>() {
        @Override
        public void serialize(Operation operation, ByteBuf buf) {
        }

        @Override
        public Nop deserialize(ByteBuf buf) {
            return new Nop();
        }
    };


}
