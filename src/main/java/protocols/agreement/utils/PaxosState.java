package protocols.agreement.utils;


import pt.unl.fct.di.novasys.network.data.Host;

import java.util.HashSet;
import java.util.Set;

public class PaxosState {

    private int np;
    private int na;
    private byte[] va;
    private int highestNa;
    private byte[] highestVa;
    private Set<Host> acceptQuorum;
    private Set<Host> prepareQuorum;
    private long quorumTimerID;
    private boolean decided;

    private long leaderTimerID; //only used in multi-paxos

    public PaxosState(int np, int na, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = na;
        this.acceptQuorum = new HashSet<>();
        this.prepareQuorum = new HashSet<>();
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
        this.leaderTimerID = -1;
        this.decided = false;
    }

    public PaxosState(int np, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = new HashSet<>();
        this.prepareQuorum = new HashSet<>();
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
        this.leaderTimerID = -1;
        this.decided = false;
    }

    public PaxosState(int np) {
        this.np = np;
        this.va = null;
        this.na = -1;
        this.acceptQuorum = new HashSet<>();
        this.prepareQuorum = new HashSet<>();
        this.highestNa = na;
        this.highestVa = null;
        this.quorumTimerID = -1;
        this.leaderTimerID = -1;
        this.decided = false;
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

    public Set<Host> getAcceptQuorum() {
        return acceptQuorum;
    }

    public void updateAcceptQuorum(Host p) {
        acceptQuorum.add(p);
    }

    public void resetAcceptQuorum() {
        acceptQuorum = new HashSet<>();
    }

    public Set<Host> getPrepareQuorum() {
        return prepareQuorum;
    }

    public void updatePrepareQuorum(Host p) {
        prepareQuorum.add(p);
    }

    public void resetPrepareQuorum() {
        this.prepareQuorum = new HashSet<>();
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

    public void setHighestVa(byte[] highestVa) {
        this.highestVa = highestVa;
    }

    public void setQuorumTimerID(long quorumTimerID) {
        this.quorumTimerID = quorumTimerID;
    }

    public long getQuorumTimerID() {
        return quorumTimerID;
    }

    public long getLeaderTimerID() {
        return leaderTimerID;
    }

    public void setLeaderTimerID(long leaderTimerID) {
        this.leaderTimerID = leaderTimerID;
    }

    public void decided() {
        this.decided = true;
    }

    public boolean isDecided() {
        return decided;
    }
}