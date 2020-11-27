package protocols.app.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class CurrentStateReply extends ProtoReply {

    public static final short REQUEST_ID = 301;

    private int instance;
    private byte[] state;
    
    public CurrentStateReply(int instance, byte[] state) {
        super(REQUEST_ID);
        this.instance = instance;
        this.state = state;
    }

    public int getInstance() {
    	return this.instance;
    }
    
    public byte[] getState() {
    	return this.state;
    }

    @Override
    public String toString() {
        return "CurrentStateReply{" +
                "instance=" + instance +
                "number of bytes=" + state.length +
                '}';
    }
}
