package protocols.statemachine.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class NopTimer extends ProtoTimer {
    public static final short TIMER_ID = 202;

    public NopTimer() {
        super(TIMER_ID);
    }

    @Override
    public String toString() {
        return "NOPTimer{}";
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
