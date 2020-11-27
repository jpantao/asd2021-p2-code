package protocols.agreement.utils;

public class PaxosState {

    private int n;
    private Integer v;

    public PaxosState(int n, int v) {
        this.n = n;
        this.v = v;
    }

    public PaxosState(int n) {
        this.n = n;
        this.v = null;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void setV(Integer v) {
        this.v = v;
    }

    public int getN() {
        return n;
    }

    public Integer getV() {
        return v;
    }

    @Override
    public String toString() {
        return "InstanceState{" +
                "n=" + n +
                ", v=" + v +
                '}';
    }

}
