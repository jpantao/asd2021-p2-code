package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import org.apache.commons.codec.binary.Hex;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class MPAcceptMessage extends ProtoMessage {

    public final static short MSG_ID = 115;

    private final int ins;
    private final int n;
    private final byte[] opDecided;
    private final byte[] newOp;

    public MPAcceptMessage(int ins, int n, byte[] opDecided, byte[] newOp) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.opDecided = opDecided;
        this.newOp = newOp;
    }

    public int getInstance() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public byte[] getOpDecided() {
        return opDecided;
    }

    public byte[] getNewOp() {
        return newOp;
    }

    @Override
    public String toString() {
        return "MPAcceptMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", opDecided=" + Hex.encodeHexString(opDecided) +
                ", newOp=" + Hex.encodeHexString(newOp) +
                '}';
    }

    public static ISerializer<MPAcceptMessage> serializer = new ISerializer<MPAcceptMessage>() {
        @Override
        public void serialize(MPAcceptMessage msg, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.opDecided.length);
            byteBuf.writeInt(msg.newOp.length);
            byteBuf.writeBytes(msg.opDecided);
            byteBuf.writeBytes(msg.newOp);
        }

        @Override
        public MPAcceptMessage deserialize(ByteBuf byteBuf) throws IOException {
            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            byte[] opDecided = new byte[byteBuf.readInt()];
            byte[] newOp = new byte[byteBuf.readInt()];
            byteBuf.readBytes(opDecided);
            byteBuf.readBytes(newOp);
            return new MPAcceptMessage(ins, n, opDecided,newOp);
        }
    };
}
