package protocols.agreement.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

public class LeaderElectedNotification extends ProtoNotification {
    public static final short NOTIFICATION_ID = 103;

    private final Host leader;

    public LeaderElectedNotification(Host leader) {
        super(NOTIFICATION_ID);
        this.leader = leader;
    }

    public Host getLeader() {
        return leader;
    }

    @Override
    public String toString() {
        return "LeaderElectedNotification{" +
                "leader=" + leader +
                '}';
    }

}
