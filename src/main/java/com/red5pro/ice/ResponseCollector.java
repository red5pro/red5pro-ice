/* See LICENSE.md for license information */
package com.red5pro.ice;

/**
 * The interface is used as a callback when sending a request. The response
 * collector is then used as a means of dispatching the response.
 *
 * @author Emil Ivov
 */
public interface ResponseCollector {
    /**
     * Dispatch the specified response.
     *
     * @param event the response to dispatch.
     */
    public void processResponse(StunResponseEvent event);

    /**
     * Notifies this collector that no response had been received after repeated
     * retransmissions of the original request (as described by rfc3489) and
     * that the request should be considered unanswered.
     *
     * @param event the StunTimeoutEvent containing a reference to the
     * transaction that has just failed.
     */
    public void processTimeout(StunTimeoutEvent event);
}
