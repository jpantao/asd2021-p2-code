package protocols.agreement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.LeaderElectedNotification;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.timers.RoundTimer;
import protocols.statemachine.notifications.ExecutedNotification;
import protocols.statemachine.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.statemachine.notifications.ChannelReadyNotification;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class MultiPaxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public static final short PROTOCOL_ID = 120;
    public static final String PROTOCOL_NAME = "MultiPaxos";

    private Map<Integer, Instance> instances;
    private Set<Host> membership;
    private final int n;
    private final int roundTimeout;
    private long roundTimer;
    private int executed;
    private Host leader;
    private int leaderN;
    private Host self;
    private byte[] vDecided;
    private int nDecided;
    private List<byte[]> futureValues;


    public MultiPaxos(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.n = Integer.parseInt(props.getProperty("n"));
        this.roundTimeout = Integer.parseInt(props.getProperty("round_timeout"));
        this.leader = null;
        this.leaderN = n;
        this.vDecided = null;
        this.nDecided = -1;
        this.futureValues = new LinkedList<>();


        /*---------------------- Register Timer Handlers --------------------------- */
        //registerTimerHandler(LeaderTimer.TIMER_ID, this::uponLeaderTimeout);
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
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
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
        registerMessageSerializer(cId, MPPrepareOkMessage.MSG_ID, MPPrepareOkMessage.serializer);
        registerMessageSerializer(cId, AcceptMessage.MSG_ID, AcceptMessage.serializer);
        registerMessageSerializer(cId, AcceptOkMessage.MSG_ID, AcceptOkMessage.serializer);
        /*---------------------- Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, PrepareMessage.MSG_ID, this::uponPrepare);
            registerMessageHandler(cId, MPPrepareOkMessage.MSG_ID, this::uponPrepareOk);
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

        if (instance == null || instance.lna == null)
            return;

        if (instance.decision == null
                && instance.lQuorum.size() > membership.size() / 2) {
            instance.decision = instance.lva;
            triggerNotification(new DecidedNotification(executed + 1, instance.decision));
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
            if (leader != null && leader.equals(self))
                sendMessage(new AcceptMessage(request.getInstance(), instance.pn, instance.pv), acceptor);
            else
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
            leader = from;
            triggerNotification(new LeaderElectedNotification(from, msg.getInstance()));
            instance.anp = msg.getN();

            //Leader behind
            List<byte[]> futureValues = new LinkedList<>();
            int inst = executed - msg.getInstance();
            for (int i = 0; i < inst; i++)
                futureValues.add(instances.get(i).decision);

            logger.debug("Sending: {} to {}", new MPPrepareOkMessage(msg.getInstance(), instance.anp, instance.ana, instance.ava, futureValues), from);
            sendMessage(new MPPrepareOkMessage(msg.getInstance(), instance.anp, instance.ana, instance.ava, futureValues), from);
        }
    }

    private void uponPrepareOk(MPPrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(), k -> new Instance());

        if (!msg.getFutureValues().isEmpty()) {
            for (int i = futureValues.size(); i < msg.getFutureValues().size(); i++)
                futureValues.add(msg.getFutureValues().get(i));
        }

        if (msg.getN() != instance.pn)
            return;

        instance.pQuorum.add(msg);
        if (!instance.lockedIn && instance.pQuorum.size() > membership.size() / 2) {
            instance.lockedIn = true;
            if (!futureValues.isEmpty()) {
                instance.pv = futureValues.get(0);
                for (int i = 0; i < msg.getFutureValues().size(); i++) {
                    Instance nextInstance = instances.computeIfAbsent(msg.getInstance() + i, k -> new Instance());
                    nextInstance.initProposer(instance.pn, futureValues.get(i));
                    for (Host acceptor : membership) {
                        logger.debug("Sending: {} to {}", new AcceptMessage(msg.getInstance() + i, nextInstance.pn, nextInstance.pv), acceptor);
                        sendMessage(new AcceptMessage(msg.getInstance() + i, nextInstance.pn, nextInstance.pv), acceptor);
                    }
                }
            }

            Optional<PrepareOkMessage> op = instance.pQuorum.stream().max(Comparator.comparingInt(PrepareOkMessage::getNa));

            if (op.get().getVa() != null) {
                instance.pv = op.get().getVa();
            }


            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new AcceptMessage(msg.getInstance() + futureValues.size(), instance.pn, instance.pv), acceptor);
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

        if (executed < msg.getInstance() - 1)
            return;
        if (instance.decision == null && instance.lQuorum.size() > membership.size() / 2) {
            instance.decision = instance.lva;
            cancelTimer(roundTimer);
            triggerNotification(new DecidedNotification(msg.getInstance(), instance.decision));
        }
    }

    /* -------------------------------- Timers ------------------------------------- */

    private void uponRoundTimeout(RoundTimer timer, long timerID) {
        Instance instance = instances.get(timer.getInstance());
        if (leader == null) {
            //getNextN
            instance.pn += membership.size(); //TODO: can generate conflicts and is unfair
            instance.pQuorum.clear();
            instance.lockedIn = false;
            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new PrepareMessage(timer.getInstance(), instance.pn), acceptor);
                sendMessage(new PrepareMessage(timer.getInstance(), instance.pn), acceptor);
            }
            roundTimer = setupTimer(new RoundTimer(timer.getInstance()), roundTimeout);
        } else if (leader.equals(self)) {
            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new AcceptMessage(timer.getInstance(), instance.pn, instance.pv), acceptor);
                sendMessage(new AcceptMessage(timer.getInstance(), instance.pn, instance.pv), acceptor);
            }
            roundTimer = setupTimer(new RoundTimer(timer.getInstance()), roundTimeout);
        }
    }


    /*--------------------------------- Messages ----------------------------------- */
    /*
    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (instance.anp == null)
            instance.initAcceptor();

        if (msg.getN() > instance.anp) {
            instance.anp = msg.getN();
            if (leaderN < msg.getN()) {
                leader = from;
                leaderN = msg.getN();
                triggerNotification(new LeaderElectedNotification(from, msg.getInstance()));
            }
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
        if (leaderN == instance.pn && !instance.lockedIn && instance.pQuorum.size() > membership.size() / 2) {
            Optional<PrepareOkMessage> op = instance.pQuorum.stream()
                    .max(Comparator.comparingInt(PrepareOkMessage::getNa));
            if (op.get().getVa() != null)
                instance.pv = op.get().getVa();
            instance.lockedIn = true;
            leader = self;
            triggerNotification(new LeaderElectedNotification(self, msg.getInstance()));

            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new AcceptMessage(msg.getInstance(), instance.pn, instance.pv), acceptor);
                sendMessage(new MPAcceptMessage(msg.getInstance(), instance.pn, instance.pv, -1, null), acceptor);
            }
        }
    }

    private void uponAccept(MPAcceptMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received: {} from {}", msg, from);
        Instance instance = instances.computeIfAbsent(msg.getInstance(),
                k -> new Instance());
        if (instance.anp == null)
            instance.initAcceptor();

        if (msg.getN() >= instance.anp) {
            if (msg.getLastN() > 0 && msg.getLastV() != null && self != from && executed == msg.getInstance() - 2) {
                Instance lastInstance = instances.get(msg.getInstance() - 1);
                lastInstance.initLearner();
                lastInstance.lna = msg.getLastN();
                lastInstance.lva = msg.getLastV();
                lastInstance.decision = lastInstance.lva;
                triggerNotification(new DecidedNotification(msg.getInstance() - 1, lastInstance.decision));
                executed++;
            }
            instance.ana = msg.getN();
            instance.ava = msg.getV();

            logger.debug("Sending: {} to {}", new AcceptOkMessage(msg.getInstance(), instance.ana, instance.ava), from);
            sendMessage(new AcceptOkMessage(msg.getInstance(), instance.ana, instance.ava), from);
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

        if (executed < msg.getInstance() - 1)
            return;
        if (instance.decision == null && instance.lQuorum.size() > membership.size() / 2) {
            instance.decision = instance.lva;
            vDecided = instance.lva;
            nDecided = instance.lna;
            cancelTimer(roundTimer);
            triggerNotification(new DecidedNotification(msg.getInstance(), instance.decision));
            executed++;
        }
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        membership.remove(request.getReplica());
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        membership.add(request.getReplica());
    }


    /* -------------------------------- Timers ------------------------------------- */
    /*
    private void uponRoundTimeout(RoundTimer timer, long timerID) {
        Instance instance = instances.get(timer.getInstance());
        if (leader == null) {
            //getNextN
            instance.pn += membership.size(); //TODO: can generate conflicts and is unfair
            instance.pQuorum.clear();
            instance.lockedIn = false;
            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new PrepareMessage(timer.getInstance(), instance.pn), acceptor);
                sendMessage(new PrepareMessage(timer.getInstance(), instance.pn), acceptor);
            }
            roundTimer = setupTimer(new RoundTimer(timer.getInstance()), roundTimeout);
        } else if (leader.equals(self)) {
            for (Host acceptor : membership) {
                logger.debug("Sending: {} to {}", new MPAcceptMessage(timer.getInstance(), instance.pn, instance.pv, nDecided, vDecided), acceptor);
                sendMessage(new MPAcceptMessage(timer.getInstance(), instance.pn, instance.pv, nDecided, vDecided), acceptor);
            }
            roundTimer = setupTimer(new RoundTimer(timer.getInstance()), roundTimeout);
        }
    }
    */
}
