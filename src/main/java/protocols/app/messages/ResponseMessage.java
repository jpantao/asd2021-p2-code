package protocols.app.messages;

import org.apache.commons.codec.binary.Hex;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class ResponseMessage extends ProtoMessage {

    public final static short MSG_ID = 302;

    private final long opId;
    private final byte[] data;

    public ResponseMessage(long opId, byte[] data) {
        super(MSG_ID);
        this.opId = opId;
        this.data = data;
    }


    public long getOpId() {
        return opId;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "opId=" + opId +
                ", data=" + Hex.encodeHexString(data) +
                '}';
    }

    public static ISerializer<ResponseMessage> serializer = new ISerializer<ResponseMessage>() {
        @Override
        public void serialize(ResponseMessage responseMsg, ByteBuf out) {
            out.writeLong(responseMsg.opId);
            out.writeInt(responseMsg.data.length);
            out.writeBytes(responseMsg.data);
        }

        @Override
        public ResponseMessage deserialize(ByteBuf in) {
            long opId = in.readLong();
            byte[] data = new byte[in.readInt()];
            in.readBytes(data);
            return new ResponseMessage(opId, data);
        }
    };
}
