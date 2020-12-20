package protocols.agreement.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

public class LeaderElectedNotification extends ProtoNotification {
    public static final short NOTIFICATION_ID = 103;

    private final Host leader;
    private final int instance;

    public LeaderElectedNotification(Host leader, int instance) {
        super(NOTIFICATION_ID);
        this.leader = leader;
        this.instance = instance;
    }

    public Host getLeader() {
        return leader;
    }

    public int getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "LeaderElectedNotification{" +
                "leader=" + leader +
                ", instance=" + instance +
                '}';
    }
}
