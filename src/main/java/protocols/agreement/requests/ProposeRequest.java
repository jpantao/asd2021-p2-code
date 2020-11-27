package protocols.agreement.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import org.apache.commons.codec.binary.Hex;

import java.util.UUID;

public class ProposeRequest extends ProtoRequest {

    public static final short REQUEST_ID = 101;

    private final int instance;
    private final byte[] operation;

    public ProposeRequest(int instance, byte[] operation) {
        super(REQUEST_ID);
        this.instance = instance;
        this.operation = operation;
    }

    public int getInstance() {
        return instance;
    }

    public byte[] getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "ProposeRequest{" +
                "instance=" + instance +
                ", operation=" + Hex.encodeHexString(operation) +
                '}';
    }
}
