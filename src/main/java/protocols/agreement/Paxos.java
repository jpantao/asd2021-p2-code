package protocols.agreement;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.AcceptMessage;
import protocols.agreement.messages.AcceptOkMessage;
import protocols.agreement.messages.PrepareMessage;
import protocols.agreement.messages.PrepareOkMessage;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.timers.AcceptTimer;
import protocols.agreement.timers.PrepareTimer;
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
    private final Set<Host> membership;
    private int n;
    private int biggest_n;
    private final int prepareTimeout;
    private final int acceptTimeout;


    public Paxos(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.self = self;
        this.n = Integer.parseInt(props.getProperty("n"));
        this.prepareTimeout = Integer.parseInt(props.getProperty("prepare_timeout"));
        this.acceptTimeout = Integer.parseInt(props.getProperty("accept_timeout"));
        this.membership = new HashSet<>();
        this.instances = new HashMap<>();


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

        /*---------------------- Register Timer Handlers --------------------------- */
        registerTimerHandler(PrepareTimer.TIMER_ID, this::uponPrepareTimeout);
        registerTimerHandler(AcceptTimer.TIMER_ID, this::uponAcceptTimeout);

    }


    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }

    /*--------------------------------- Requests ----------------------------------- */
    private void uponPropose(ProposeRequest request, short sourceProto) {
        //TODO: verify propose logic
        int instance = request.getInstance();
        instances.put(instance, new PaxosState(n, request.getOperation()));
        n += membership.size();
        for (Host p : membership)
            sendMessage(new PrepareMessage(instance, n), p);
        setupTimer(new PrepareTimer(instance), prepareTimeout);
    }

    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        int instance = msg.getInstance();
        PaxosState state = instances.get(instance);
        if (state == null) {
            state = instances.put(instance, new PaxosState(n));
        }
        if (msg.getN() > state.getN()){
            state.setN(msg.getN());
            sendMessage(new PrepareOkMessage(instance, ));
        }
        //TODO: else send NACK? (optimization)
    }

    private void uponPrepareOk(PrepareMessage msg, Host from, short sourceProto, int channelId) {

    }

    private void uponAccept(PrepareMessage msg, Host from, short sourceProto, int channelId) {

    }

    private void uponAcceptOk(PrepareMessage msg, Host from, short sourceProto, int channelId) {

    }

    private void uponAcceptTimeout(AcceptTimer acceptTimer, long timerId) {

    }


    private void uponPrepareTimeout(PrepareTimer prepareTimer, long timerId) {
    }


    private void uponMsgFail(ProtoMessage msg, Host host, short destProto,
                             Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* -------------------------------- Timers ------------------------------------- */

}
