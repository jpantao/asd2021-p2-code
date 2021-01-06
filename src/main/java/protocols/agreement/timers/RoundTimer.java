package protocols.agreement.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class RoundTimer extends ProtoTimer {

    public static final short TIMER_ID = 111;

    private final int instance;

    public RoundTimer(int instance) {
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
