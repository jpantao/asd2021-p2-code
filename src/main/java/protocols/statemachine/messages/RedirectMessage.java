package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import protocols.statemachine.utils.Operation;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;

public class RedirectMessage extends ProtoMessage {
    public static final short MSG_ID = 203;

    private final Operation operation;

    public RedirectMessage(Operation operation) {
        super(MSG_ID);
        this.operation = operation;
    }

    public Operation getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "RedirectMessage{" +
                "operation=" + operation +
                '}';
    }

    public static ISerializer<RedirectMessage> serializer = new ISerializer<RedirectMessage>() {
        @Override
        public void serialize(RedirectMessage msg, ByteBuf buf) throws IOException {
            byte[] serialized_op = Operation.serialize(msg.operation);
            buf.writeInt(serialized_op.length);
            buf.writeBytes(serialized_op);
        }

        @Override
        public RedirectMessage deserialize(ByteBuf buf) throws IOException {
            byte[] serialized_op = new byte[buf.readInt()];
            buf.readBytes(serialized_op);
            return new RedirectMessage(Operation.deserialize(serialized_op));
        }
    };
}
