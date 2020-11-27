package protocols.agreement;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.agreement.messages.AcceptMessage;
import protocols.agreement.messages.AcceptedMessage;
import protocols.agreement.messages.PrepareMessage;
import protocols.agreement.messages.PromiseMessage;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.utils.PaxosState;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
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


    public Paxos(Properties props, Host self) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.self = self;

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
        registerMessageSerializer(channelId, PromiseMessage.MSG_ID, PromiseMessage.serializer);
        registerMessageSerializer(channelId, AcceptMessage.MSG_ID, AcceptMessage.serializer);
        registerMessageSerializer(channelId, AcceptedMessage.MSG_ID, AcceptedMessage.serializer);

        /*---------------------- Register Message Handlers ------------------------- */
        registerMessageHandler(channelId, PrepareMessage.MSG_ID, this::uponPrepare, this::uponMsgFail);
        registerMessageHandler(channelId, PromiseMessage.MSG_ID, this::uponPromise, this::uponMsgFail);
        registerMessageHandler(channelId, AcceptMessage.MSG_ID, this::uponAccept, this::uponMsgFail);
        registerMessageHandler(channelId, AcceptedMessage.MSG_ID, this::uponAccepted, this::uponMsgFail);

        /*---------------------- Register Timer Handlers --------------------------- */

    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {

    }

    /*--------------------------------- Requests ----------------------------------- */
    private void uponPropose(ProposeRequest request, short sourceProto) {
        //TODO: complete propose logic
    }

    /*--------------------------------- Messages ----------------------------------- */

    private void uponPrepare(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        PaxosState instance = instances.get(msg.getIns());

        if (msg.getN() > instance.getN()) {
            instance.setN(msg.getN());
            sendMessage(new PromiseMessage(msg.getIns(), msg.getN(), instance.getOpId(), instance.getOp()), from);
        }
        //TODO: else send NACK? (optimization)
    }

    private void uponPromise(PrepareMessage msg, Host from, short sourceProto, int channelId) {
        
    }

    private void uponAccept(PrepareMessage msg, Host from, short sourceProto, int channelId) {

    }

    private void uponAccepted(PrepareMessage msg, Host from, short sourceProto, int channelId) {

    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto,
                             Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* -------------------------------- Timers ------------------------------------- */

}
