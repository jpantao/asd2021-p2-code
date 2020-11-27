package protocols.statemachine.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

public class ChannelReadyNotification extends ProtoNotification {

    public static final short NOTIFICATION_ID = 201;

    private final int channelId;
    private final Host myself;

    public ChannelReadyNotification(int channelId, Host myself) {
        super(NOTIFICATION_ID);
        this.channelId = channelId;
        this.myself = myself;
    }

    public Host getMyself() {
        return myself;
    }

    public int getChannelId() {
        return channelId;
    }

    @Override
    public String toString() {
        return "ChannelCreated{" +
                "channelId=" + channelId +
                ", myself=" + myself +
                '}';
    }
}
