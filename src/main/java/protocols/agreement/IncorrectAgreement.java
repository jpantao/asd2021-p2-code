package protocols.agreement;

import protocols.agreement.messages.BroadcastMessage;
import protocols.agreement.notifications.JoinedNotification;
import protocols.agreement.requests.AddReplicaRequest;
import protocols.agreement.requests.RemoveReplicaRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.statemachine.notifications.ChannelReadyNotification;
import protocols.agreement.notifications.DecidedNotification;
import protocols.agreement.requests.ProposeRequest;

import java.io.IOException;
import java.util.*;

/**
 * This is NOT a correct agreement protocol (it is actually a VERY wrong one)
 * This is simply an example of things you can do, and can be used as a starting point.
 *
 * You are free to change/delete ANYTHING in this class, including its fields.
 * Do not assume that any logic implemented here is correct, think for yourself!
 */
public class IncorrectAgreement extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(IncorrectAgreement.class);

    //Protocol information, to register in babel
    public final static short PROTOCOL_ID = 100;
    public final static String PROTOCOL_NAME = "EmptyAgreement";

    private Host myself;
    private int joinedInstance;
    private List<Host> membership;

    public IncorrectAgreement(Properties props) throws IOException, HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        joinedInstance = -1; //-1 means we have not yet joined the system
        membership = null;

        /*--------------------- Register Timer Handlers ----------------------------- */

        /*--------------------- Register Request Handlers ----------------------------- */
        registerRequestHandler(ProposeRequest.REQUEST_ID, this::uponProposeRequest);
        registerRequestHandler(AddReplicaRequest.REQUEST_ID, this::uponAddReplica);
        registerRequestHandler(RemoveReplicaRequest.REQUEST_ID, this::uponRemoveReplica);

        /*--------------------- Register Notification Handlers ----------------------------- */
        subscribeNotification(ChannelReadyNotification.NOTIFICATION_ID, this::uponChannelCreated);
        subscribeNotification(JoinedNotification.NOTIFICATION_ID, this::uponJoinedNotification);
    }

    @Override
    public void init(Properties props) {
        //Nothing to do here, we just wait for events from the application or agreement
    }

    //Upon receiving the channelId from the membership, register our own callbacks and serializers
    private void uponChannelCreated(ChannelReadyNotification notification, short sourceProto) {
        int cId = notification.getChannelId();
        myself = notification.getMyself();
        logger.info("Channel {} created, I am {}", cId, myself);
        // Allows this protocol to receive events from this channel.
        registerSharedChannel(cId);
        /*---------------------- Register Message Serializers ---------------------- */
        registerMessageSerializer(cId, BroadcastMessage.MSG_ID, BroadcastMessage.serializer);
        /*---------------------- Register Message Handlers -------------------------- */
        try {
              registerMessageHandler(cId, BroadcastMessage.MSG_ID, this::uponBroadcastMessage, this::uponMsgFail);
        } catch (HandlerRegistrationException e) {
            throw new AssertionError("Error registering message handler.", e);
        }

    }

    private void uponBroadcastMessage(BroadcastMessage msg, Host host, short sourceProto, int channelId) {
        if(joinedInstance >= 0 ){
            //Obviously your agreement protocols will not decide things as soon as you receive the first message
            triggerNotification(new DecidedNotification(msg.getInstance(), msg.getOpId(), msg.getOp()));
        } else {
            //We have not yet received a JoinedNotification, but we are already receiving messages from the other
            //agreement instances, maybe we should do something with them...?
        }
    }

    private void uponJoinedNotification(JoinedNotification notification, short sourceProto) {
        //We joined the system and can now start doing things
        joinedInstance = notification.getJoinInstance();
        membership = new LinkedList<>(notification.getMembership());
        logger.info("Agreement starting at instance {},  membership: {}", joinedInstance, membership);
    }

    private void uponProposeRequest(ProposeRequest request, short sourceProto) {
        logger.debug("Received " + request);
        BroadcastMessage msg = new BroadcastMessage(request.getInstance(), request.getOpId(), request.getOperation());
        logger.debug("Sending to: " + membership);
        membership.forEach(h -> sendMessage(msg, h));
    }
    private void uponAddReplica(AddReplicaRequest request, short sourceProto) {
        logger.debug("Received " + request);
        //The AddReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.add(request.getReplica());
    }
    private void uponRemoveReplica(RemoveReplicaRequest request, short sourceProto) {
        logger.debug("Received " + request);
        //The RemoveReplicaRequest contains an "instance" field, which we ignore in this incorrect protocol.
        //You should probably take it into account while doing whatever you do here.
        membership.remove(request.getReplica());
    }

    private void uponMsgFail(ProtoMessage msg, Host host, short destProto, Throwable throwable, int channelId) {
        //If a message fails to be sent, for whatever reason, log the message and the reason
        logger.error("Message {} to {} failed, reason: {}", msg, host, throwable);
    }

}
