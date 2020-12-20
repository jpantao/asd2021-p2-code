package protocols.agreement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.timers.RoundTimer;
import protocols.statemachine.notifications.ExecutedNotification;
import protocols.statemachine.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.statemachine.notifications.ChannelReadyNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

public class Paxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public static final short PROTOCOL_ID = 110;
    public static final String PROTOCOL_NAME = "Paxos";

    private Map<Integer, Instance> instances;
    private Set<Host> membership;
    private final int n;
    private final int roundTimeout;
    private long roundTimer;
    private int executed;

    public Paxos(Properties props) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.n = Integer.parseInt(props.getProperty("n"));
        this.roundTimeout = Integer.parseInt(props.getProperty("round_timeout"));

        /*---------------------- Register Timer Handlers --------------------------- */
        registerTimerHandler(RoundTimer.TIMER_ID, this::uponRoundTimeout);

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
        //My own address/port
        Host self = notification.getMyself();
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
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }
    }

    /*--------------------------------- Notifications ------------------------------ */
    private void uponJoined(JoinedNotification notification, short sourceProto) {
        membership = new HashSet<>(notification.getMembership());
        executed = notification.getJoinInstance() - 1;
        instances = new HashMap<>();
    }

    private void uponExecuted(ExecutedNotification notification, short sourceProto) {
        executed = notification.getInstance();
        Instance instance = instances.get(executed + 1);

        if(instance == null || instance.lna == null)
            return;

        if (instance.decision == null
                && instance.lQuorum.size() > membership.size() / 2) {
            instance.decision = instance.lva;
            triggerNotification(new DecidedNotification(executed+1, instance.decision));
        }
    }

    /*--------------------------------- Requests ----------------------------------- */

    private void uponPropose(ProposeRequest request, short sourceProto) {
        logger.debug("Propose: {}  - {}", request.getInstance(), Arrays.hashCode(request.getOperation()));
        Instance instance = instances.computeIfAbsent(request.getInstance(),
                k -> new Instance());
        if (instance.decision != null)
            return; // already decided
        if (instance.pn == null)
            instance.initProposer(n, request.getOperation());

        for (Host acceptor : membership) {
            logger.debug("Sending: {} to {}", new PrepareMessage(request.getInstance(), instance.pn), acceptor);
            sendMessage(new PrepareMessage(request.getInstance(), instance.pn), acceptor);
        }

        roundTimer = setupTimer(new RoundTimer(request.getInstance()), roundTimeout);
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        membership.remove(request.getReplica());
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        membership.add(request.getReplica());
    }

    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (instance.anp == null)
            instance.initAcceptor();

        if (msg.getN() > instance.anp) {
            instance.anp = msg.getN();
            logger.debug("Sending: {} to {}", new PrepareOkMessage(msg.getInstance(), instance.anp, instance.ana, instance.ava), from);
            sendMessage(new PrepareOkMessage(msg.getInstance(), instance.anp, instance.ana, instance.ava), from);
        }
    }

    private void uponPrepareOk(PrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (msg.getN() != instance.pn)
            return;

        instance.pQuorum.add(msg);
        if (!instance.lockedIn
                && instance.pQuorum.size() > membership.size() / 2) {
            Optional<PrepareOkMessage> op = instance.pQuorum.stream()
                    .max(Comparator.comparingInt(PrepareOkMessage::getNa));
            if(op.get().getVa() != null)
                instance.pv = op.get().getVa();
            instance.lockedIn = true;


            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new AcceptMessage(msg.getInstance(), instance.pn, instance.pv), acceptor);
                sendMessage(new AcceptMessage(msg.getInstance(), instance.pn, instance.pv),
                        acceptor);
            }
        }
    }

    private void uponAccept(AcceptMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (instance.anp == null)
            instance.initAcceptor();

        if (msg.getN() >= instance.anp) {
            instance.ana = msg.getN();
            instance.ava = msg.getV();
            for (Host learner : membership) {
                logger.debug("Sending: {} to {}", new AcceptOkMessage(msg.getInstance(), instance.ana, instance.ava), learner);
                sendMessage(new AcceptOkMessage(msg.getInstance(), instance.ana, instance.ava),
                        learner);
            }
        }
    }

    private void uponAcceptOk(AcceptOkMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (instance.lna == null)
            instance.initLearner();

        if (msg.getN() > instance.lna) {
            instance.lna = msg.getN();
            instance.lva = msg.getV();
            instance.lQuorum.clear();
        } else if (msg.getN() < instance.lna)
            return;

        instance.lQuorum.add(msg);

        if(executed < msg.getInstance() -1)
            return;
        if (instance.decision == null
                && instance.lQuorum.size() > membership.size() / 2) {
            instance.decision = instance.lva;
            cancelTimer(roundTimer);
            triggerNotification(new DecidedNotification(msg.getInstance(), instance.decision));
        }
    }

    /* -------------------------------- Timers ------------------------------------- */

    private void uponRoundTimeout(RoundTimer timer, long timerID) {
        Instance instance = instances.get(timer.getInstance());
        //getNextN
        instance.pn += membership.size(); //TODO: can generate conflicts and is unfair
        instance.pQuorum.clear();
        instance.lockedIn = false;
        for (Host acceptor : membership)
            sendMessage(new PrepareMessage(timer.getInstance(), instance.pn), acceptor);
        roundTimer = setupTimer(new RoundTimer(timer.getInstance()), roundTimeout);
    }
}
