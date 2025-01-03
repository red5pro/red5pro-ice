package com.red5pro.server.util;

import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides ports and more.
 *
 * **Note:** This is a copy from commons; it here under tests to avoid dependency on commons.
 *
 * @author Andy Shaules
 * @author Paul Gregoire
 */
public class PortManager {

    private static Logger log = LoggerFactory.getLogger(PortManager.class);

    private static boolean isDebug = log.isDebugEnabled();

    // maximum ephemeral port inclusive
    private static final int MAX_PORT = 65535;

    // default low anon ephemeral port inclusive; max 65535
    /**
     * Lowest port number that can be allocated(inclusive).
     */
    private static int rtpPortBase = 49152;

    // maximum anon ephemeral port 65535
    /**
     * Highest port number that can be allocated(inclusive).
     */
    private static int rtpPortCeiling = MAX_PORT;

    // base socket timeout in milliseconds
    private static int soTimeoutMs = 5;

    // allow system port allocations (may be outside configured port range)
    private static boolean allowSystemPorts;

    // holds ports that have been allocated and only those that are allocated. The port may be bound to multiple addresses/devices by the client.
    private static CopyOnWriteArraySet<Integer> allocatedPorts = new CopyOnWriteArraySet<>();

    // holds ports that were not allocated from here, or were still bound when the next client tried to use it.
    private static CopyOnWriteArraySet<Integer> otherAllocatedPorts = new CopyOnWriteArraySet<>();

    // port selection base
    @SuppressWarnings("unused")
    private volatile int port = Math.max(1024, (rtpPortBase - 1)); // use 1024 as base max to avoid root issue on unix

    // atomic updater for the port
    private static AtomicIntegerFieldUpdater<PortManager> portUpdater = AtomicIntegerFieldUpdater.newUpdater(PortManager.class, "port");

    // single static instance of this port manager for use with the atomic updater
    private static final PortManager instance = new PortManager();

    private static ReentrantLock criticalSection = new ReentrantLock();

    /**
     * Clear an allocated port entry.
     *
     * @param rtpPort the port to clear
     */
    public static void clearRTPServerPort(int rtpPort) {
        if (allocatedPorts.remove(rtpPort)) {
            if (isDebug) {
                log.debug("Clearing server port for re-use {}", rtpPort);
            }
        } else {
            if (isDebug) {
                log.debug("Port {} was not allocated or has already been cleared", rtpPort);
            }
        }
        if (otherAllocatedPorts.remove(rtpPort)) {
            log.debug("Port {} was recovered for use {}", rtpPort);
        }
    }

    /**
     * Clear a collection of allocated port entries.
     *
     * @param allocatedPorts ports to clear
     */
    public static void clearRTPServerPorts(Collection<Integer> allocatedPorts) {
        allocatedPorts.forEach(port -> {
            PortManager.clearRTPServerPort(port);
        });
    }

    private static int getNextInRange() throws InterruptedException {
        // Grab the lock, but bail if interrupted.
        criticalSection.lockInterruptibly();

        try {
            int previous = portUpdater.get(instance);
            if (previous >= rtpPortCeiling) {//should never be greater-than but you never know what the future brings.
                portUpdater.set(instance, (rtpPortBase - 1));
            }
            return portUpdater.incrementAndGet(instance);

        } finally {
            if (criticalSection.isHeldByCurrentThread()) {
                criticalSection.unlock();
            }
        }
    }

    /**
     * Get an available port; defaults to UDP.
     *
     * @return port for use with a socket
     */
    public static int getRTPServerPort() {
        return getRTPServerPort(true);
    }

