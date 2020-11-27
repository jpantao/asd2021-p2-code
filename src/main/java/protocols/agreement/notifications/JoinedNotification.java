package protocols.agreement.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.List;

public class JoinedNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 102;

    private final List<Host> membership;
    private final int joinInstance;

    public JoinedNotification(List<Host> membership, int joinInstance) {
        super(NOTIFICATION_ID);
        this.membership = membership;
        this.joinInstance = joinInstance;
    }

    public int getJoinInstance() {
        return joinInstance;
    }

    public List<Host> getMembership() {
        return membership;
    }

    @Override
    public String toString() {
        return "JoinedNotification{" +
                "membership=" + membership +
                ", joinInstance=" + joinInstance +
                '}';
    }
}
