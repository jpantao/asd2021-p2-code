package protocols.agreement;

import protocols.agreement.messages.AcceptOkMessage;
import protocols.agreement.messages.PrepareOkMessage;

import java.util.HashSet;
import java.util.Set;

public class Instance {

    // proposer
    Integer pn;
    byte[] pv;
    Set<PrepareOkMessage> pQuorum;

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

}


