package protocols.agreement.utils;


public class PaxosState {

    private boolean acceptStatus;
    private int np;
    private int na;
    private byte[] va;
    private int acceptQuorum;
    private int prepareQuorum;

    public PaxosState(int np, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
        this.acceptStatus = false;
    }

    public PaxosState(int np, int na, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = na;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
        this.acceptStatus = false;
    }

    public PaxosState(int np) {
        this.np = np;
        this.va = null;
        this.na = -1;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
        this.acceptStatus = false;
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

    public int getAcceptQuorum() {
        return acceptQuorum;
    }

    public void updateAcceptQuorum() {
        acceptQuorum++;
    }

    public void resetAcceptQuorum() {
        acceptQuorum = 0;
    }

    public int getPrepareQuorum() {
        return prepareQuorum;
    }

    public void updatePrepareQuorum() {
        prepareQuorum++;
    }
    
    public void accept(){
        acceptStatus = true;
    }
    
    public boolean accepted(){
        return acceptStatus;
    }

    public void reset(int n, byte[] va) {
        this.np = n;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
    }


}