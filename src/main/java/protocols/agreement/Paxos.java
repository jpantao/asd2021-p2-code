package protocols.agreement;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.AcceptMessage;
import protocols.agreement.messages.AcceptOkMessage;
import protocols.agreement.messages.PrepareMessage;
import protocols.agreement.messages.PrepareOkMessage;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.timers.PrepareTimer;
import protocols.agreement.timers.QuorumTimer;
import protocols.agreement.utils.PaxosState;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class Paxos extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Paxos.class);

    //Protocol information, to register in babel
    public static final short PROTOCOL_ID = 110;
    public static final String PROTOCOL_NAME = "Paxos";

    private final Host self; //My own address/port

    private final int channelId; //Id of the created channel

    private final Map<Integer, PaxosState> instances;
    private Set<Host> membership;
    private int n;
    private int biggest_n;
    private final int prepareTimeout;
    private final int acceptTimeout;
    private int quorumSize;


    public Paxos(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.self = self;
        this.n = Integer.parseInt(props.getProperty("n"));
        this.prepareTimeout = Integer.parseInt(props.getProperty("prepare_timeout"));
        this.acceptTimeout = Integer.parseInt(props.getProperty("accept_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();
        this.quorumSize = 0;

        //Create a properties object to setup channel-specific properties. See the channel description for more details.
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("address")); //The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("port")); //The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); //Heartbeats interval for established connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); //Time passed without heartbeats until closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); //TCP connect timeout
        channelId = createChannel(TCPChannel.NAME, channelProps); //Create the channel with the given properties

        /*---------------------- Register Request Handlers ------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponPropose);

        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(channelId, PrepareMessage.MSG_ID, PrepareMessage.serializer);
        registerMessageSerializer(channelId, PrepareOkMessage.MSG_ID, PrepareOkMessage.serializer);
        registerMessageSerializer(channelId, AcceptMessage.MSG_ID, AcceptMessage.serializer);
        registerMessageSerializer(channelId, AcceptOkMessage.MSG_ID, AcceptOkMessage.serializer);

        /*---------------------- Register Message Handlers ------------------------- */
        registerMessageHandler(channelId, PrepareMessage.MSG_ID, this::uponPrepare, this::uponMsgFail);
        registerMessageHandler(channelId, PrepareOkMessage.MSG_ID, this::uponPrepareOk, this::uponMsgFail);
        registerMessageHandler(channelId, AcceptMessage.MSG_ID, this::uponAccept, this::uponMsgFail);
        registerMessageHandler(channelId, AcceptOkMessage.MSG_ID, this::uponAcceptOk, this::uponMsgFail);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);

        /*---------------------- Register Timer Handlers --------------------------- */
        //registerTimerHandler(PrepareTimer.TIMER_ID, this::uponPrepareTimeout);
        //registerTimerHandler(AcceptTimer.TIMER_ID, this::uponAcceptTimeout);
        registerTimerHandler(QuorumTimer.TIMER_ID, this::uponQuorumTimer);

    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }

    /*--------------------------------- Requests ----------------------------------- */
    private void uponPropose(ProposeRequest request, short sourceProto) {
        propose(n,request.getInstance(), request.getOperation());
    }

    private void propose(int np,int instance, byte[] v) {
        PaxosState state = instances.get(instance);
        if(state==null) {
            state = new PaxosState(np, v);
            instances.put(instance,state);
        }
        for (Host p : membership)
            sendMessage(new PrepareMessage(instance, np, v), p);
        long quorumTimer = setupTimer(new QuorumTimer(instance), prepareTimeout);
        state.setQuorumTimerID(quorumTimer);
    }


    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);
        if (state == null) {
            state = new PaxosState(np, v);
            instances.put(instance, state);
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
        } else if (np > state.getNp()) {
            state.setNp(msg.getN());
            sendMessage(new PrepareOkMessage(instance, state.getNa(), state.getVa()), from);
        }
    }

    private void uponPrepareOk(PrepareOkMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        PaxosState state = instances.get(instance);
        int naReceived = msg.getNa();
        byte[] vaReceived = msg.getVa();
        int highestNa = state.getHighestNa();
        if(naReceived > highestNa) {
            state.setHighestNa(naReceived);
            state.setHighestVa(vaReceived);
        }
        state.updatePrepareQuorum();
        if (state.getPrepareQuorum() >= quorumSize) {
            cancelTimer(state.getQuorumTimerID());
            int np = state.getNp();
            byte[] v = state.getHighestVa();
            for (Host p : membership)
                sendMessage(new AcceptMessage(instance, np, v), p);
            long quorumTimer = setupTimer(new QuorumTimer(instance), prepareTimeout);
            state.setQuorumTimerID(quorumTimer);
        }
    }

    private void uponAccept(AcceptMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        int np = msg.getN();
        byte[] v = msg.getV();
        PaxosState state = instances.get(instance);
        if (state == null) {
            instances.put(instance, new PaxosState(np, np, v));
            for (Host p : membership)
                sendMessage(new AcceptOkMessage(instance, np, v), p);
        } else if (np >= state.getNp()) {
            state.setNa(np);
            state.setVa(v);
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
            instances.put(instance,state);
        } else if(n > state.getNa()) {
            state.setNa(n);
            state.setVa(v);
            state.resetAcceptQuorum();
        }
        state.updateAcceptQuorum();
        if (!state.accepted() && state.getAcceptQuorum() >= quorumSize) {
            state.accept();
            triggerNotification(new DecidedNotification(instance, v));
        }
    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto,
                             Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    private void uponJoinedNotification(JoinedNotification notification, short sourceProto) {
        membership.addAll(notification.getMembership());
        quorumSize = membership.size() / 2 + 1;
    }

    /* -------------------------------- Timers ------------------------------------- */

    private void uponQuorumTimer(QuorumTimer quorumTimer, long timerID) {
        int instance = quorumTimer.getInstance();
        PaxosState state = instances.get(instance);
        if (!state.accepted())
            propose(state.getNp()+membership.size(),instance, state.getVa());
    }
}
