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
    private int lastExecutedInstance;

    public Paxos(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.n = Integer.parseInt(props.getProperty("n"));
        this.quorumTimeout = Integer.parseInt(props.getProperty("quorum_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();
        this.lastExecutedInstance = -1;

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
        logger.debug("Propose: {}  - {}", request.getInstance(), Arrays.hashCode(request.getOperation()));
        int instance = request.getInstance();
        PaxosState state = instances.computeIfAbsent(instance, k -> new PaxosState(n));
        state.setProposedByMeValueFromAbove(request.getOperation());

        if (!state.isDecided())
            sendPrepares(instance, n, state);

    }

    private boolean canDecide(PaxosState state, int instance) {
        return lastExecutedInstance == (instance - 1)
                && state.getAcceptQuorum().size() > membership.size() / 2
                && !state.isDecided();
    }

    private void decide(PaxosState state, int instance) {
        triggerNotification(new DecidedNotification(instance, state.getVa()));
        state.decided();
    }

    private void sendPrepares(int instance, int np, PaxosState state) {
        PrepareMessage msg = new PrepareMessage(instance, np);
        logger.debug("Sending: {}", msg);
        for (Host p : membership)
            sendMessage(msg, p);
        long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
        state.setQuorumTimerID(quorumTimer);
    }

    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        int instance = msg.getInstance();
        int np = msg.getN();
        PaxosState state = instances.get(instance);

        if (state == null) {
            state = new PaxosState(np);
            state.setPrepared(np);
            instances.put(instance, state);
            logger.debug("Sending: {}", new PrepareOkMessage(instance, state.getNp(), state.getNa(), state.getVa()));
            sendMessage(new PrepareOkMessage(instance, np, state.getNa(), state.getVa()), from);
        } else if (np > state.getNp()) {
            state.setNp(np);
            state.setPrepared(np);
            logger.debug("Sending: {}", new PrepareOkMessage(instance, state.getNp(), state.getNa(), state.getVa()));
            sendMessage(new PrepareOkMessage(instance, np, state.getNa(), state.getVa()), from);
        }
    }

    private void uponPrepareOk(PrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        int instance = msg.getInstance();
        PaxosState state = instances.get(instance);
        int naReceived = msg.getNa();
        byte[] vaReceived = msg.getVa();
        int highestNa = state.getHighestNa();


        if(msg.getN() < state.getNp())
            return;

        state.updatePrepareQuorum(from);

        if (naReceived > highestNa) {
            state.setHighestNa(naReceived);
            state.setHighestVa(vaReceived);
        }

        if (state.getPrepareQuorum().size() > membership.size() / 2 && state.getPrepared() != -1) {

            state.setPrepared(-1);

            cancelTimer(state.getQuorumTimerID());
            byte[] v = state.getHighestVa();
            if (v == null) {
                state.changeToMyVal();
                v = state.getVa();
            }

            state.setVa(v);



            logger.debug("Sending: {}", new AcceptMessage(instance, msg.getN(), v));
            for (Host p : membership)
                sendMessage(new AcceptMessage(instance, state.getNp(), v), p);

            //TODO: podemos usar apenas um timer maior sem fazer reset
            long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
            state.setQuorumTimerID(quorumTimer);
        }
    }

    private void uponAccept(AcceptMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        int instance = msg.getInstance();
        int np = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);
        if (state == null) {
            state = new PaxosState(np, v);
            instances.put(instance, state);
        }

        if (np >= state.getNp()) {
            state.setNa(np);
            state.setVa(v);
            logger.debug("Sending: {}", new AcceptOkMessage(instance, np, v));
            for (Host p : membership)
                sendMessage(new AcceptOkMessage(instance, np, v), p);
        }
    }

    private void uponAcceptOk(AcceptOkMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        int instance = msg.getInstance();
        int n = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);
        if (state == null) {
            state = new PaxosState(n, n, v);
            instances.put(instance, state);
        }

        if (n > state.getNa()) {
            state.setNa(n);
            state.setVa(v);
            state.resetAcceptQuorum();
        } else if (n < state.getNa())
            return;
        state.updateAcceptQuorum(from);
        if (canDecide(state, instance))
            decide(state, instance);

    }

    private void uponReject(RejectMessage msg, Host from, short sourceProto, int channelId) {
        cancelTimer(instances.get(msg.getInstance()).getQuorumTimerID());
    }

    private void uponJoined(JoinedNotification notification, short sourceProto) {
        membership.addAll(notification.getMembership());
    }


    private void uponExecuted(ExecutedNotification notification, short sourceProto) {
        lastExecutedInstance = notification.getInstance();
        PaxosState state = instances.get(lastExecutedInstance + 1);
        if (state != null && canDecide(state, lastExecutedInstance + 1))
            decide(state, lastExecutedInstance + 1);
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
        if (!state.isDecided()) {
            state.resetPrepareQuorum();
            state.resetAcceptQuorum();
            sendPrepares(instance, state.getNp() + membership.size() + n, state);
        }
    }
}
