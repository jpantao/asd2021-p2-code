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
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class MultiPaxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public static final short PROTOCOL_ID = 110;
    public static final String PROTOCOL_NAME = "Paxos";

    private final Host self; //My own address/port

    private final Map<Integer, PaxosState> instances;
    private Set<Host> membership;
    private final int n;
    private final int quorumTimeout;
    private Host leader; //if true it believes he is a leader
    private byte[] opDecided;


    public MultiPaxos(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.self = self;
        this.n = Integer.parseInt(props.getProperty("n"));
        this.quorumTimeout = Integer.parseInt(props.getProperty("quorum_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();
        this.leader = null;
        this.opDecided = null;

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout

        //Id of the created channel
        int channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Request Handlers ------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponPropose);
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponRemoveReplica);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, PrepareMessage.MSG_ID, PrepareMessage.serializer);
        registerMessageSerializer(channelId, PrepareOkMessage.MSG_ID, PrepareOkMessage.serializer);
        registerMessageSerializer(channelId, MPAcceptMessage.MSG_ID, MPAcceptMessage.serializer);
        registerMessageSerializer(channelId, AcceptOkMessage.MSG_ID, AcceptOkMessage.serializer);
        registerMessageSerializer(channelId, RejectMessage.MSG_ID, RejectMessage.serializer);

        /*---------------------- Register Message Handlers ------------------------- */
        registerMessageHandler(channelId, PrepareMessage.MSG_ID, this::uponPrepare);
        registerMessageHandler(channelId, PrepareOkMessage.MSG_ID, this::uponPrepareOk);
        registerMessageHandler(channelId, MPAcceptMessage.MSG_ID, this::uponAccept);
        registerMessageHandler(channelId, AcceptOkMessage.MSG_ID, this::uponAcceptOk);
        registerMessageHandler(channelId, RejectMessage.MSG_ID, this::uponRejectMessage);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);

        /*---------------------- Register Timer Handlers --------------------------- */
        registerTimerHandler(LeaderTimer.TIMER_ID,this::uponLeaderTimeout);
        registerTimerHandler(QuorumTimer.TIMER_ID, this::uponQuorumTimeout);

    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {
    }

    private void uponPropose(ProposeRequest request, short sourceProto) {
        propose(request.getInstance(),n,new PaxosState(n,request.getOperation()));
    }

    private void propose(int instance, int np, PaxosState state) {
        state.setNp(np);
        state.updatePrepareQuorum(self);
        for (Host p : state.getMembership()) {
            if(!p.equals(self)) {
                if (!leader.equals(self))
                    sendMessage(new PrepareMessage(instance, np), p);
                else
                    sendMessage(new MPAcceptMessage(instance,np,opDecided,state.getVa()), p);
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
        if (state == null) {
            leader = from;
            state = new PaxosState(np);
            instances.put(instance, state);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
            triggerNotification(new LeaderElectedNotification(leader));
        } else if (np > state.getNp()) {
            if(instance > 0)
                cancelTimer(instances.get(instance-1).getLeaderTimerID());
            leader = from;
            state.setNp(np);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
            triggerNotification(new LeaderElectedNotification(leader));
        } else
            sendMessage(new RejectMessage(instance),from);
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
                    sendMessage(new MPAcceptMessage(instance, np,null,v), p);
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
        if(opDecided != null)
            triggerNotification(new DecidedNotification(np,opDecided));
        if(state == null) {
            leader = from;
            state = new PaxosState(np,newOp);
            instances.put(instance,state);
        }
        if(leader.equals(from)) {
            if (np >= state.getNp()) {
                if(instance > 0)
                    cancelTimer(instances.get(instance-1).getLeaderTimerID());
                state.setNa(np);
                state.setVa(newOp);
                sendMessage(new AcceptOkMessage(instance,np,newOp),from);
                state.setQuorumTimerID(setupTimer(new LeaderTimer(instance),quorumTimeout));
            }
        } else
            sendMessage(new RejectMessage(instance),from);
    }

    private void uponAcceptOk(AcceptOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);
        state.updateAcceptQuorum(from);
        if (!state.accepted() && state.hasAcceptQuorum()) {
            cancelTimer(state.getQuorumTimerID());
            state.accept();
            triggerNotification(new DecidedNotification(instance, v));
            opDecided = v;
        }
    }

    private void uponRejectMessage(RejectMessage msg, Host from, short sourceProto, int channelId) {
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
            propose(instance, state.getNp() + membership.size(), state);
    }

    private void uponQuorumTimeout(QuorumTimer quorumTimer, long timerID) {
        timeout(quorumTimer.getInstance());
    }


}
