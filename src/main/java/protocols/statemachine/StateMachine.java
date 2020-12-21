package protocols.statemachine;

import protocols.agreement.MultiPaxos;
import protocols.statemachine.notifications.JoinedNotification;
import protocols.agreement.notifications.LeaderElectedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.ProposeRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import protocols.app.HashApp;
import protocols.app.requests.CurrentStateReply;
import protocols.app.requests.CurrentStateRequest;
import protocols.app.requests.InstallStateRequest;
import protocols.statemachine.messages.JoinMessage;
import protocols.statemachine.messages.JoinedMessage;
import protocols.statemachine.messages.RedirectMessage;
import protocols.statemachine.notifications.ExecuteNotification;
import protocols.statemachine.notifications.ExecutedNotification;
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

    private final Map<Host, Integer> joiningConn;
    private final Map<Integer, Host> waitingState;
    private final Map<Host, Long> retryingConn;
    private final int connMaxRetries;
    private final int connRetryTimeout;

    private final int nopTimeout;
    private long nopTimer;

    private final short agreement;
    private Host leader;

    private final Set<Operation> rediretOps;
    private final Deque<Operation> pendingOps;
    private int nextInstance;

    public StateMachine(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);

        this.agreement = Short.parseShort(props.getProperty("agreement"));

        this.joiningConn = new HashMap<>();
        this.waitingState = new HashMap<>();
        this.retryingConn = new HashMap<>();
        this.connMaxRetries = Integer.parseInt(props.getProperty("conn_retry_maxn"));
        this.connRetryTimeout = Integer.parseInt(props.getProperty("conn_retry_timeout"));
        this.nopTimeout = Integer.parseInt(props.getProperty("nop_timeout"));

        this.rediretOps = new HashSet<>();
        this.pendingOps = new LinkedList<>();
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

        /*--------------------- Reply Request Handlers -------------------------------- */
        registerReplyHandler(CurrentStateReply.REQUEST_ID, this::uponCurrentStateReply);

        /*--------------------- Register Timer Handlers ------------------------------- */
        registerTimerHandler(RetryConnTimer.TIMER_ID, this::uponRetryConnTimer);
        registerTimerHandler(NopTimer.TIMER_ID, this::uponNopTimer);

        /*--------------------- Register Notification Handlers ------------------------ */
        subscribeNotification(DecidedNotification.NOTIFICATION_ID, this::uponDecidedNotification);
        subscribeNotification(LeaderElectedNotification.NOTIFICATION_ID, this::uponLeaderElectedNotification);

        /*--------------------- Register Message Serializers -------------------------- */
        registerMessageSerializer(channelId, JoinMessage.MSG_ID, JoinMessage.serializer);
        registerMessageSerializer(channelId, JoinedMessage.MSG_ID, JoinedMessage.serializer);
        registerMessageSerializer(channelId, RedirectMessage.MSG_ID, RedirectMessage.serializer);

        /*--------------------- Register Message Handlers ----------------------------- */
        registerMessageHandler(channelId, JoinMessage.MSG_ID, this::uponJoin, this::uponMsgFail);
        registerMessageHandler(channelId, JoinedMessage.MSG_ID, this::uponJoined, this::uponMsgFail);
        registerMessageHandler(channelId, RedirectMessage.MSG_ID, this::uponRedirect, this::uponMsgFail);
    }

    @Override
    public void init(Properties props) {
        //Inform the agreement protocol about the channel we created in the constructor
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

        membership = new LinkedList<>(initialMembership);
        if (membership.contains(self)) {
            logger.info("Starting in ACTIVE as I am part of initial membership");
            state = State.ACTIVE;
            leader = self;
            //I'm part of the initial membership, so I'm assuming the system is bootstrapping
            membership.forEach(this::openConnection);
            triggerNotification(new JoinedNotification(membership, 0));
        } else {
            logger.info("Starting in JOINING as I am not part of initial membership");
            state = State.JOINING;
            //You have to do something to join the system and know which instance you joined
            //(and copy the state of that instance)
            membership.forEach(this::openConnection);
        }
    }

    /*--------------------------------- Timers ---------------------------------------- */
    private void uponRetryConnTimer(RetryConnTimer timer, long timerId) {
        logger.trace("Retrying connection to {}, available attempts: {}", timer.getNode(), timer.getRetry());
        if (retryingConn.remove(timer.getNode()) == null)
            return; //should not happen (just a safeguard)

        if (timer.getRetry() > 0) {
            openConnection(timer.getNode());
            long t = setupTimer(new RetryConnTimer(timer.getNode(), timer.getRetry() - 1),
                    connRetryTimeout);
            retryingConn.put(timer.getNode(), t);
        } else if (membership.contains(timer.getNode()))
            newProposal(new RemReplica(timer.getNode()));
    }

    private void uponNopTimer(NopTimer timer, long timerId) {
        if (!self.equals(leader))
            return; //should not happen just a safeguard
        if (pendingOps.isEmpty())
            propose(new Nop()); //Dont add to the queue and propose immediately
    }

    /*--------------------------------- Requests -------------------------------------- */
    private void uponOrderRequest(OrderRequest request, short sourceProto) {
        logger.debug("Received request: " + request);

        newProposal(new AppOperation(request.getOpId(), request.getOperation()));
    }

    /*--------------------------------- Replies --------------------------------------- */
    private void uponCurrentStateReply(CurrentStateReply request, short sourceProto) {
        logger.debug("Received request: " + request);

        waitingState.computeIfPresent(request.getInstance(), (instance, node) -> {
            sendMessage(new JoinedMessage(leader, instance, request.getState()), node);
            return null; //returning null removes the entry
        });
    }

    /*--------------------------------- Notifications --------------------------------- */
    private void uponDecidedNotification(DecidedNotification notification, short sourceProto) {
        //logger.debug("Received notification: " + notification);
        Operation op = Operation.deserialize(notification.getOperation());

        if (op.equals(pendingOps.peek()))
            pendingOps.poll();
        rediretOps.remove(op);

        if (op instanceof Nop)
            logger.debug("Instance: {} -> Decided Nop", notification.getInstance());
        else if (op instanceof AddReplica) {
            logger.debug("Instance: {} -> Decided AddReplica {}", notification.getInstance(), ((AddReplica) op).getNode());
            pendingOps.remove(op); //no need to propose this op again
            addReplica(notification.getInstance() + 1, ((AddReplica) op).getNode());
        } else if (op instanceof RemReplica) {
            logger.debug("Instance: {} -> Decided RemReplica {}", notification.getInstance(), ((RemReplica) op).getNode());
            pendingOps.remove(op); //no need to propose this op again
            removeReplica(notification.getInstance(), ((RemReplica) op).getNode());
        } else if (op instanceof AppOperation) {
            logger.debug("Instance: {} -> Decided AppOperation {}", notification.getInstance(), Arrays.hashCode(notification.getOperation()));
            triggerNotification(new ExecuteNotification(
                    ((AppOperation) op).getOpId(), ((AppOperation) op).getOp()));
        }

        assert nextInstance == notification.getInstance();
        nextInstance = notification.getInstance() + 1;
        triggerNotification(new ExecutedNotification(notification.getInstance()));

        proposeNext();
    }

    private void uponLeaderElectedNotification(LeaderElectedNotification notification, short sourceProto) {
        logger.debug("Received notification: " + notification);
        if(leader.equals(self) && notification.getLeader().equals(self))
            return; //I'm still the leader

        leader = notification.getLeader();
        // nextInstance = notification.getInstance() + 1;
        pendingOps.removeAll(rediretOps);
        rediretOps.clear();
    }

    /*--------------------------------- Messages -------------------------------------- */
    private void uponJoin(JoinMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received message: {}", msg);
        if (membership.contains(from))
            return;
        newProposal(new AddReplica(from));
    }

    private void uponJoined(JoinedMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received message: {}", msg);
        membership.add(self);

        nextInstance = msg.getInstance();
        sendRequest(new InstallStateRequest(msg.getState()), HashApp.PROTO_ID);
        triggerNotification(new JoinedNotification(membership, nextInstance));

        if (msg.getLeader() != null)
            leader = msg.getLeader();

        state = State.ACTIVE;
        proposeNext();
    }

    private void uponRedirect(RedirectMessage msg, Host from, short sourceProto, int channelId) {
        logger.debug("Received message: {}", msg);

        if(rediretOps.add(msg.getOperation()))
            newProposal(msg.getOperation());
    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

    /* --------------------------------- TCPChannel Events ---------------------------- */
    private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
        logger.debug("Connection to {} is up", event.getNode());

        //TODO optimization: could send a join message to only one replica
        if (state == State.JOINING) {
            logger.debug("Sending join request to {}", event.getNode());
            sendMessage(new JoinMessage(), event.getNode());
        } else if (state == State.ACTIVE)
            joiningConn.computeIfPresent(event.getNode(), (node, instance) -> {
                logger.debug("Node {} joining on instance {} -> waiting for state...", node, instance);
                waitingState.put(instance, node);
                sendRequest(new CurrentStateRequest(instance), HashApp.PROTO_ID);
                return null; //returning null removes the entry
            });

        retryingConn.computeIfPresent(event.getNode(), (node, timerId) -> {
            logger.debug("Stop attempts to connect to {}", node);
            cancelTimer(timerId); //stop attempts to connect
            return null; //returning null removes the entry
        });
    }

    private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
        logger.error("Connection to {} is down, cause {}", event.getNode(), event.getCause());

        retryingConn.computeIfAbsent(event.getNode(), k -> //start retying connection
                setupTimer(new RetryConnTimer(k, connMaxRetries), connRetryTimeout));
    }

    private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
        logger.error("Connection to {} failed, cause: {}", event.getNode(), event.getCause());

        retryingConn.computeIfAbsent(event.getNode(), k -> //start retying connection
                setupTimer(new RetryConnTimer(k, connMaxRetries), connRetryTimeout));
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
        logger.info("Instance: {} ->  Adding replica: {}", instance, node);
        joiningConn.put(node, instance);
        openConnection(node);
        membership.add(node);
        sendRequest(new AddReplicaRequest(instance, node), agreement); //request before establishing the connection?
    }

    private void removeReplica(int instance, Host node) {
        if (!membership.contains(node))
            return;
        logger.info("Instance: {} ->  Removing replica: {}", instance, node);
        sendRequest(new RemoveReplicaRequest(instance, node), agreement);
        membership.remove(node);
        closeConnection(node);
    }

    private void newProposal(Operation proposal) {
        if (pendingOps.isEmpty() && rediretOps.isEmpty() && state == State.ACTIVE)
            propose(proposal);
        pendingOps.add(proposal);
        resetNopTimerMP();
    }

    //If not used carefully you can be proposing more than one instance at a time
    private void proposeNext() {
        if (state != State.ACTIVE)
            return;
        if (!pendingOps.isEmpty())
            propose(pendingOps.peek());
    }

    private void propose(Operation op) {
        if (self.equals(leader)) {
            logger.trace("Sending to myself: inst-{} op -> {}", nextInstance, leader);
            sendRequest(new ProposeRequest(nextInstance, Operation.serialize(op)), agreement);
        } else {
            logger.trace("Sending to leader: op -> {} ", leader);
            sendMessage(new RedirectMessage(op), leader);
        }
    }

    private void resetNopTimerMP(){
        cancelTimer(nopTimer);
        if(agreement == MultiPaxos.PROTOCOL_ID && self.equals(leader))
            nopTimer = setupTimer(new NopTimer(), nopTimeout);
    }
}
