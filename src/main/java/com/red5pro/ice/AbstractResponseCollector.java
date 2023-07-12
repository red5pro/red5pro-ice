/* See LICENSE.md for license information */
package com.red5pro.ice;

/**
 * @author Lubomir Marinov
 */
public abstract class AbstractResponseCollector implements ResponseCollector {

    /**
     * Notifies this ResponseCollector that a transaction described by
     * the specified BaseStunMessageEvent has failed. The possible
     * reasons for the failure include timeouts, unreachable destination, etc.
     *
     * @param event the BaseStunMessageEvent which describes the failed
     * transaction and the runtime type of which specifies the failure reason
     */
    protected abstract void processFailure(BaseStunMessageEvent event);

    /**
     * Notifies this collector that no response had been received after repeated
     * retransmissions of the original request (as described by rfc3489) and
     * that the request should be considered unanswered.
     *
     * @param event the StunTimeoutEvent containing a reference to the
     * transaction that has just failed.
     */
    public void processTimeout(StunTimeoutEvent event) {
        processFailure(event);
    }

    /**
     * Notifies this collector that the destination of the request has been
     * determined to be unreachable and that the request should be considered
     * unanswered.
     *
     * @param event the StunFailureEvent containing the
     * PortUnreachableException that has just occurred.
     */
    public void processUnreachable(StunFailureEvent event) {
        processFailure(event);
    }
}
