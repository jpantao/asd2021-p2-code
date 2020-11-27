package protocols.agreement.utils;

import java.util.Arrays;
import java.util.UUID;

public class PaxosState {

    private int n;
    private UUID opId;
    private byte[] op;

    public PaxosState(int n, UUID opId, byte[] op) {
        this.n = n;
        this.opId = opId;
        this.op = op;
    }

    public PaxosState(int n) {
        this.n = n;
        this.opId = null;
    }

    public int getN() {
        return n;
    }

    public UUID getOpId() {
        return opId;
    }

    public byte[] getOp() {
        return op;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void setOp(UUID opId, byte[] op) {
        this.opId = opId;
        this.op = op;
    }

    @Override
    public String toString() {
        return "PaxosState{" +
                "n=" + n +
                ", opId=" + opId +
                ", op=" + Arrays.toString(op) +
                '}';
    }

}
