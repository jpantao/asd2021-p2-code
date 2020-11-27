package protocols.agreement.notifications;

import org.apache.commons.codec.binary.Hex;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class DecidedNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 101;

    private final int instance;
    private final byte[] operation;

    public DecidedNotification(int instance, byte[] operation) {
        super(NOTIFICATION_ID);
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
        return "DecidedNotification{" +
                "instance=" + instance +
                ", operation=" + Hex.encodeHexString(operation) +
                '}';
    }
}
