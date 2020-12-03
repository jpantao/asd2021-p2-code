package protocols.statemachine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.tuple.Pair;
import protocols.agreement.MultiPaxos;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.timers.NopTimer;
import protocols.statemachine.timers.RetryConnTimer;
import protocols.statemachine.utils.*;
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

    private State state;
    private List<Host> membership;

    private final Map<Host, Long> pendingConn;
    private final int connMaxRetries;
    private final int connRetryTimeout;

    private final int nopInterval;

    private final short agreement;
    private Host leader;

    private final Deque<byte[]> pendingInternal;
    private final Deque<byte[]> pendingOperations;
    private int nextInstance;


    public StateMachine(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.agreement = Short.parseShort(props.getProperty("agreement"));

        this.pendingConn = new HashMap<>();
        this.connMaxRetries = Integer.parseInt(props.getProperty("conn_retry_maxn"));
        this.connRetryTimeout = Integer.parseInt(props.getProperty("conn_retry_timeout"));
        this.nopInterval = Integer.parseInt(props.getProperty("nop_interval"));

        this.pendingInternal = new LinkedList<>();
        this.pendingOperations = new LinkedList<>();
        this.nextInstance = 0;


        String address = props.getProperty("address");
        String port = props.getProperty("p2p_port");

        logger.info("Listening on {}:{}", address, port);
        this.self = new Host(InetAddress.getByName(address), Integer.parseInt(port));

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
        registerTimerHandler(RetryConnTimer.TIMER_ID, this::uponRetryConnTimer);
        registerTimerHandler(NopTimer.TIMER_ID, this::uponNopTimer);

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

        setupPeriodicTimer(new NopTimer(), nopInterval, nopInterval);
    }

    /*--------------------------------- Requests -------------------------------------- */
    private void uponOrderRequest(OrderRequest request, short sourceProto) {
        logger.debug("Received request: " + request);

        newProposal(new AppOperation(request.getOpId(), request.getOperation()));
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponRetryConnTimer(RetryConnTimer timer, long timerId) {
        logger.trace("Retrying connection to {}, available attempts: {}", timer.getNode(), timer.getRetry());
        if (pendingConn.remove(timer.getNode()) == null)
            return; //should not happen (just a safeguard)

        if (timer.getRetry() > 0) {
            openConnection(timer.getNode());
            long t = setupTimer(new RetryConnTimer(timer.getNode(), timer.getRetry() - 1), connRetryTimeout);
            pendingConn.put(timer.getNode(), t);
        } else {
            RemReplica op = new RemReplica(timer.getNode());
            newProposalInternal(op);
        }
    }

    private void uponNopTimer(NopTimer timer, long timerId) {
        if(agreement == MultiPaxos.PROTOCOL_ID && self.equals(leader)) //TODO: set equals to multi paxos id (not 0)
            return;
        newProposalInternal(new Nop());
    }

    /*--------------------------------- Notifications --------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        logger.debug("Received notification: " + notification);


        if (Arrays.equals(pendingInternal.peek(), notification.getOperation()))
            pendingOperations.poll();

        if (Arrays.equals(pendingOperations.peek(), notification.getOperation()))
            pendingOperations.poll();


        Operation op = Operation.deserialize(notification.getOperation());

        if (op instanceof AddReplica)
            addReplica(notification.getInstance(), ((AddReplica)op).getNode());
        else if (op instanceof  RemReplica)
            removeReplica(notification.getInstance(), ((RemReplica)op).getNode());
        else if (op instanceof AppOperation)
            triggerNotification(new ExecuteNotification(((AppOperation)op).getOpId(), ((AppOperation)op).getOp()));


        proposeNext();
    }

    /*--------------------------------- Messages -------------------------------------- */
    //TODO: add replica and redirect messages

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.info("Connection to {} is up", event.getNode());

        if (pendingConn.containsKey(event.getNode()))
            cancelTimer(pendingConn.remove(event.getNode()));
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.debug("Connection to {} is down, cause {}", event.getNode(), event.getCause());
        long timer = setupTimer(new RetryConnTimer(event.getNode(), connMaxRetries), connRetryTimeout);
        pendingConn.put(event.getNode(), timer);
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        logger.debug("Connection to {} failed, cause: {}", event.getNode(), event.getCause());
    }


    private void uponInConnectionUp(InConnectionUp event, int channelId) {
        logger.trace("Connection from {} is up", event.getNode());
    }

    private void uponInConnectionDown(InConnectionDown event, int channelId) {
        logger.trace("Connection from {} is down, cause: {}", event.getNode(), event.getCause());
    }

    /*--------------------------------- Auxiliary ------------------------------------- */

    private void addReplica(int instance, Host node) {
        if (membership.contains(node))
            return;
        sendRequest(new AddReplicaRequest(instance, node), agreement);
        membership.add(node);
    }

    private void removeReplica(int instance,Host node) {
        if (!membership.contains(node))
            return;
        sendRequest(new RemoveReplicaRequest(instance, node), agreement);
        membership.remove(node);
    }

    private void newProposal(Operation op) {
        byte[] proposal = Operation.serialize(op);
        if (pendingInternal.isEmpty() && pendingOperations.isEmpty() && state == State.ACTIVE)
            sendRequest(new ProposeRequest(nextInstance++, proposal), agreement);
        pendingOperations.add(proposal);
    }

    private void newProposalInternal(Operation op) {
        byte[] proposal = Operation.serialize(op);
        if (pendingInternal.isEmpty() && pendingOperations.isEmpty() && state == State.ACTIVE)
            sendRequest(new ProposeRequest(nextInstance++, proposal), agreement);
        pendingInternal.add(proposal);
    }

    private void proposeNext() {
        if (state != State.ACTIVE)
            return;

        if (!pendingInternal.isEmpty())
            sendRequest(new ProposeRequest(nextInstance++, pendingInternal.peek()), agreement);
        else if (!pendingOperations.isEmpty())
            sendRequest(new ProposeRequest(nextInstance++, pendingOperations.peek()), agreement);
    }


}