    /**
     * Get an available port.
     *
     * @param udp true to use DatagramSocket and false to use ServerSocket
     * @return port for use with a socket
     */
    public static int getRTPServerPort(boolean udp) {
        log.debug("Get port");
        int serverPort = 0;
        //Checking range exhaustion in loop to halt potential thread races.
        while (!isRangeExhausted()) {
            try {
                serverPort = getNextInRange();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
            if (serverPort == 0) {
                break;//problems.
            }
            // check the other list first
            if (otherAllocatedPorts.contains(serverPort)) {
                // port is not available
                log.info("Port is bound elsewhere {}", serverPort);
                serverPort = 0;// Do not let thread escape with 'otherAllocatedPort' number.
                continue;
            }
            // add only works if its not already allocated
            if (allocatedPorts.add(serverPort)) {
                // if the port isn't allocated, allocate it and check availability
                if (udp) {
                    // check UDP port
                    if (!checkAvailable(serverPort)) {
                        // port is not available
                        log.warn("Unallocated port is already bound or not freed yet {}", serverPort);
                        // XXX remove it from allocated and transfer to otherAllocated, since its allocated from elsewhere
                        if (allocatedPorts.remove(serverPort)) {
                            otherAllocatedPorts.add(serverPort);
                        }
                        serverPort = 0;// Do not let thread escape with unavailable port number.
                        continue;
                    }
                } else {
                    // check TCP port
                    if (!checkAvailable(serverPort, true)) {
                        // port is not available
                        log.warn("Unallocated port is already bound or not freed yet {}", serverPort);
                        // XXX remove it from allocated and transfer to otherAllocated, since its allocated from elsewhere
                        if (allocatedPorts.remove(serverPort)) {
                            otherAllocatedPorts.add(serverPort);

                        }
                        serverPort = 0;// Do not let thread escape with unavailable port number.
                        continue;
                    }
                }
                // return with currently available port
                return serverPort;
            } else {// else continue while range is not exhausted.
                serverPort = 0;
            }
        }

        if (serverPort == 0) {
            if (allowSystemPorts) {
                // this will allow the return a port outside configured range
                log.info("Configured port range has been exhausted, the next system available port will be searched");
                // give them a port available on the system which exists outside the range
                serverPort = udp ? findFreeUdpPort() : findFreeTcpPort();
            } else {
                log.warn("Configured port range has been exhausted, no ports available");
            }
        }

        log.debug("Ports exhausted");
        return serverPort;
    }

    /**
     * Get an available port using randomizer.
     *
     * @return port for use with a socket
     */
    public static int getRTPServerPortRandom() {
        //log.debug("Get port");
        // start a random port within range
        int serverPort = ThreadLocalRandom.current().nextInt(rtpPortBase, rtpPortCeiling + 1);//+1 to include rtpPortCeiling.
        for (; serverPort <= rtpPortCeiling; serverPort++) {
            // add only works if its not already allocated
            if (allocatedPorts.add(serverPort)) {
                // flag to check availability
                if (!checkAvailable(serverPort)) {
                    // port is not available
                    log.warn("Unallocated port is already bound {}", serverPort);
                    continue;
                }
                // break out with currently available port
                break;
            }
        }
        if (isDebug) {
            log.debug("Port allocated {}", serverPort);
        }
        return serverPort;
    }

    /**
     * Checks a port for availability using DatagramSocket; this may or may not be useful for TCP as well.
     *
     * @param port to check
     * @return true if port is available and false otherwise
     */
    public static boolean checkAvailable(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(soTimeoutMs);
            int retPort = socket.getLocalPort();
            if (port == retPort) {
                if (isDebug) {
                    log.debug("Port: {} is available", port);
                }
            } else {
                if (isDebug) {
                    log.debug("Port didnt match: {}", retPort);
                }
            }
            socket.close();
            return true;
        } catch (Throwable t) {
            log.warn("Exception checking port: {}", port, t);
        }
        return false;
    }

    /**
     * Checks a port for availability using ServerSocket primarily for TCP.
     *
     * @param port to check
     * @param tcp true to use ServerSocket and false to use DatagramSocket
     * @return true if port is available and false otherwise
     */
    public static boolean checkAvailable(int port, boolean tcp) {
        if (tcp) {
            try (ServerSocket socket = new ServerSocket(port)) {
                socket.setReuseAddress(true);
                socket.setSoTimeout(1); // 1ms
                int retPort = socket.getLocalPort();
                if (port == retPort) {
                    if (isDebug) {
                        log.debug("Port: {} is available", port);
                    }
                } else {
                    if (isDebug) {
                        log.debug("Port didnt match: {}", retPort);
                    }
                }
                socket.close();
                return true;
            } catch (Throwable t) {
                log.warn("Exception checking port: {}", port, t);
            }
            return false;
        } else {
            return checkAvailable(port);
        }
    }

