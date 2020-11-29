package protocols.statemachine.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

public class RetryConnTimer extends ProtoTimer {
    public static final short TIMER_ID = 201;

    private final Host toConn;

    public RetryConnTimer(Host toConn) {
        super(TIMER_ID);
        this.toConn = toConn;
    }


    public Host getToConn() {
        return toConn;
    }

    @Override
    public String toString() {
        return "RetryConnTimer{" +
                "toConn=" + toConn +
                '}';
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
