/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.message.Message;

/**
 * This is a utility class used for dispatching incoming request events. We use this class mainly (and probably solely) for its ability to handle listener
 * proxies (i.e. listeners interested in requests received on a particular NetAccessPoint only).
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 */
public class EventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    /**
     * The STUN request and indication listeners registered with this EventDispatcher.
     */
    private final CopyOnWriteArraySet<MessageTypeEventHandler<?>> messageListeners = new CopyOnWriteArraySet<>();

    /**
     * The Map of EventDispatchers which keep the registrations of STUN request and indication listeners registered for
     * STUN requests and indications from specific local TransportAddresses.
     */
    private final ConcurrentMap<TransportAddress, EventDispatcher> children = new ConcurrentHashMap<>();

    /**
     * Initializes a new EventDispatcher instance.
     */
    public EventDispatcher() {
    }

    /**
     * Registers a specific MessageEventHandler for notifications about STUN indications received at a specific local TransportAddress.
     *
     * @param localAddr the local TransportAddress STUN indications received at which are to be reported to the specified indicationListener
     * @param indicationListener the MessageEventHandler which is to be registered for notifications about STUN indications received at the
     * specified local TransportAddress
     */
    public void addIndicationListener(TransportAddress localAddr, MessageEventHandler indicationListener) {
        addMessageListener(localAddr, new IndicationEventHandler(indicationListener));
    }

    /**
     * Registers a specific MessageEventHandler for notifications about old indications received at a specific local TransportAddress.
     *
     * @param localAddr the local TransportAddress STUN indications received at which are to be reported to the specified
     * indicationListener
     * @param indicationListener the MessageEventHandler which is to be registered for notifications about old indications received at the
     * specified local TransportAddress
     */
    public void addOldIndicationListener(TransportAddress localAddr, MessageEventHandler indicationListener) {
        addMessageListener(localAddr, new OldIndicationEventHandler(indicationListener));
    }

    /**
     * Registers a specific MessageTypeEventHandler for notifications about received STUN messages.
     *
     * @param messageListener the MessageTypeEventHandler which is to be registered for notifications about received STUN messages
     */
    private void addMessageListener(MessageTypeEventHandler<?> messageListener) {
        if (!messageListeners.contains(messageListener)) {
            messageListeners.add(messageListener);
        }
    }

    /**
     * Registers a specific MessageTypeEventHandler for notifications about STUN messages received at a specific local TransportAddress.
     *
     * @param localAddr the local TransportAddress STUN messages received at which are to be reported to the specified
     * messageListener
     * @param messageListener the MessageTypeEventHandler which is to be registered for notifications about STUN messages received at the
     * specified local TransportAddress
     */
    private void addMessageListener(TransportAddress localAddr, MessageTypeEventHandler<?> messageListener) {
        EventDispatcher child = children.get(localAddr);
        if (child == null) {
            child = new EventDispatcher();
            children.put(localAddr, child);
        }
        child.addMessageListener(messageListener);
    }

    /**
     * Add a RequestListener to the listener list. The listener is registered for requests coming from no matter which NetAccessPoint.
     *
     * @param listener  The RequestListener to be added
     */
    public void addRequestListener(RequestListener listener) {
        addMessageListener(new RequestListenerMessageEventHandler(listener));
    }

    /**
     * Add a RequestListener for a specific NetAccessPoint. The listener will be invoked only when a call on fireRequestReceived is issued for
     * that specific NetAccessPoint.
     *
     * @param localAddr  The NETAP descriptor that we're interested in.
     * @param listener  The ConfigurationChangeListener to be added
     */
    public void addRequestListener(TransportAddress localAddr, RequestListener listener) {
        addMessageListener(localAddr, new RequestListenerMessageEventHandler(listener));
    }

    /**
     * Unregisters a specific MessageTypeEventHandler from notifications about received STUN messages.
     *
     * @param messageListener the MessageTypeEventHandler to be
     * unregistered for notifications about received STUN messages
     */
    private void removeMessageListener(MessageTypeEventHandler<?> messageListener) {
        if (messageListeners.remove(messageListener)) {
            logger.debug("Removed message listener: {}", messageListener);
        }
    }

    /**
     * Unregisters a specific MessageTypeEventHandler from notifications about STUN messages received at a specific local TransportAddress.
     *
     * @param localAddr the local TransportAddress STUN messages
     * received at which to no longer be reported to the specified
     * messageListener
     * @param messageListener the MessageTypeEventHandler to be
     * unregistered for notifications about STUN messages received at the
     * specified local TransportAddress
     */
    private void removeMessageListener(TransportAddress localAddr, MessageTypeEventHandler<?> messageListener) {
        EventDispatcher child = children.get(localAddr);
        if (child != null) {
            logger.debug("Removing local addr: {} message listener: {}", localAddr, messageListener);
            child.removeMessageListener(messageListener);
        }
    }

    /**
     * Remove a RquestListener from the listener list. This removes a RequestListener that was registered
     * for all NetAccessPoints and would not remove listeners registered for specific NetAccessPointDescriptors.
     *
     * @param listener The RequestListener to be removed
     */
    public void removeRequestListener(RequestListener listener) {
        messageListeners.stream().filter(messageListener -> messageListener.delegate.equals(listener)).findFirst().ifPresent(this::removeMessageListener);
    }

    /**
     * Remove a RequestListener for a specific NetAccessPointDescriptor. This would only remove the listener for the specified NetAccessPointDescriptor
     * and would not remove it if it was also registered as a wildcard listener.
     *
     * @param localAddr  The NetAPDescriptor that was listened on.
     * @param listener  The RequestListener to be removed
     */
    public void removeRequestListener(TransportAddress localAddr, RequestListener listener) {
        removeMessageListener(localAddr, new RequestListenerMessageEventHandler(listener));
    }

    /**
     * Dispatch a StunMessageEvent to any registered listeners.
     *
     * @param evt  The request event to be delivered.
     */
    public void fireMessageEvent(StunMessageEvent evt) {
        TransportAddress localAddr = evt.getLocalAddress();
        char messageType = (char) (evt.getMessage().getMessageType() & 0x0110);
        for (MessageTypeEventHandler<?> messageListener : messageListeners) {
            if (messageType == messageListener.messageType) {
                messageListener.handleMessageEvent(evt);
            }
        }
        EventDispatcher child = children.get(localAddr);
        if (child != null) {
            child.fireMessageEvent(evt);
        }
    }

    /**
     * Check if there are any listeners for a specific address. (Generic listeners count as well)
     *
     * @param localAddr the NetAccessPointDescriptor.
     * @return true if there are one or more listeners for the specified NetAccessPointDescriptor
     */
    public boolean hasRequestListeners(TransportAddress localAddr) {
        if (!messageListeners.isEmpty()) {
            // there is a generic listener
            return true;
        }
        if (!children.isEmpty()) {
            EventDispatcher child = children.get(localAddr);
            if (child != null) {
                return !child.messageListeners.isEmpty();
            }
        }
        return false;
    }

    /**
     * Removes (absolutely all listeners for this event dispatcher).
     */
    public void removeAllListeners() {
        messageListeners.stream().forEach(this::removeMessageListener);
        messageListeners.clear();
        children.forEach((addr, child) -> child.removeAllListeners());
        children.clear();
    }

    /**
     * Implements MessageEventHandler for a MessageEventHandler which handles STUN indications.
     */
    private static class IndicationEventHandler extends MessageTypeEventHandler<MessageEventHandler> {

        /**
         * Initializes a new IndicationEventHandler which is to implement MessageEventHandler for a specific
         * MessageEventHandler which handles STUN indications.
         *
         * @param indicationListener the RequestListener for which the new instance is to implement MessageEventHandler
         */
        public IndicationEventHandler(MessageEventHandler indicationListener) {
            super(Message.STUN_INDICATION, indicationListener);
        }

        /**
         * Notifies this MessageEventHandler that a STUN message has been received, parsed and is ready for delivery.
         *
         * @param e a StunMessageEvent which encapsulates the STUN message to be handled
         * @see MessageEventHandler#handleMessageEvent(StunMessageEvent)
         */
        public void handleMessageEvent(StunMessageEvent e) {
            delegate.handleMessageEvent(e);
        }
    }

    /**
     * Implements MessageEventHandler for a MessageEventHandler which handles old DATA indications (0x0115).
     */
    private static class OldIndicationEventHandler extends MessageTypeEventHandler<MessageEventHandler> {

        /**
         * Initializes a new IndicationEventHandler which is to implement MessageEventHandler for a specific
         * MessageEventHandler which handles old DATA indications (0x0115).
         *
         * @param indicationListener the RequestListener for which the
         * new instance is to implement MessageEventHandler
         */
        public OldIndicationEventHandler(MessageEventHandler indicationListener) {
            super((char) 0x0110, indicationListener);
        }

        /**
         * Notifies this MessageEventHandler that a STUN message has been received, parsed and is ready for delivery.
         *
         * @param e a StunMessageEvent which encapsulates the STUN message to be handled
         * @see MessageEventHandler#handleMessageEvent(StunMessageEvent)
         */
        public void handleMessageEvent(StunMessageEvent e) {
            delegate.handleMessageEvent(e);
        }
    }

    /**
     * Represents the base for providers of MessageEventHandler implementations to specific Objects.
     *
     * @param <T> the type of the delegate to which the notifications are to be forwarded
     */
    private static abstract class MessageTypeEventHandler<T> implements MessageEventHandler {

        /**
         * The Object for which this instance implements MessageEventHandler.
         */
        public final T delegate;

        /**
         * The type of the STUN messages that this MessageEventHandler is interested in.
         */
        public final char messageType;

        /**
         * Initializes a new MessageTypeEventHandler which is to forward STUN messages with a specific type to a specific handler.
         *
         * @param messageType the type of the STUN messages that the new instance is to forward to the specified handler delegate
         * @param delegate the handler to which the new instance is to forward STUN messages with the specified messageType
         */
        public MessageTypeEventHandler(char messageType, T delegate) {
            if (delegate == null) {
                throw new NullPointerException("delegate");
            }
            this.messageType = messageType;
            this.delegate = delegate;
        }

        /**
         * Determines whether a specific Object is value equal to this Object.
         *
         * @param obj the Object to be compared to this Object for value equality
         * @return true if this Object is value equal to the specified obj
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!getClass().isInstance(obj))
                return false;

            MessageTypeEventHandler<?> mteh = (MessageTypeEventHandler<?>) obj;
            return (messageType == mteh.messageType) && delegate.equals(mteh.delegate);
        }

        /**
         * Returns a hash code value for this Object for the benefit of hashtables.
         *
         * @return a hash code value for this Object for the benefit of hashtables
         */
        @Override
        public int hashCode() {
            return (messageType | delegate.hashCode());
        }
    }

    /**
     * Implements MessageEventHandler for RequestListener.
     */
    private static class RequestListenerMessageEventHandler extends MessageTypeEventHandler<RequestListener> {

        /**
         * Initializes a new RequestListenerMessageEventHandler which is to implement MessageEventHandler for a specific RequestListener.
         *
         * @param requestListener the RequestListener for which the new instance is to implement MessageEventHandler
         */
        public RequestListenerMessageEventHandler(RequestListener requestListener) {
            super(Message.STUN_REQUEST, requestListener);
        }

        /**
         * Notifies this MessageEventHandler that a STUN message has been received, parsed and is ready for delivery.
         *
         * @param e a StunMessageEvent which encapsulates the STUN message to be handled
         * @see MessageEventHandler#handleMessageEvent(StunMessageEvent)
         */
        public void handleMessageEvent(StunMessageEvent e) {
            delegate.processRequest(e);
        }
    }
}