    /**
     * Returns a free UDP port on this machine.
     *
     * @return port
     */
    public static int findFreeUdpPort() {
        int port = 0;
        do {
            try (DatagramSocket socket = new DatagramSocket(0)) {
                socket.setReuseAddress(true);
                socket.setSoTimeout(soTimeoutMs);
                port = socket.getLocalPort();
                socket.close();
                return port;
            } catch (Throwable t) {
                log.debug("Exception checking port: {}", port, t);
            }
        } while (port < MAX_PORT);
        throw new IllegalStateException("Could not find a free UDP port");
    }

    /**
     * Returns a free TCP port on this machine.
     *
     * @return port
     */
    public static int findFreeTcpPort() {
        int port = 0;
        do {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                socket.setSoTimeout(1); // 1ms
                port = socket.getLocalPort();
                socket.close();
                return port;
            } catch (Throwable t) {
                log.debug("Exception checking port: {}", port, t);
            }
        } while (port < MAX_PORT);
        throw new IllegalStateException("Could not find a free TCP port");
    }

    /**
     * Sets the port base and also reset last port allocated to the newly selected base minus one.
     *
     * @param rtpPortBase base port to start on
     */
    public static void setRtpPortBase(int rtpPortBase) {
        if (rtpPortBase > MAX_PORT) {
            log.warn("Invalid base port: {}; defaulting to 1024", rtpPortBase);
            // XXX setting this to the max would seem to prevent any port allocations at all; use default >root port instead 1024
            rtpPortBase = 1024;
        }
        PortManager.rtpPortBase = rtpPortBase;
        // set the port base on this change (minus 1 since we always use incrementAndGet)
        portUpdater.set(instance, (rtpPortBase - 1));
    }

    public static void setRtpPortCeiling(int rtpPortCeiling) {
        if (rtpPortCeiling > MAX_PORT) {
            log.warn("Invalid max port: {}; defaulting to {}", rtpPortCeiling, MAX_PORT);
            rtpPortCeiling = MAX_PORT;
        }
        PortManager.rtpPortCeiling = rtpPortCeiling;
    }

    /**
     * Set the socket timeout for availability checks.
     *
     * @param soTimeoutMs
     */
    public static void setSoTimeout(int soTimeoutMs) {
        PortManager.soTimeoutMs = soTimeoutMs;
    }

    /**
     * Sets whether or not to test a port for availability before returning it.
     *
     * @param checkPortAvailability true to check the port with a binding and false to simply return it
     */
    public static void setCheckPortAvailability(boolean checkPortAvailability) {
        //log.info("ignoring request for port checks {}",checkPortAvailability);
        //PortManager.checkPortAvailability = checkPortAvailability;
    }

    /**
     * Allows for using system ports, which may exceed the port range specified.
     *
     * @param allowSystemPorts
     */
    public static void setAllowSystemPorts(boolean allowSystemPorts) {
        PortManager.allowSystemPorts = allowSystemPorts;
    }

    /**
     * Returns allocated UDP ports; this can include ports allocated outside this application in the specified range.
     *
     * @return allocated port count plus ports found to be allocated by other processes or leaked by faulty client behavior/handling.
     */
    public static int getCount() {
        // may not be accurate
        return allocatedPorts.size() + otherAllocatedPorts.size();
    }

    /**
     * Returns a "pretty" string of the port base and ceiling.
     *
     * @return port range string
     */
    public static String getRange() {
        return String.format("%d-%d", rtpPortBase, rtpPortCeiling);
    }

    /**
     * Returns port range exhaustion state.
     * Range includes rtpPortCeiling and rtpPortBase.
     *
     * @return true if ports in configured range are exhausted and false if not exhausted
     */
    public static boolean isRangeExhausted() {
        log.trace("isRangeExhausted - {} == {}", ((rtpPortCeiling + 1) - rtpPortBase), getCount());
        return ((rtpPortCeiling + 1) - rtpPortBase) == getCount();
    }

    /**
     * Clear out ports that have closed without having been deallocated.
     */
    public static void cleanAllocations() {
        allocatedPorts.forEach(port -> {
            // XXX be aware that checking the port incurs a blocking penalty on receive, up to soTimeoutMs
            if (checkAvailable(port)) {
                clearRTPServerPort(port);
            }
        });

        otherAllocatedPorts.forEach(port -> {
            // XXX be aware that checking the port incurs a blocking penalty on receive, up to soTimeoutMs
            if (checkAvailable(port)) {
                clearRTPServerPort(port);
            }
        });
    }
}
