package protocols.agreement.utils;


import pt.unl.fct.di.novasys.network.data.Host;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PaxosState {

    private boolean acceptStatus;
    private int np;
    private int na;
    private byte[] va;
    private int highestNa;
    private byte[] highestVa;
    private Set<Host> acceptQuorum;
    private Set<Host> prepareQuorum;
    private long quorumTimerID;
    private Set<Host> membership;

    public PaxosState(int np, byte[] va) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = new HashSet<>();
        this.prepareQuorum = new HashSet<>();
        this.acceptStatus = false;
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
        this.membership = new HashSet<>();
    }

    public PaxosState(int np, byte[] va, Set<Host> membership) {
        this.np = np;
        this.va = va;
        this.na = -1;
        this.acceptQuorum = new HashSet<>();
        this.prepareQuorum = new HashSet<>();
        this.acceptStatus = false;
        this.highestNa = na;
        this.highestVa = va;
        this.quorumTimerID = -1;
        this.membership = membership;
    }

    public void setMembership(Set<Host> m) {
        this.membership = m;
    }

    public int getQuorumSize() {
        return membership.size() == 0 ? -1 : membership.size() / 2 + 1;
    }

    public void addReplica(Host host) {
        membership.add(host);
    }

    public void removeReplica(Host host) {
        membership.remove(host);
    }

    public Set<Host> getMembership() {
        return membership;
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

    public boolean hasAcceptQuorum() {
        return getQuorumSize() > 0 && acceptQuorum.size() >= getQuorumSize();
    }

    public Set<Host> getPrepareQuorum() {
        return prepareQuorum;
    }

    public void updatePrepareQuorum(Host p) {
        prepareQuorum.add(p);
    }

    public boolean hasPrepareQuorum() {
        return getQuorumSize() > 0 && prepareQuorum.size() >= getQuorumSize();
    }

    public void accept() {
        acceptStatus = true;
    }

    public boolean accepted() {
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

    public void setHighestVa(byte[] highestVa) {
        this.highestVa = highestVa;
    }

    public void setQuorumTimerID(long quorumTimerID) {
        this.quorumTimerID = quorumTimerID;
    }

    public long getQuorumTimerID() {
        return quorumTimerID;
    }

}