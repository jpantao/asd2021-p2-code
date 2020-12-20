package protocols.agreement;

import protocols.agreement.messages.AcceptOkMessage;
import protocols.agreement.messages.PrepareOkMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Instance {

    // proposer
    Integer pn;
    byte[] pv;
    Set<PrepareOkMessage> pQuorum;
    boolean lockedIn;

    // acceptor
    Integer anp;
    Integer ana;
    byte[] ava;

    // learner
    Integer lna;
    byte[] lva;
    Set<AcceptOkMessage> lQuorum;
    byte[] decision;

    Instance(){
    }

    void initProposer(int n, byte[] v){
        this.pn = n;
        this.pv = v;
        this.pQuorum = new HashSet<>();
        this.lockedIn = false;
    }

    void initAcceptor(){
        this.anp = -1;
        this.ana = -1;
        this.ava = null;
    }

    void initLearner(){
        this.lna = -1;
        this.lva = null;
        this.lQuorum = new HashSet<>();
        this.decision = null;
    }

    @Override
    public String toString() {
        return "Instance{" +
                "pn=" + pn +
                ", pv=" + Arrays.hashCode(pv) +
                ", pQuorum=" + pQuorum +
                ", anp=" + anp +
                ", ana=" + ana +
                ", ava=" + Arrays.hashCode(ava) +
                ", lna=" + lna +
                ", lva=" + Arrays.hashCode(lva) +
                ", lQuorum=" + lQuorum +
                ", decision=" + Arrays.hashCode(decision) +
                '}';
    }
}


