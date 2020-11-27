package protocols.app.messages;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Hex;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class RequestMessage extends ProtoMessage {

    public final static short MSG_ID = 301;

    public final static byte READ = 0;
    public final static byte WRITE = 1;

    private final long opId;
    private final byte opType;
    private final String key;
    private final byte[] data;

    public RequestMessage(long opId, byte opType, String key, byte[] data) {
        super(MSG_ID);
        this.opId = opId;
        this.opType = opType;
        this.key = key;
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public long getOpId() {
        return opId;
    }

    public String getKey() {
        return key;
    }

    public byte getOpType() {
        return opType;
    }

    @Override
    public String toString() {
        return "RequestMessage{" +
                "opId=" + opId +
                ", opType=" + opType +
                ", key='" + key + '\'' +
                ", data=" + Hex.encodeHexString(data) +
                '}';
    }

    public static ISerializer<RequestMessage> serializer = new ISerializer<RequestMessage>() {
        @Override
        public void serialize(RequestMessage requestMessage, ByteBuf out) {
            out.writeLong(requestMessage.opId);
            out.writeByte(requestMessage.opType);
            byte[] keyBytes = requestMessage.key.getBytes(StandardCharsets.UTF_8);
            out.writeInt(keyBytes.length);
            out.writeBytes(keyBytes);
            out.writeInt(requestMessage.data.length);
            out.writeBytes(requestMessage.data);
        }

        @Override
        public RequestMessage deserialize(ByteBuf in) {
            long opId = in.readLong();
            byte opType = in.readByte();
            byte[] keyBytes = new byte[in.readInt()];
            in.readBytes(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);
            byte[] data = new byte[in.readInt()];
            in.readBytes(data);
            return new RequestMessage(opId, opType, key, data);
        }
    };
}
