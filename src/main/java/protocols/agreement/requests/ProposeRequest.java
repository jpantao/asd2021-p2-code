package protocols.agreement.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import org.apache.commons.codec.binary.Hex;

import java.util.UUID;

public class ProposeRequest extends ProtoRequest {

    public static final short REQUEST_ID = 101;

    private final int instance;
    private final UUID opId;
    private final byte[] operation;

    public ProposeRequest(int instance, UUID opId, byte[] operation) {
        super(REQUEST_ID);
        this.instance = instance;
        this.opId = opId;
        this.operation = operation;
    }

    public int getInstance() {
        return instance;
    }

    public byte[] getOperation() {
        return operation;
    }

    public UUID getOpId() {
        return opId;
    }

    @Override
    public String toString() {
        return "ProposeRequest{" +
                "instance=" + instance +
                ", opId=" + opId +
                ", operation=" + Hex.encodeHexString(operation) +
                '}';
    }
}
