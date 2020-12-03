package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class JoinMessage extends ProtoMessage {
    public static final short MSG_ID = 201;

    public JoinMessage() {
        super(MSG_ID);
    }

    @Override
    public String toString() {
        return "JoinMessage{}";
    }

    public static ISerializer<JoinMessage> serializer = new ISerializer<JoinMessage>() {
        @Override
        public void serialize(JoinMessage msg, ByteBuf buf) throws IOException {

        }

        @Override
        public JoinMessage deserialize(ByteBuf buf) throws IOException {
            return new JoinMessage();
        }
    };
}
