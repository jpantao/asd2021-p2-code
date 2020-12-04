package protocols.agreement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.*;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.notifications.LeaderElectedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.agreement.timers.LeaderTimer;
import protocols.agreement.timers.QuorumTimer;
import protocols.agreement.utils.PaxosState;
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

    private Host self; //My own address/port

    private final Map<Integer, PaxosState> instances;
    private final Set<Host> membership;
    private final int n;
    private final int quorumTimeout;
    private Host leader; //if true it believes he is a leader
    private byte[] opDecided;
    private int npDecided;


    public MultiPaxos(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.n = Integer.parseInt(props.getProperty("n"));
        this.quorumTimeout = Integer.parseInt(props.getProperty("quorum_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();
        this.leader = null;
        this.opDecided = null;
        this.npDecided = n;

        /*---------------------- Register Timer Handlers --------------------------- */
        registerTimerHandler(LeaderTimer.TIMER_ID, this::uponLeaderTimeout);
        registerTimerHandler(QuorumTimer.TIMER_ID, this::uponQuorumTimeout);

        /*---------------------- Register Request Handlers ------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponPropose);
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponRemoveReplica);


        /*---------------------- Register Notification Handlers -------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);
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
        registerMessageSerializer(cId, PrepareOkMessage.MSG_ID, PrepareOkMessage.serializer);
        registerMessageSerializer(cId, MPAcceptMessage.MSG_ID, MPAcceptMessage.serializer);
        registerMessageSerializer(cId, AcceptOkMessage.MSG_ID, AcceptOkMessage.serializer);
        registerMessageSerializer(cId, RejectMessage.MSG_ID, RejectMessage.serializer);

        /*---------------------- Register Message Handlers -------------------------- */
        try {
            registerMessageHandler(cId, PrepareMessage.MSG_ID, this::uponPrepare);
            registerMessageHandler(cId, PrepareOkMessage.MSG_ID, this::uponPrepareOk);
            registerMessageHandler(cId, MPAcceptMessage.MSG_ID, this::uponAccept);
            registerMessageHandler(cId, AcceptOkMessage.MSG_ID, this::uponAcceptOk);
            registerMessageHandler(cId, RejectMessage.MSG_ID, this::uponReject);
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }
    }

    /*--------------------------------- Requests ----------------------------------- */
    private void uponPropose(ProposeRequest request, short sourceProto) {
        PaxosState state = new PaxosState(npDecided, request.getOperation());
        int instance = request.getInstance();
        instances.put(instance, state);
        propose(instance, npDecided, state);
    }

    private void propose(int instance, int np, PaxosState state) {
        state.setNp(np);
        state.updatePrepareQuorum(self);
        for (Host p : state.getMembership()) {
            if (!p.equals(self)) {
                if (!leader.equals(self))
                    sendMessage(new PrepareMessage(instance, np), p);
                else
                    sendMessage(new MPAcceptMessage(instance, np, opDecided, state.getVa()), p);
            }
        }
        long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
        state.setQuorumTimerID(quorumTimer);
    }

    /*--------------------------------- Messages ----------------------------------- */
    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        PaxosState state = instances.get(instance);
        long leaderTimer;
        if (state == null) {
            leader = from;
            state = new PaxosState(np);
            instances.put(instance, state);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
            leaderTimer = setupTimer(new LeaderTimer(instance), quorumTimeout);
            state.setLeaderTimerID(leaderTimer);
            triggerNotification(new LeaderElectedNotification(leader));
        } else if (np > state.getNp()) {
            if (state.getLeaderTimerID() > 0)
                cancelTimer(state.getLeaderTimerID());
            PaxosState previousState = instances.get(instance - 1);
            long previousLeaderTimerID = previousState.getLeaderTimerID();
            if (previousLeaderTimerID > 0) {
                cancelTimer(previousLeaderTimerID);
                previousState.setLeaderTimerID(-1);
            }
            leader = from;
            state.setNp(np);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
            leaderTimer = setupTimer(new LeaderTimer(instance), quorumTimeout);
            state.setLeaderTimerID(leaderTimer);
            triggerNotification(new LeaderElectedNotification(leader));
        } else
            sendMessage(new RejectMessage(instance), from);
    }

    private void uponPrepareOk(PrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        PaxosState state = instances.get(instance);
        state.updatePrepareQuorum(from);
        int naReceived = msg.getNa();
        byte[] vaReceived = msg.getVa();
        int highestNa = state.getHighestNa();
        if (naReceived > highestNa) {
            state.setHighestNa(naReceived);
            state.setHighestVa(vaReceived);
        }
        if (state.hasPrepareQuorum()) {
            leader = self;
            triggerNotification(new LeaderElectedNotification(self));
            state.updateAcceptQuorum(self);
            cancelTimer(state.getQuorumTimerID());
            int np = state.getNp();
            byte[] v = state.getHighestVa();
            for (Host p : state.getMembership()) {
                if (!p.equals(self))
                    sendMessage(new MPAcceptMessage(instance, np, null, v), p);
            }
            long quorumTimer = setupTimer(new QuorumTimer(instance), quorumTimeout);
            state.setQuorumTimerID(quorumTimer);
        }
    }

    private void uponAccept(MPAcceptMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        byte[] opDecided = msg.getOpDecided();
        byte[] newOp = msg.getNewOp();
        PaxosState state = instances.get(instance);
        if (opDecided != null)
            triggerNotification(new DecidedNotification(np, opDecided));
        if (state == null) {
            leader = from;
            state = new PaxosState(np, newOp);
            instances.put(instance, state);
        }
        if (np >= state.getNp()) {
            if (state.getLeaderTimerID() > 0)
                cancelTimer(state.getLeaderTimerID());
            PaxosState previousState = instances.get(instance - 1);
            long previousLeaderTimerID = previousState.getLeaderTimerID();
            if (previousLeaderTimerID > 0) {
                cancelTimer(previousLeaderTimerID);
                previousState.setLeaderTimerID(-1);
            }
            leader = from;
            triggerNotification(new LeaderElectedNotification(from));
            long leaderTimer = setupTimer(new LeaderTimer(instance), quorumTimeout);
            state.setLeaderTimerID(leaderTimer);

            state.setNa(np);
            state.setVa(newOp);
            sendMessage(new AcceptOkMessage(instance, np, newOp), from);
        } else
            sendMessage(new RejectMessage(instance), from);
    }

    private void uponAcceptOk(AcceptOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        byte[] v = msg.getV();
        int n = msg.getN();
        PaxosState state = instances.get(instance);

        if (n > state.getNa()) {
            state.setNa(n);
            state.setVa(v);
            state.resetAcceptQuorum();
        }

        state.updateAcceptQuorum(from);
        if (!state.accepted() && state.hasAcceptQuorum()) {
            cancelTimer(state.getQuorumTimerID());
            state.accept();
            triggerNotification(new DecidedNotification(instance, v));
            opDecided = v;
            npDecided = n;

        }
    }

    private void uponReject(RejectMessage msg, Host from, short sourceProto, int channelId) {
        leader = null;
        cancelTimer(instances.get(msg.getInstance()).getQuorumTimerID());
    }

    private void uponJoinedNotification(JoinedNotification notification, short sourceProto) {
        membership.addAll(notification.getMembership());
    }

    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        membership.remove(request.getReplica());
    }

    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        membership.add(request.getReplica());
    }

    /* -------------------------------- Timers ------------------------------------- */
    private void uponLeaderTimeout(LeaderTimer leaderTimer, long timerID) {
        leader = null;
        timeout(leaderTimer.getInstance());
    }

    private void timeout(int instance) {
        PaxosState state = instances.get(instance);
        if (!state.accepted())
            propose(instance, state.getNp() + membership.size() + n, state);
    }

    private void uponQuorumTimeout(QuorumTimer quorumTimer, long timerID) {
        timeout(quorumTimer.getInstance());
    }


}
