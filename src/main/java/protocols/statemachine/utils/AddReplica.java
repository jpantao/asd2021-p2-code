package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class AddReplica extends Operation {

    private final Host node;

    public AddReplica(Host node) {
        super(Type.ADD_REP);
        this.node = node;
    }

    public Host getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "AddReplica{" +
                "node=" + node +
                '}';
    }

    public static Serializer<Operation> serializer = new Serializer<Operation>() {
        @Override
        public void serialize(Operation operation, ByteBuf buf) throws IOException {
            Host.serializer.serialize(((AddReplica) operation).node, buf);
        }

        @Override
        public AddReplica deserialize(ByteBuf buf) throws IOException {
            return new AddReplica(Host.serializer.deserialize(buf));
        }
    };


}
