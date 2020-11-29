package protocols.agreement.utils;


public class PaxosState {

    private int np;
    private int na;
    private byte[] va;
    private int acceptQuorums;
    private int prepareQuorums;

    public PaxosState(int np, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorums = 0;
        this.prepareQuorums = 0;
    }

    public PaxosState(int np) {
        this.np = np;
        this.va = null;
        this.na = -1;
        this.acceptQuorums = 0;
        this.prepareQuorums = 0;
    }

    public int getNp() {
        return np;
    }

    public void setNp(int np) {
        this.np = np;
    }

    public byte[] getVa() {
        return va;
    }

    public void setVa(byte[] v) {
        this.va = v;
    }

    public int getNa() {
        return na;
    }

    public void setNa(int na) {
        this.na = na;
    }

    public int getAcceptQuorums() {
        return acceptQuorums;
    }

    public void setAcceptQuorums() {
        acceptQuorums++;
    }

    public int getPrepareQuorums() {
        return prepareQuorums = 0;
    }

    public void setPrepareQuorums(){
        prepareQuorums++;
    }

}