package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class RejectMessage extends ProtoMessage {

    public final static short MSG_ID = 116;

    private final int ins;

    public RejectMessage(int ins) {
        super(MSG_ID);
        this.ins = ins;
    }

    public int getInstance() {
        return ins;
    }

    @Override
    public String toString() {
        return "RejectMessage{" +
                "ins=" + ins +
                '}';
    }

    public static ISerializer<RejectMessage> serializer = new ISerializer<RejectMessage>() {
        @Override
        public void serialize(RejectMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
        }

        @Override
        public RejectMessage deserialize(ByteBuf byteBuf) throws IOException {
            return new RejectMessage(byteBuf.readInt());
        }
    };
}
