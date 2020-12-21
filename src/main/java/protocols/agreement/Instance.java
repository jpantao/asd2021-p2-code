package protocols.agreement;


import org.apache.commons.lang3.tuple.Pair;
import protocols.agreement.messages.AcceptOkMessage;

import java.util.*;

public class Instance {

    // proposer
    Integer pn;
    byte[] pv;
    List<Pair<Integer, byte[]>> pQuorum;

    // acceptor
    Integer anp;
    Integer ana;
    byte[] ava;

    // learner
    Integer lna;
    byte[] lva;
    List<AcceptOkMessage> lQuorum;
    byte[] decision;

    Instance(){
    }

    void initProposer(int n, byte[] v){
        this.pn = n;
        this.pv = v;
        this.pQuorum = new LinkedList<>();
    }

    void initAcceptor(){
        this.anp = -1;
        this.ana = -1;
        this.ava = null;
    }

    void initLearner(){
        this.lna = -1;
        this.lva = null;
        this.lQuorum = new LinkedList<>();
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


