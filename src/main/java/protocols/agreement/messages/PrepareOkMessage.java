package protocols.agreement.messages;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.Arrays;

public class PrepareOkMessage extends ProtoMessage {
    public final static short MSG_ID = 112;

    private final int ins;

    private final int n;

    private final int na;
    private final byte[] va;


    public PrepareOkMessage(int ins, int n, int na, byte[] v) {
        super(MSG_ID);
        this.ins = ins;
        this.n = n;
        this.na = na;
        this.va = v;
    }

    public int getInstance() {
        return ins;
    }

    public int getN() {
        return n;
    }

    public int getNa() {
        return na;
    }

    public byte[] getVa() {
        return va;
    }


    @Override
    public String toString() {
        return "PrepareOkMessage{" +
                "ins=" + ins +
                ", n=" + n +
                ", na=" + na +
                ", va=" + Arrays.toString(va) +
                '}';
    }

    public static ISerializer<PrepareOkMessage> serializer = new ISerializer<PrepareOkMessage>() {
        @Override
        public void serialize(PrepareOkMessage msg, ByteBuf byteBuf) throws IOException {
            if (msg.va != null) {
                byteBuf.writeBoolean(true);
                byteBuf.writeInt(msg.va.length);
                byteBuf.writeBytes(msg.va);
            } else {
                byteBuf.writeBoolean(false);
            }

            byteBuf.writeInt(msg.ins);
            byteBuf.writeInt(msg.n);
            byteBuf.writeInt(msg.na);
        }

        @Override
        public PrepareOkMessage deserialize(ByteBuf byteBuf) throws IOException {

            byte[] v = null;
            if(byteBuf.readBoolean()){
                v = new byte[byteBuf.readInt()];
                byteBuf.readBytes(v);
            }


            int ins = byteBuf.readInt();
            int n = byteBuf.readInt();
            int na = byteBuf.readInt();
            return new PrepareOkMessage(ins , n, na, v);
        }
    };
}
