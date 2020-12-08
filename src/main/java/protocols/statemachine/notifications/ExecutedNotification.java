package protocols.statemachine.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class ExecutedNotification extends ProtoNotification {
    public static final short NOTIFICATION_ID = 203;

    private final int instance;

    public ExecutedNotification(int instance) {
        super(NOTIFICATION_ID);
        this.instance = instance;
    }

    public int getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "ExecutedNotification{" +
                "instance=" + instance +
                '}';
    }
}
