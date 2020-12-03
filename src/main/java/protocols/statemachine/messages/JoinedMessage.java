package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;

public class JoinedMessage extends ProtoMessage {
    public static final short MSG_ID = 202;

    int instance;
    byte [] state;

    public JoinedMessage(int instance, byte[] state) {
        super(MSG_ID);
        this.instance = instance;
        this.state = state;
    }

    public int getInstance() {
        return instance;
    }

    public byte[] getState() {
        return state;
    }

    @Override
    public String toString() {
        return "JoinedMessage{" +
                "instance=" + instance +
                ", state=" + Arrays.toString(state) +
                '}';
    }

    public static ISerializer<JoinedMessage> serializer = new ISerializer<JoinedMessage>() {
        @Override
        public void serialize(JoinedMessage msg, ByteBuf buf) throws IOException {
            buf.writeInt(msg.instance);
            buf.writeInt(msg.state.length);
            buf.writeBytes(msg.state);
        }

        @Override
        public JoinedMessage deserialize(ByteBuf buf) throws IOException {
            int instance = buf.readInt();
            byte[] state = new byte[buf.readInt()];
            buf.readBytes(state);
            return new JoinedMessage(instance, state);
        }
    };
}
