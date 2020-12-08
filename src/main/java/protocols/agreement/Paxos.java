package protocols.agreement;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.statemachine.notifications.ExecutedNotification;
import protocols.statemachine.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.agreement.timers.QuorumTimer;
import protocols.agreement.utils.PaxosState;
import protocols.statemachine.notifications.ChannelReadyNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class Paxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public static final short PROTOCOL_ID = 110;
    public static final String PROTOCOL_NAME = "Paxos";

    private Host self; //My own address/port

    private final Map<Integer, PaxosState> instances;
    private final Set<Host> membership;
    private final int n;
    private final int quorumTimeout;
    private int last_executed_instance;


    public Paxos(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.n = Integer.parseInt(props.getProperty("n"));
        this.quorumTimeout = Integer.parseInt(props.getProperty("quorum_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();
        this.last_executed_instance = -1;


        /*---------------------- Register Timer Handlers --------------------------- */
        registerTimerHandler(QuorumTimer.TIMER_ID, this::uponQuorumTimeout);

        /*---------------------- Register Request Handlers ------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponPropose);
        registerRequestHandler(AddReplicaRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(RemoveReplicaRequest.REQUEST_ID, this::uponRemoveReplica);

        /*---------------------- Register Notification Handlers -------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoined);
        subscribeNotification(ExecutedNotification.NOTIFICATION_ID, this::uponExecuted);
    }


    @Override
    public void init(Properties props) {
        //Nothing to do here, we just wait for events from the application or agreement
    }

    private void uponChannelCreated(ChannelReadyNotification notification, short sourceProto) {
        int cId = notification.getChannelId();
        self = notification.getMyself();
        logger.info("Channel {} created, I am {}", cId, self);
        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);
        /*---------------------- Register Message Serializers ----------------------- */
        registerMessageSerializer(cId, PrepareMessage.MSG_ID, PrepareMessage.serializer);
        registerMessageSerializer(cId, PrepareOkMessage.MSG_ID, PrepareOkMessage.serializer);
        registerMessageSerializer(cId, AcceptMessage.MSG_ID, AcceptMessage.serializer);
        registerMessageSerializer(cId, AcceptOkMessage.MSG_ID, AcceptOkMessage.serializer);
        registerMessageSerializer(cId, RejectMessage.MSG_ID, RejectMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, PrepareMessage.MSG_ID, this::uponPrepare);
            registerMessageHandler(cId, PrepareOkMessage.MSG_ID, this::uponPrepareOk);
            registerMessageHandler(cId, AcceptMessage.MSG_ID, this::uponAccept);
            registerMessageHandler(cId, AcceptOkMessage.MSG_ID, this::uponAcceptOk);
            registerMessageHandler(cId, RejectMessage.MSG_ID, this::uponReject);
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }
    }

    /*--------------------------------- Requests ----------------------------------- */

    private void uponPropose(ProposeRequest request, short sourceProto) {
        int instance = request.getInstance();
        String op = request.getOperation() == null ? "null" : String.valueOf(request.getOperation()[0]);
        logger.debug("[{}] Propose {} : op-{}", instance, self, op);
        PaxosState state = instances.get(instance);
        if (state == null)
            state = new PaxosState(n, request.getOperation());
        instances.put(instance, state);
        propose(instance, n, state);
    }

    private boolean tryToDecide(PaxosState state, int instance) {
        if (last_executed_instance < instance && state.getAcceptQuorum().size() > membership.size() / 2) {
            triggerNotification(new DecidedNotification(instance, state.getVa()));
            return true;
        }
        return false;
    }

    private void propose(int instance, int np, PaxosState state) {
        state.setNp(np);
        for (Host p : membership)
            sendMessage(new PrepareMessage(instance, np), p);
        long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
        state.setQuorumTimerID(quorumTimer);
    }

    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        PaxosState state = instances.get(instance);
        if (state == null) {
            state = new PaxosState(np);
            instances.put(instance, state);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
        } else if (np > state.getNp()) {
            state.setNp(msg.getN());
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
        } //else
        //sendMessage(new RejectMessage(instance), from);
    }

    private void uponPrepareOk(PrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        PaxosState state = instances.get(instance);
        state.updatePrepareQuorum(from);
        int naReceived = msg.getNa();
        byte[] vaReceived = msg.getVa();
        int highestNa = state.getHighestNa();
        if (naReceived > highestNa) {
            logger.debug("[{}] PrepareOk (IF) {} -> {}: na-{} hna-{}", instance, from, self, naReceived, highestNa);
            state.setHighestNa(naReceived);
            state.setHighestVa(vaReceived);
        }

        String hva = state.getHighestVa() == null ? "null" : String.valueOf(state.getHighestVa()[0]);
        String var = vaReceived == null ? "null" : String.valueOf(vaReceived[0]);
        logger.debug("[{}] PrepareOk (AFTER IF) {} -> {}: na-{} hna-{} va-{} hva-{}", instance, from, self, naReceived, state.getHighestNa(), var, hva);
        logger.debug("[{}] PrepareOk (QUORUM) {} -> {}: m-{} p-{} a-{}", instance, from, self, membership.size() / 2, state.getPrepareQuorum().size(), state.getAcceptQuorum().size());

        if (state.getPrepareQuorum().size() > membership.size() / 2) {
            cancelTimer(state.getQuorumTimerID());
            int np = state.getNp();
            byte[] v = state.getHighestVa();
            for (Host p : membership)
                sendMessage(new AcceptMessage(instance, np, v), p);
            long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
            state.setQuorumTimerID(quorumTimer);
        }
    }

    private void uponAccept(AcceptMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);

        if (state == null) {
            state = new PaxosState(np, v);
            instances.put(instance, state);
        }

        logger.debug("[{}] Accept (Before) {} -> {}: m-{} p-{} a-{}", instance, from, self, membership.size() / 2, state.getPrepareQuorum().size(), state.getAcceptQuorum().size());
        if (np >= state.getNp()) {
            state.setNa(np);
            state.setVa(v);
            logger.debug("[{}] Accept (after) {} -> {}: m-{} p-{} a-{}", instance, from, self, membership.size() / 2, state.getPrepareQuorum().size(), state.getAcceptQuorum().size());
            for (Host p : membership)
                sendMessage(new AcceptOkMessage(instance, np, v), p);
        }
    }

    private void uponAcceptOk(AcceptOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int n = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);

        if (state == null) {
            state = new PaxosState(n, n, v);
            instances.put(instance, state);
        }
        logger.debug("[{}] Accept OK (Before) {} -> {}: m-{} p-{} a-{}", instance, from, self, membership.size() / 2, state.getPrepareQuorum().size(), state.getAcceptQuorum().size());

        if (n > state.getNa()) {
            state.setNa(n);
            state.setVa(v);
            state.resetAcceptQuorum();
        }
        logger.debug("[{}] Accept OK (After) {} -> {}: m-{} p-{} a-{}", instance, from, self, membership.size() / 2, state.getPrepareQuorum().size(), state.getAcceptQuorum().size());
        state.updateAcceptQuorum(from);
        tryToDecide(state, instance);
    }

    private void uponReject(RejectMessage msg, Host from, short sourceProto, int channelId) {
        cancelTimer(instances.get(msg.getInstance()).getQuorumTimerID());
    }

    private void uponJoined(JoinedNotification notification, short sourceProto) {
        membership.addAll(notification.getMembership());
    }

    private void uponExecuted(ExecutedNotification notification, short sourceProto) {
        PaxosState state = instances.get(last_executed_instance + 1);
        if (state != null)
            tryToDecide(state, last_executed_instance + 1);
        last_executed_instance++;
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        membership.remove(request.getReplica());
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        membership.add(request.getReplica());
    }


    /* -------------------------------- Timers ------------------------------------- */

    private void uponQuorumTimeout(QuorumTimer quorumTimer, long timerID) {
        int instance = quorumTimer.getInstance();
        PaxosState state = instances.get(instance);
        if (instance > last_executed_instance) {
            state.resetPrepareQuorum();
            state.resetAcceptQuorum();
            propose(instance, state.getNp() + membership.size() + n, state);
        }
    }
}
