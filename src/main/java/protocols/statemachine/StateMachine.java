package protocols.statemachine;

import org.apache.commons.lang3.tuple.Pair;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.statemachine.timers.RetryConnTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.requests.ProposeRequest;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.requests.OrderRequest;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This is NOT a fully functional StateMachine implementation.
 * This is simply an example of things you can do, and can be used as a starting point.
 * <p>
 * You are free to change/delete anything in this class, including its fields.
 * The only thing that you cannot change are the notifications/requests between the StateMachine and the APPLICATION
 * You can change the requests/notification between the StateMachine and AGREEMENT protocol, however make sure it is
 * coherent with the specification shown in the project description.
 * <p>
 * Do not assume that any logic implemented here is correct, think for yourself!
 */
public class StateMachine extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(StateMachine.class);

    private enum State {JOINING, ACTIVE}

    //Protocol information, to register in babel
    public static final String PROTOCOL_NAME = "StateMachine";
    public static final short PROTOCOL_ID = 200;

    private final Host self;     //My own address/port
    private final int channelId; //Id of the created channel

    private final Map<Host, Integer> pendingConn;
    private final int connMaxRetries;
    private final int connRetryTimeout;


    private final short agreement;
    //private Host leader;
    private State state;
    private List<Host> membership;
    private int nextInstance;
    private final Deque<byte[]> pendingOps;

    public StateMachine(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        nextInstance = 0;

        String address = props.getProperty("address");
        String port = props.getProperty("p2p_port");
        String agreement = props.getProperty("agreement");
        String conn_attempt = props.getProperty("conn_retry");
        String conn_timeout = props.getProperty("conn_retry_timeout");

        logger.info("Listening on {}:{}", address, port);
        this.self = new Host(InetAddress.getByName(address), Integer.parseInt(port));
        this.agreement = Short.parseShort(agreement);
        this.connMaxRetries = Integer.parseInt(conn_attempt);
        this.connRetryTimeout = Integer.parseInt(conn_timeout);
        this.pendingConn = new HashMap<>();
        this.pendingOps = new LinkedList<>();

        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, address);
        channelProps.setProperty(TCPChannel.PORT_KEY, port); //The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000");
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000");
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000");
        channelId = createChannel(TCPChannel.NAME, channelProps);

        /*-------------------- Register Channel Events -------------------------------- */
        registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
        registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
        registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
        registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
        registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(OrderRequest.REQUEST_ID, this::uponOrderRequest);

        /*--------------------- Register Timer Handlers ------------------------------- */


        /*--------------------- Register Notification Handlers ------------------------ */
        subscribeNotification(DecidedNotification.NOTIFICATION_ID, this::uponDecidedNotification);
    }

    @Override
    public void init(Properties props) {
        //Inform the state machine protocol about the channel we created in the constructor
        triggerNotification(new ChannelReadyNotification(channelId, self));

        String host = props.getProperty("initial_membership");
        String[] hosts = host.split(",");
        List<Host> initialMembership = new LinkedList<>();
        for (String s : hosts) {
            String[] hostElements = s.split(":");
            Host h;
            try {
                h = new Host(InetAddress.getByName(hostElements[0]), Integer.parseInt(hostElements[1]));
            } catch (UnknownHostException e) {
                throw new AssertionError("Error parsing initial_membership", e);
            }
            initialMembership.add(h);
        }

        if (initialMembership.contains(self)) {
            state = State.ACTIVE;
            logger.info("Starting in ACTIVE as I am part of initial membership");
            //I'm part of the initial membership, so I'm assuming the system is bootstrapping
            membership = new LinkedList<>(initialMembership);
            membership.forEach(this::openConnection);
            triggerNotification(new JoinedNotification(membership, 0));
        } else {
            state = State.JOINING;
            logger.info("Starting in JOINING as I am not part of initial membership");
            //You have to do something to join the system and know which instance you joined
            //(and copy the state of that instance)
        }

    }

    /*--------------------------------- Requests -------------------------------------- */
    private void uponOrderRequest(OrderRequest request, short sourceProto) {
        logger.debug("Received request: " + request);
        if (state == State.JOINING) {
            //TODO: Do something smart (like buffering the requests)
        } else if (state == State.ACTIVE) {
            //Also do something starter, we don't want an infinite number of instances active
            //Maybe you should modify what is it that you are proposing so that you remember that this
            //operation was issued by the application (and not an internal operation, check the uponDecidedNotification)

            byte[] op = serializeOp(request.getOpId(), request.getOperation());

            if (pendingOps.isEmpty())
                sendRequest(new ProposeRequest(nextInstance, op), agreement);

            pendingOps.add(op);
        }
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponRetryConnTimer(RetryConnTimer timer, long timerId) {
        openConnection(timer.getToConn());
    }

    /*--------------------------------- Notifications --------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        logger.debug("Received notification: " + notification);
        //Maybe we should make sure operations are executed in order?
        //You should be careful and check if this operation if an application operation (and send it up)
        //or if this is an operations that was executed by the state machine itself (in which case you should execute)

        pendingOps.poll();
        nextInstance++;

        Pair<UUID, byte[]> operation = deserializeOp(notification.getOperation());
        triggerNotification(new ExecuteNotification(operation.getLeft(), operation.getRight()));

        if (!pendingOps.isEmpty())
            sendRequest(new ProposeRequest(nextInstance, pendingOps.peek()), agreement);
    }

    /*--------------------------------- Messages -------------------------------------- */
    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.info("Connection to {} is up", event.getNode());
        //TODO: if not in initial membership add replica? or do this on add replica message
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.debug("Connection to {} is down, cause {}", event.getNode(), event.getCause());
        //TODO: retry conn
        //removeReplica(nextInstance, event.getNode());

    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        logger.debug("Connection to {} failed, cause: {}", event.getNode(), event.getCause());
        //Maybe we don't want to do this forever. At some point we assume he is no longer there.
        //Also, maybe wait a little bit before retrying, or else you'll be trying 1000s of times per second

        //TODO: retry conn here?
        //if (!membership.contains(event.getNode()) || !pendingConn.containsKey(event.getNode())) return;
        //pendingConn.computeIfPresent(event.getNode(), this::retryConn);

    }


    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    /*--------------------------------- Auxiliary ------------------------------------- */

    private byte[] serializeOp(UUID opId, byte[] op) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeLong(opId.getMostSignificantBits());
            dos.writeLong(opId.getLeastSignificantBits());
            dos.writeInt(op.length);
            dos.write(op);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            throw new AssertionError();
        }
    }

    private Pair<UUID, byte[]> deserializeOp(byte[] operation) {
        ByteArrayInputStream bis = new ByteArrayInputStream(operation);
        DataInputStream dis = new DataInputStream(bis);
        try {
            long mostSignificantBits = dis.readLong();
            long leastSignificantBits = dis.readLong();
            UUID opId = new UUID(mostSignificantBits, leastSignificantBits);
            byte[] op = new byte[dis.readInt()];
            return Pair.of(opId, op);
        } catch (IOException e) {
            e.printStackTrace();
            throw new AssertionError();
        }
    }

    private void removeReplica(int instance, Host toRm) {
        sendRequest(new RemoveReplicaRequest(instance, toRm), agreement);
        membership.remove(toRm);
    }

    private Integer retryConn(Host toConn, Integer retry) {
        if (retry == 0) return null;
        setupTimer(new RetryConnTimer(toConn), connRetryTimeout);
        return retry - 1;
    }

}
