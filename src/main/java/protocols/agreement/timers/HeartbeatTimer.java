package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class HeartbeatTimer extends ProtoTimer {

    public static final short TIMER_ID = 120;

    private final int instance;

    public HeartbeatTimer(int instance) {
        super(TIMER_ID);
        this.instance = instance;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

    public int getInstance() {
        return instance;
    }
}
