package protocols.agreement.utils;


public class PaxosState {

    private int n;
    private byte[] v;

    public PaxosState(int n, byte[] v) {
        this.n = n;
        this.v = v;
    }

    public PaxosState(int n) {
        this.n = n;
        this.v = null;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public byte[] getV() {
        return v;
    }

    public void setV(byte[] v) {
        this.v = v;
    }
}
