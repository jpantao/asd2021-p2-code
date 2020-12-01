package protocols.statemachine.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

public class RetryConnTimer extends ProtoTimer {
    public static final short TIMER_ID = 201;

    private final Host node;
    private final int retry;

    public RetryConnTimer(Host node, int retry) {
        super(TIMER_ID);
        this.node = node;
        this.retry = retry;
    }

    public Host getNode() {
        return node;
    }

    public int getRetry() {
        return retry;
    }

    @Override
    public String toString() {
        return "RetryConnTimer{" +
                "toConn=" + node +
                ", retry=" + retry +
                '}';
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
