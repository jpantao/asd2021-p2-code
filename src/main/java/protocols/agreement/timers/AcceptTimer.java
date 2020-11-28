package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class AcceptTimer extends ProtoTimer {
    public static final short TIMER_ID = 115;

    private final int instance;

    public AcceptTimer(int instance) {
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
