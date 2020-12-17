package protocols.statemachine.utils;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class RemReplica extends Operation {

    private final Host node;

    public RemReplica(Host node) {
        super(Type.ADD_REP);
        this.node = node;
    }

    public Host getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "RemReplica{" +
                "node=" + node +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemReplica)) return false;
        RemReplica that = (RemReplica) o;
        return node.equals(that.node);
    }

    public static Serializer<Operation> serializer = new Serializer<Operation>() {
        @Override
        public void serialize(Operation operation, ByteBuf buf) throws IOException {
            Host.serializer.serialize(((RemReplica) operation).node, buf);
        }

        @Override
        public RemReplica deserialize(ByteBuf buf) throws IOException {
            return new RemReplica(Host.serializer.deserialize(buf));
        }
    };


}
