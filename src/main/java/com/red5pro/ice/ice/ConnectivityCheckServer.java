/* See LICENSE.md for license information */
package com.red5pro.ice.ice;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.red5pro.ice.attribute.Attribute;
import com.red5pro.ice.attribute.AttributeFactory;
import com.red5pro.ice.attribute.ErrorCodeAttribute;
import com.red5pro.ice.attribute.IceControlAttribute;
import com.red5pro.ice.attribute.PriorityAttribute;
import com.red5pro.ice.attribute.UsernameAttribute;
import com.red5pro.ice.message.Message;
import com.red5pro.ice.message.MessageFactory;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import com.red5pro.ice.security.CredentialsAuthority;
import com.red5pro.ice.stack.RequestListener;
import com.red5pro.ice.stack.StunStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StunMessageEvent;

/**
 * The class that would be handling and responding to incoming connectivity checks.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
class ConnectivityCheckServer implements RequestListener, CredentialsAuthority {

    private static final Logger logger = LoggerFactory.getLogger(ConnectivityCheckServer.class);

    /**
     * The agent that created us.
     */
    private final Agent parentAgent;

    /**
     * The indicator which determines whether this ConnectivityCheckServer is currently started.
     */
    private AtomicBoolean started = new AtomicBoolean(false);

    /**
     * The StunStack  that we will use for connectivity checks.
     */
    private final StunStack stunStack;

    /**
     * A flag that determines whether we have received a STUN request or not.
     */
    private boolean alive;

    /**
     * Creates a new ConnectivityCheckServer setting parentAgent as the agent that will be used for retrieving
     * information such as user fragments for example.
     *
     * @param parentAgent the Agent that is creating this instance.
     */
    public ConnectivityCheckServer(Agent parentAgent) {
        this.parentAgent = parentAgent;
        stunStack = this.parentAgent.getStunStack();
        stunStack.getCredentialsManager().registerAuthority(this);
        //start();
    }

    /**
     * Returns a boolean value indicating whether we have received a STUN request or not.
     *
     * Note that this should NOT be taken as an indication that the negotiation has succeeded, it merely indicates that we have received ANY STUN
     * request, even invalid ones (e.g. with the wrong username or ufrag). It is completely unrelated/independent from the ICE spec and it's only meant to
     * be used for debugging purposes.
     *
     * @return a boolean value indicating whether we have received a STUN request or not.
     */
    boolean isAlive() {
        return alive;
    }

    /**
     * Handles the {@link Request} delivered in evt by possibly queuing a triggered check and sending a success or an error response
     * depending on how processing goes.
     *
     * @param evt the {@link StunMessageEvent} containing the {@link Request} that we need to process
     * @throws IllegalArgumentException if the request is malformed and the stack needs to reply with a 400 Bad Request response
     */
    public void processRequest(StunMessageEvent evt) throws IllegalArgumentException {
        if (logger.isDebugEnabled()) {
            logger.debug("Received request {}", evt);
        }
        alive = true;
        Request request = (Request) evt.getMessage();
        // Ignore incoming requests that are not meant for the local user. normally the stack will get rid of faulty user names but we could
        // still see messages not meant for this server if both peers are running on this same instance of the stack.
        UsernameAttribute uname = (UsernameAttribute) request.getAttribute(Attribute.Type.USERNAME);
        if (logger.isDebugEnabled()) {
            logger.debug("Username: {}", uname);
        }
        String username = new String(uname.getUsername());
        if (!checkLocalUserName(username)) {
            logger.debug("Username is not known: {}", username);
            return;
        }
        // Learn the peer reflexive candidate, even if we are going to send a role conflict error. This allows us to learn faster, and compensates
        // for a buggy peer that doesn't switch roles when it gets a role conflict error.
        long priority = extractPriority(request);
        // if we're not controlling and use candidate is false, set it anyway to work around an Edge bug
        boolean useCandidate = (request.getAttribute(Attribute.Type.USE_CANDIDATE) != null) || !parentAgent.isControlling();
        if (logger.isDebugEnabled()) {
            logger.debug("useCandidate: {}", useCandidate);
        }
        // caller gave us the entire username
        boolean wholeUserName = username.contains(":");
        String remoteUfrag = wholeUserName ? username.split(":")[0] : username;
        String localUFrag = wholeUserName ? username.split(":")[1] : null;
        if (logger.isTraceEnabled()) {
            logger.trace("localUfrag: {} remoteUfrag: {}", localUFrag, remoteUfrag);
        }
        // tell our address handler we saw a new remote address
        parentAgent.incomingCheckReceived(evt.getRemoteAddress(), evt.getLocalAddress(), priority, remoteUfrag, localUFrag, useCandidate);
        boolean controlling = (parentAgent.isControlling() && request.getAttribute(Attribute.Type.ICE_CONTROLLING) != null);
        boolean controlled = (!parentAgent.isControlling() && request.getAttribute(Attribute.Type.ICE_CONTROLLED) != null);
        if (logger.isTraceEnabled()) {
            logger.trace("controlling: {} controlled: {}", controlling, controlled);
        }
        //detect role conflicts
        if (controlling || controlled) {
            if (!repairRoleConflict(evt)) {
                logger.debug("Role conflict not repaired: {}", username);
                return;
            }
        }
        Response response = MessageFactory.createBindingResponse(request, evt.getRemoteAddress());
        // add USERNAME and MESSAGE-INTEGRITY attribute in the response
        // The responses utilize the same usernames and passwords as the requests
        Attribute usernameAttribute = AttributeFactory.createUsernameAttribute(uname.getUsername());
        response.putAttribute(usernameAttribute);
        logger.debug("UsernameAttribute: {}", usernameAttribute);
        Attribute messageIntegrityAttribute = AttributeFactory.createMessageIntegrityAttribute(username);
        response.putAttribute(messageIntegrityAttribute);
        try {
            logger.debug("Sending response: {}", response);
            stunStack.sendResponse(evt.getTransactionID().getBytes(), response, evt.getLocalAddress(), evt.getRemoteAddress());
        } catch (Exception e) {
            logger.warn("Failed to send {} through {}", response, evt.getLocalAddress(), e);
            // try to trigger a 500 response although if this one failed, then chances are the 500 will fail too.
            throw new RuntimeException("Failed to send a response", e);
        }
    }

    /**
     * Returns the value of the {@link PriorityAttribute} in request if there is one or throws an IllegalArgumentException with the
     * corresponding message.
     *
     * @param request the {@link Request} whose priority we'd like to obtain
     * @return the value of the {@link PriorityAttribute} in request if there is one
     * @throws IllegalArgumentException if the request does not contain a PRIORITY attribute and the stack needs to respond with a 400 Bad Request
     * {@link Response}.
     */
    private long extractPriority(Request request) throws IllegalArgumentException {
        // make sure we have a priority attribute and ignore otherwise.
        PriorityAttribute priorityAttr = (PriorityAttribute) request.getAttribute(Attribute.Type.PRIORITY);
        // apply tie-breaking
        // extract priority
        if (priorityAttr == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Received a connectivity check with no PRIORITY attribute, discarding");
            }
            throw new IllegalArgumentException("Missing PRIORITY attribute!");
        }
        return priorityAttr.getPriority();
    }

    /**
     * Resolves a role conflicts by either sending a 487 Role Conflict response or by changing this server's parent agent role. The method
     * returns true if the role conflict is silently resolved and processing can continue. It returns false if we had to reply
     * with a 487 and processing needs to stop until a repaired request is received.
     *
     * @param evt the {@link StunMessageEvent} containing the ICE-CONTROLLING or ICE-CONTROLLED attribute that allowed us to detect the role conflict
     * @return true if the role conflict is silently resolved and processing can continue and false otherwise
     */
    private boolean repairRoleConflict(StunMessageEvent evt) {
        Message req = evt.getMessage();
        boolean selfIceControlling = parentAgent.isControlling();
        // determine requester control
        IceControlAttribute theirIceControl = (IceControlAttribute) Optional.ofNullable(req.getAttribute(Attribute.Type.ICE_CONTROLLING)).orElse(req.getAttribute(Attribute.Type.ICE_CONTROLLED));
        logger.info("Requested control attribute: {}", theirIceControl);
        // If the agent is in the controlling role, and the ICE-CONTROLLING attribute is present in the request:
        boolean bothControllingConflict = selfIceControlling && theirIceControl.isControlling();
        // If the agent is in the controlled role, and the ICE-CONTROLLED
        // attribute is present in the request:
        boolean bothControlledConflict = !selfIceControlling && !theirIceControl.isControlling();
        if (!(bothControllingConflict || bothControlledConflict)) {
            // we don't have a role conflict
            return true;
        }
        long selfTieBreaker = parentAgent.getTieBreaker();
        long theirTieBreaker = theirIceControl.getTieBreaker();
        // If the agent's tie-breaker is larger than or equal to the contents of the ICE control attribute, the agent generates
        // a Binding error response and includes an ERROR-CODE attribute with a value of 487 (Role Conflict) but retains its role.
        if (Long.compareUnsigned(selfTieBreaker, theirTieBreaker) >= 0) {
            UsernameAttribute requestUserName = (UsernameAttribute) req.getAttribute(Attribute.Type.USERNAME);
            Response response = MessageFactory.createBindingErrorResponse(ErrorCodeAttribute.ROLE_CONFLICT);
            Attribute messageIntegrityAttribute = AttributeFactory.createMessageIntegrityAttribute(new String(requestUserName.getUsername()));
            response.putAttribute(messageIntegrityAttribute);
            try {
                stunStack.sendResponse(evt.getTransactionID().getBytes(), response, evt.getLocalAddress(), evt.getRemoteAddress());
                return false;
            } catch (Exception exc) {
                // rethrow so that we would send a 500 response instead.
                throw new RuntimeException("Failed to send a 487", exc);
            }
        } else {
            //If the agent's tie-breaker is less than the contents of the ICE control attribute, the agent toggles its ICE control role.
            String selfNextControlState = selfIceControlling ? "controlled" : "controlling";
            logger.trace("Switching to {} because theirTieBreaker={} selfTieBreaker={}", selfNextControlState, theirTieBreaker, selfTieBreaker);
            parentAgent.setControlling(!selfIceControlling);
        }
        logger.debug("No role conflict");
        return true; // we don't have a role conflict
    }

    /**
     * Verifies whether username is currently known to this server and returns true if so. Returns false otherwise.
     *
     * @param username the user name whose validity we'd like to check.
     * @return true if username is known to this ConnectivityCheckServer and false otherwise.
     */
    public boolean checkLocalUserName(String username) {
        String ufrag = username.split(":")[0];
        return ufrag.equals(parentAgent.getLocalUfrag());
    }

    /**
     * Implements the {@link CredentialsAuthority#getLocalKey(String)} method in a way that would return this handler's parent agent password if
     * username is either the local ufrag or the username that the agent's remote peer was expected to use.
     *
     * @param username the local ufrag that we should return a password for
     * @return this handler's parent agent local password if username equals the local ufrag and null otherwise
     */
    public byte[] getLocalKey(String username) {
        return checkLocalUserName(username) ? parentAgent.getLocalPassword().getBytes() : null;
    }

    /**
     * Implements the {@link CredentialsAuthority#getRemoteKey(String, String)} method in a way that would return this handler's parent agent remote
     * password if username is either the remote ufrag or the username that we are expected to use when querying the remote peer.
     *
     * @param username the remote ufrag that we should return a password for
     * @param media the media name that we want to get remote key
     * @return this handler's parent agent remote password if username equals the remote ufrag and null otherwise
     */
    public byte[] getRemoteKey(String username, String media) {
        IceMediaStream stream = parentAgent.getStream(media);
        if (stream != null) {
            // support both the case where username is the local fragment or the entire user name.
            int colon = username.indexOf(":");
            if (colon < 0) {
                //caller gave us a ufrag
                if (username.equals(stream.getRemoteUfrag())) {
                    return stream.getRemotePassword().getBytes();
                }
            } else {
                //caller gave us the entire username.
                if (username.equals(parentAgent.generateLocalUserName(media))) {
                    if (stream.getRemotePassword() != null) {
                        return stream.getRemotePassword().getBytes();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Starts this ConnectivityCheckServer. If it is not currently running, does nothing.
     */
    public void start() {
        if (started.compareAndSet(false, true)) {
            stunStack.addRequestListener(this);
        }
    }

    /**
     * Stops this ConnectivityCheckServer. A stopped ConnectivityCheckServer can be restarted by calling {@link #start()} on it.
     */
    public void stop() {
        if (started.compareAndSet(true, false)) {
            stunStack.removeRequestListener(this);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((parentAgent == null) ? 0 : parentAgent.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConnectivityCheckServer other = (ConnectivityCheckServer) obj;
        if (parentAgent == null) {
            if (other.parentAgent != null)
                return false;
        } else if (!parentAgent.equals(other.parentAgent))
            return false;
        return true;
    }

}
