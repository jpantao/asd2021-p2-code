package protocols.agreement.utils;


public class PaxosState {

    private boolean acceptStatus;
    private int np;
    private int na;
    private byte[] va;
    private int highestNa;
    private byte[] highestVa;
    private int acceptQuorum;
    private int prepareQuorum;
    private long quorumTimerID;

    public PaxosState(int np, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
        this.acceptStatus = false;
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
    }

    public PaxosState(int np, int na, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = na;
        this.acceptQuorum = 0;
        this.prepareQuorum = 0;
        this.acceptStatus = false;
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
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

    public byte[] getHighestVa() {
        return highestVa;
    }

    public void setHighestNa(int highestNa) {
        this.highestNa = highestNa;
    }

    public int getHighestNa() {
        return highestNa;
    }

    public void setHighestVa(byte[] highestVa){
        this.highestVa = highestVa;
    }

    public void setQuorumTimerID(long quorumTimerID) {
        this.quorumTimerID = quorumTimerID;
    }

    public long getQuorumTimerID() {
        return quorumTimerID;
    }

}