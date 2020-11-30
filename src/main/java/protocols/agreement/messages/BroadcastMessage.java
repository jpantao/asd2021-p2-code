package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.Arrays;
import java.util.UUID;

/*************************************************
 * This is here just as an example, your solution
 * probably needs to use different message types
 *************************************************/
public class BroadcastMessage extends ProtoMessage {

    public final static short MSG_ID = 101;

    private final int instance;
    private final byte[] op;

    public BroadcastMessage(int instance, byte[] op) {
        super(MSG_ID);
        this.instance = instance;
        this.op = op;
    }

    public int getInstance() {
        return instance;
    }

    public byte[] getOp() {
        return op;
    }

    @Override
    public String toString() {
        return "BroadcastMessage{" +
                "instance=" + instance +
                ", op=" + Arrays.toString(op) +
                '}';
    }

    public static ISerializer<BroadcastMessage> serializer = new ISerializer<BroadcastMessage>() {
        @Override
        public void serialize(BroadcastMessage msg, ByteBuf out) {
            out.writeInt(msg.instance);
            out.writeInt(msg.op.length);
            out.writeBytes(msg.op);
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) {
            int instance = in.readInt();
            byte[] op = new byte[in.readInt()];
            in.readBytes(op);
            return new BroadcastMessage(instance, op);
        }
    };

}
