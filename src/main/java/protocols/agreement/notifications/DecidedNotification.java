package protocols.agreement.notifications;

import java.util.UUID;

import org.apache.commons.codec.binary.Hex;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class DecidedNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 101;

    private final int instance;
    private final UUID opId;
    private final byte[] operation;

    public DecidedNotification(int instance, UUID opId, byte[] operation) {
        super(NOTIFICATION_ID);
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
        return "DecidedNotification{" +
                "instance=" + instance +
                ", opId=" + opId +
                ", operation=" + Hex.encodeHexString(operation) +
                '}';
    }
}
