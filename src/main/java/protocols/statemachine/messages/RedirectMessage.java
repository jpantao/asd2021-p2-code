package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;

public class RedirectMessage extends ProtoMessage {
    public static final short MSG_ID = 203;

    private final byte[] operation;

    public RedirectMessage(byte[] operation) {
        super(MSG_ID);
        this.operation = operation;
    }

    public byte[] getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "RedirectMessage{" +
                "operation=" + Arrays.toString(operation) +
                '}';
    }

    public static ISerializer<RedirectMessage> serializer = new ISerializer<RedirectMessage>() {
        @Override
        public void serialize(RedirectMessage msg, ByteBuf buf) throws IOException {
            buf.writeInt(msg.operation.length);
            buf.writeBytes(msg.operation);
        }

        @Override
        public RedirectMessage deserialize(ByteBuf buf) throws IOException {
            byte[] op = new byte[buf.readInt()];
            buf.readBytes(op);
            return new RedirectMessage(op);
        }
    };
}
