/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.message.Request;
import com.red5pro.ice.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.ResponseCollector;
import com.red5pro.ice.StackProperties;
import com.red5pro.ice.StunException;
import com.red5pro.ice.StunMessageEvent;
import com.red5pro.ice.StunResponseEvent;
import com.red5pro.ice.StunTimeoutEvent;

/**
 * The {@code StunClientTransaction} class retransmits requests as specified by RFC 3489.
 *
 * Once formulated and sent, the client sends the Binding Request.  Reliability is accomplished through request retransmissions.  The
 * {@code StunClientTransaction} retransmits the request starting with an interval of 100ms, doubling every retransmit until the interval reaches 1.6s.
 * Retransmissions continue with intervals of 1.6s until a response is received, or a total of 9 requests have been sent. If no response is received by 1.6
 * seconds after the last request has been sent, the client SHOULD consider the transaction to have failed. In other words, requests would be sent at times
 * 0ms, 100ms, 300ms, 700ms, 1500ms, 3100ms, 4700ms, 6300ms, and 7900ms. At 9500ms, the client considers the transaction to have failed if no response
 * has been received.
 *
 * @author Emil Ivov.
 * @author Pascal Mogeri (contributed configuration of client transactions).
 * @author Lyubomir Marinov
 */
public class StunClientTransaction implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(StunClientTransaction.class);

    /**
     * The number of times to retransmit a request if no explicit value has been specified by com.red5pro.ice.MAX_RETRANSMISSIONS.
     */
    public static final int DEFAULT_MAX_RETRANSMISSIONS = 6;

    /**
     * The maximum number of milliseconds a client should wait between consecutive retransmissions, after it has sent a request for the first
     * time.
     */
    public static final int DEFAULT_MAX_WAIT_INTERVAL = 1600;

    /**
     * The number of milliseconds a client should wait before retransmitting, after it has sent a request for the first time.
     */
    public static final int DEFAULT_ORIGINAL_WAIT_INTERVAL = 100;

    private static UncaughtExceptionHandler uncaughtExceptionHandler = new UncaughtExceptionHandler() {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.warn("Uncaught exception on thread: {}", t.getName(), e);
        }

    };

    /**
     * The pool of Threads which retransmit StunClientTransactions.
     */
    private static final ExecutorService retransmissionThreadPool = Executors.newCachedThreadPool(new ThreadFactory() {
        /**
         * The default {@code ThreadFactory} implementation which is augmented by this instance to create daemon {@code Thread}s.
         */
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = defaultThreadFactory.newThread(r);
            if (t != null) {
                t.setDaemon(true);
                // Additionally, make it known through the name of the Thread that it is associated with the
                // StunClientTransaction class for debugging/informational purposes.
                String name = t.getName();
                if (name == null) {
                    name = "";
                }
                t.setName("StunClientTransaction-" + name);
                t.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            }
            return t;
        }
    });

    /**
     * Maximum number of retransmissions. Once this number is reached and if no response is received after {@link #maxWaitInterval} milliseconds the
     * request is considered unanswered.
     */
    public int maxRetransmissions = DEFAULT_MAX_RETRANSMISSIONS;

    /**
     * The number of milliseconds to wait before the first retransmission of the request.
     */
    public int originalWaitInterval = DEFAULT_ORIGINAL_WAIT_INTERVAL;

    /**
     * The maximum wait interval. Once this interval is reached we should stop doubling its value.
     */
    public int maxWaitInterval = DEFAULT_MAX_WAIT_INTERVAL;

    /**
     * The StunStack that created us.
     */
    private final StunStack stackCallback;

    /**
     * The request that we are retransmitting.
     */
    private final Request request;

    /**
     * The destination of the request.
     */
    private final TransportAddress requestDestination;

    /**
     * The id of the transaction.
     */
    private final TransactionID transactionID;

    /**
     * The TransportAddress through which the original request was sent and that we are supposed to be retransmitting through.
     */
    private final TransportAddress localAddress;

    /**
     * The instance to notify when a response has been received in the current transaction or when it has timed out.
     */
    private final ResponseCollector responseCollector;

    /**
     * Determines whether the transaction is active or not.
     */
    private boolean cancelled;

    /**
     * The Lock which synchronizes the access to the state of this instance. Introduced along with {@link #lockCondition} in order to allow
     * the invocation of {@link #cancel(boolean)} without a requirement to acquire the synchronization root. Otherwise, callers of
     * cancel(boolean) may (and have be reported multiple times to) fall into a deadlock merely because they want to cancel this
     * StunClientTransaction.
     */
    private final Lock lock = new ReentrantLock();

    /**
     * The Condition of {@link #lock} which this instance uses to wait for either the next retransmission interval or the cancellation of this
     * StunClientTransaction.
     */
    private final Condition lockCondition = lock.newCondition();

    /**
     * Creates a client transaction.
     *
     * @param stackCallback the stack that created us.
     * @param request the request that we are living for.
     * @param requestDestination the destination of the request.
     * @param localAddress the local TransportAddress this transaction
     * will be communication through.
     * @param responseCollector the instance that should receive this request's
     * response retransmit.
     */
    public StunClientTransaction(StunStack stackCallback, Request request, TransportAddress requestDestination, TransportAddress localAddress, ResponseCollector responseCollector) {
        this(stackCallback, request, requestDestination, localAddress, responseCollector, TransactionID.createNewTransactionID());
    }

    /**
     * Creates a client transaction.
     *
     * @param stackCallback the stack that created us.
     * @param request the request that we are living for.
     * @param requestDestination the destination of the request.
     * @param localAddress the local TransportAddress this transaction
     * will be communication through.
     * @param responseCollector the instance that should receive this request's
     * response retransmit.
     * @param transactionID the ID that we'd like the new transaction to have
     * in case the application created it in order to use it for application
     * data correlation.
     */
    public StunClientTransaction(StunStack stackCallback, Request request, TransportAddress requestDestination, TransportAddress localAddress, ResponseCollector responseCollector, TransactionID transactionID) {
        this.stackCallback = stackCallback;
        this.request = request;
        this.localAddress = localAddress;
        this.responseCollector = responseCollector;
        this.requestDestination = requestDestination;
        initTransactionConfiguration();
        this.transactionID = transactionID;
        try {
            request.setTransactionID(transactionID.getBytes());
        } catch (StunException ex) {
            // Shouldn't happen so lets just throw a RuntimeException in case something is really messed up.
            throw new IllegalArgumentException("The TransactionID class generated an invalid transaction ID");
        }
    }

    /**
     * Implements the retransmissions algorithm. Retransmits the request starting with an interval of 100ms, doubling every retransmit until the
     * interval reaches 1.6s.  Retransmissions continue with intervals of 1.6s until a response is received, or a total of 7 requests have been sent.
     * If no response is received by 1.6 seconds after the last request has been sent, we consider the transaction to have failed.
     * <br>
     * The method acquires {@link #lock} and invokes {@link #runLocked()}.
     */
    @Override
    public void run() {
        lock.lock();
        try {
            runLocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Implements the retransmissions algorithm. Retransmits the request starting with an interval of 100ms, doubling every retransmit until the
     * interval reaches 1.6s.  Retransmissions continue with intervals of 1.6s until a response is received, or a total of 7 requests have been sent.
     * If no response is received by 1.6 seconds after the last request has been sent, we consider the transaction to have failed.
     * <br>
     * The method assumes that the current thread has already acquired {@link #lock}.
     */
    private void runLocked() {
        // Indicates how many times we have retransmitted so far.
        int retransmissionCounter = 0;
        // How much did we wait after our last retransmission?
        int nextWaitInterval = originalWaitInterval;
        for (retransmissionCounter = 0; retransmissionCounter < maxRetransmissions; retransmissionCounter++) {
            waitFor(nextWaitInterval);
            //did someone tell us to get lost?
            if (cancelled) {
                return;
            }
            int curWaitInterval = nextWaitInterval;
            if (nextWaitInterval < maxWaitInterval) {
                nextWaitInterval *= 2;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Retrying STUN tid {} from {} to {} waited {}ms retrans {} of {}", transactionID, localAddress, requestDestination, curWaitInterval, (retransmissionCounter + 1), maxRetransmissions);
            }
            try {
                sendRequest0();
            } catch (Exception ex) {
                //I wonder whether we should notify anyone that a retransmission has failed
                logger.warn("A client tran {} retransmission failed", transactionID, ex);
            }
        }
        //before stating that a transaction has timeout-ed we should first wait for a reception of the response
        if (nextWaitInterval < maxWaitInterval) {
            nextWaitInterval *= 2;
        }
        waitFor(nextWaitInterval);
        if (cancelled) {
            return;
        }
        stackCallback.removeClientTransaction(this);
        responseCollector.processTimeout(new StunTimeoutEvent(stackCallback, request, getLocalAddress(), transactionID));
    }

    /**
     * Sends the request and schedules the first retransmission for after {@link #originalWaitInterval} and thus starts the retransmission
     * algorithm.
     *
     * @throws IOException if an error occurs while sending message bytes through the network socket
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     */
    void sendRequest() throws IllegalArgumentException, IOException {
        logger.debug("Sending STUN tid {} from {} to {}", transactionID, localAddress, requestDestination);
        sendRequest0();
        retransmissionThreadPool.execute(this);
    }

    /**
     * Simply calls the sendMessage method of the access manager.
     *
     * @throws IOException  if an error occurs while sending message bytes through the network socket
     * @throws IllegalArgumentException if the apDescriptor references an access point that had not been installed
     */
    private void sendRequest0() throws IllegalArgumentException, IOException {
        if (cancelled) {
            logger.debug("Trying to resend a cancelled transaction");
        } else {
            stackCallback.getNetAccessManager().sendMessage(request, localAddress, requestDestination);
        }
    }

    /**
     * Returns the request that was the reason for creating this transaction.
     *
     * @return the request that was the reason for creating this transaction.
     */
    Request getRequest() {
        return this.request;
    }

    /**
     * Waits until next retransmission is due or until the transaction is cancelled (whichever comes first).
     *
     * @param millis the number of milliseconds to wait for.
     */
    void waitFor(long millis) {
        logger.debug("Transaction: {} waitFor: {}", transactionID, millis);
        lock.lock();
        try {
            lockCondition.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels the transaction. Once this method is called the transaction is considered terminated and will stop retransmissions.
     *
     * @param waitForResponse indicates whether we should wait for the current RTO to expire before ending the transaction or immediately terminate.
     */
    void cancel(boolean waitForResponse) {
        // XXX The cancelled field is initialized to false and then the one and only write access to it is here to set it to true. The rest of the
        // code just checks whether it has become true. Consequently, there shouldn't be a problem if the set is outside a synchronized block.
        // However, it being outside a synchronized block will decrease the risk of deadlocks.
        cancelled = true;
        if (!waitForResponse) {
            // Try to interrupt #waitFor(long) if possible. But don't risk a deadlock. It is not a problem if it is not possible to interrupt
            // #waitFor(long) here because it will complete in finite time and this StunClientTransaction will eventually notice that it has
            // been cancelled.
            if (lock.tryLock()) {
                try {
                    lockCondition.signal();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Cancels the transaction. Once this method is called the transaction is considered terminated and will stop retransmissions.
     */
    void cancel() {
        cancel(false);
    }

    /**
     * Dispatches the response then cancels itself and notifies the StunStack for its termination.
     *
     * @param evt the event that contains the newly received message
     */
    public void handleResponse(StunMessageEvent evt) {
        lock.lock();
        try {
            TransactionID transactionID = getTransactionID();
            logger.debug("handleResponse tid {}", transactionID);
            if (!StackProperties.getBoolean(StackProperties.KEEP_CRANS_AFTER_A_RESPONSE, false)) {
                cancel();
            }
            responseCollector.processResponse(new StunResponseEvent(stackCallback, evt.getRawMessage(), (Response) evt.getMessage(), request, transactionID));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the ID of the current transaction.
     *
     * @return the ID of the transaction.
     */
    TransactionID getTransactionID() {
        return this.transactionID;
    }

    /**
     * Init transaction duration/retransmission parameters. (Mostly contributed by Pascal Maugeri.)
     */
    private void initTransactionConfiguration() {
        //Max Retransmissions
        String maxRetransmissionsStr = System.getProperty(StackProperties.MAX_CTRAN_RETRANSMISSIONS);
        if (maxRetransmissionsStr != null && maxRetransmissionsStr.trim().length() > 0) {
            try {
                maxRetransmissions = Integer.parseInt(maxRetransmissionsStr);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse MAX_RETRANSMISSIONS", e);
                maxRetransmissions = DEFAULT_MAX_RETRANSMISSIONS;
            }
        }
        //Original Wait Interval
        String originalWaitIntervalStr = System.getProperty(StackProperties.FIRST_CTRAN_RETRANS_AFTER);
        if (originalWaitIntervalStr != null && originalWaitIntervalStr.trim().length() > 0) {
            try {
                originalWaitInterval = Integer.parseInt(originalWaitIntervalStr);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse ORIGINAL_WAIT_INTERVAL", e);
                originalWaitInterval = DEFAULT_ORIGINAL_WAIT_INTERVAL;
            }
        }
        //Max Wait Interval
        String maxWaitIntervalStr = System.getProperty(StackProperties.MAX_CTRAN_RETRANS_TIMER);
        if (maxWaitIntervalStr != null && maxWaitIntervalStr.trim().length() > 0) {
            try {
                maxWaitInterval = Integer.parseInt(maxWaitIntervalStr);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse MAX_WAIT_INTERVAL", e);
                maxWaitInterval = DEFAULT_MAX_WAIT_INTERVAL;
            }
        }
    }

    /**
     * Returns the local TransportAddress that this transaction is
     * sending requests from.
     *
     * @return  the local TransportAddress that this transaction is
     * sending requests from.
     */
    public TransportAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * Returns the remote TransportAddress that this transaction is
     * sending requests to.
     *
     * @return the remote TransportAddress that this transaction is
     * sending requests to.
     */
    public TransportAddress getRemoteAddress() {
        return requestDestination;
    }
}
