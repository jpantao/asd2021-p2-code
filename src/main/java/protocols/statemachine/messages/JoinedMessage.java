package protocols.statemachine.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Arrays;

public class JoinedMessage extends ProtoMessage {
    public static final short MSG_ID = 202;

    private final Host leader;
    private final int instance;
    private final byte [] state;

    public JoinedMessage(Host leader, int instance, byte[] state) {
        super(MSG_ID);
        this.leader = leader;
        this.instance = instance;
        this.state = state;
    }

    public Host getLeader() {
        return leader;
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
                "leader=" + leader +
                ", instance=" + instance +
                ", state=" + Arrays.toString(state) +
                '}';
    }

    public static ISerializer<JoinedMessage> serializer = new ISerializer<JoinedMessage>() {
        @Override
        public void serialize(JoinedMessage msg, ByteBuf buf) throws IOException {
            if(msg.leader != null){
                buf.writeBoolean(true);
                Host.serializer.serialize(msg.leader, buf);
            }else {
                buf.writeBoolean(false);
            }

            buf.writeInt(msg.instance);
            buf.writeInt(msg.state.length);
            buf.writeBytes(msg.state);
        }

        @Override
        public JoinedMessage deserialize(ByteBuf buf) throws IOException {
            Host leader = null;
            if(buf.readBoolean()){
                leader = Host.serializer.deserialize(buf);
            }

            int instance = buf.readInt();
            byte[] state = new byte[buf.readInt()];
            buf.readBytes(state);
            return new JoinedMessage(leader, instance, state);
        }
    };
}
