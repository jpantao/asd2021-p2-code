package protocols.app.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class InstallStateRequest extends ProtoRequest {

    public static final short REQUEST_ID = 302;

    private byte[] state;
    
    public InstallStateRequest(byte[] state) {
        super(REQUEST_ID);
        this.state = state;
    }


    public byte[] getState() {
    	return this.state;
    }

    @Override
    public String toString() {
        return "CurrentStateReply{" +
                "number of bytes=" + state.length +
                '}';
    }
}
