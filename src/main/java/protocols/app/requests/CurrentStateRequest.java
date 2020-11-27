package protocols.app.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class CurrentStateRequest extends ProtoRequest {

    public static final short REQUEST_ID = 301;

    private int instance;
    
    public CurrentStateRequest(int instance) {
        super(REQUEST_ID);
        this.instance = instance;
    }

    public int getInstance() {
    	return this.instance;
    }

    @Override
    public String toString() {
        return "CurrentStateRequest{" +
                "instance=" + instance +
                '}';
    }
}
