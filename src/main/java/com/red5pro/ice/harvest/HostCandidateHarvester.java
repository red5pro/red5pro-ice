/* See LICENSE.md for license information */
package com.red5pro.ice.harvest;

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.red5pro.ice.nio.IceTransport;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stack.StunStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Component;
import com.red5pro.ice.HostCandidate;
import com.red5pro.ice.NetworkUtils;
import com.red5pro.ice.StackProperties;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * A HostCandidateHarvester gathers host Candidates for a specified {@link com.red5pro.ice.Component}. Most CandidateHarvesters
 * would rely on the output of the host harvester, that is all host addresses, to be already present and bound in a Component before being able to
 * harvest the type of addresses that they are responsible for.
 *
 * @author Emil Ivov
 * @author George Politis
 * @author Boris Grozev
 */
public class HostCandidateHarvester {

    private static final Logger logger = LoggerFactory.getLogger(HostCandidateHarvester.class);

    /**
     * Manages statistics about harvesting time.
     */
    private HarvestStatistics harvestStatistics = new HarvestStatistics();

    /**
     * Holds the list of allowed interfaces. It's either a non-empty array or null.
     */
    private static String[] allowedInterfaces;

    /**
     * Holds the list of blocked interfaces. It's either a non-empty array or null.
     */
    private static String[] blockedInterfaces;

    /**
     * The list of allowed addresses.
     */
    private static List<InetAddress> allowedAddresses;

    /**
     * The list of blocked addresses.
     */
    private static List<InetAddress> blockedAddresses;

    /**
     * A boolean value that indicates whether the host candidate interface filters have been initialized or not.
     */
    private static AtomicBoolean interfaceFiltersInitialized = new AtomicBoolean(false);

    /**
     * A boolean value that indicates whether the host candidate address filters have been initialized or not.
     */
    private static AtomicBoolean addressFiltersInitialized = new AtomicBoolean(false);

    /**
     * Available host addresses.
     */
    private static List<AddressRef> availableHostAddresses;

    static {
        availableHostAddresses = getAvailableHostAddresses();
        logger.debug("Available host addresses: {}", availableHostAddresses);
    }

    /**
     * Gets the array of allowed interfaces.
     *
     * @return the non-empty String array of allowed interfaces or null
     */
    public static String[] getAllowedInterfaces() {
        if (!interfaceFiltersInitialized.get()) {
            try {
                initializeInterfaceFilters();
            } catch (Exception e) {
                logger.warn("There were errors during host candidate interface filters initialization", e);
            }
        }
        return allowedInterfaces;
    }

    /**
     * Gets the array of blocked interfaces.
     *
     * @return the non-empty String array of blocked interfaces or null.
     */
    public static String[] getBlockedInterfaces() {
        if (!interfaceFiltersInitialized.get()) {
            try {
                initializeInterfaceFilters();
            } catch (Exception e) {
                logger.warn("There were errors during host candidate interface filters initialization", e);
            }
        }
        return blockedInterfaces;
    }

    /**
     * Gets the list of addresses which have been explicitly allowed via configuration properties. To get the list of all allowed addresses,
     * use {@link #getAllAllowedAddresses()}.
     * 
     * @return the list of explicitly allowed addresses
     */
    public static List<InetAddress> getAllowedAddresses() {
        if (!addressFiltersInitialized.get()) {
            initializeAddressFilters();
        }
        return allowedAddresses;
    }

    /**
     * Gets the list of blocked addresses.
     * 
     * @return the list of blocked addresses
     */
    public static List<InetAddress> getBlockedAddresses() {
        if (!addressFiltersInitialized.get()) {
            initializeAddressFilters();
        }
        return blockedAddresses;
    }

    /**
     * Initializes the lists of allowed and blocked addresses according to the configuration properties.
     */
    private static void initializeAddressFilters() {
        if (addressFiltersInitialized.compareAndSet(false, true)) {
            String[] allowedAddressesStr = StackProperties.getStringArray(StackProperties.ALLOWED_ADDRESSES, ";");
            if (allowedAddressesStr != null) {
                for (String addressStr : allowedAddressesStr) {
                    InetAddress address;
                    try {
                        address = InetAddress.getByName(addressStr);
                    } catch (Exception e) {
                        logger.warn("Failed to add an allowed address: {}", addressStr, e);
                        continue;
                    }
                    if (allowedAddresses == null) {
                        allowedAddresses = new ArrayList<>();
                    }
                    allowedAddresses.add(address);
                    logger.debug("Allowed: {}", allowedAddresses);
                }
            }
            String[] blockedAddressesStr = StackProperties.getStringArray(StackProperties.BLOCKED_ADDRESSES, ";");
            // if allowed addresses is specified and blocked addresses are not, block all other addresses
            if (allowedAddressesStr != null && blockedAddressesStr == null) {
                boolean isIPv6Disabled = StackProperties.getBoolean(StackProperties.DISABLE_IPv6, true);
                // create the blocked address holder if not done already
                if (blockedAddresses == null) {
                    blockedAddresses = new ArrayList<>();
                }
                // go thru detected host addresses and block those that aren't explicitly allowed
                availableHostAddresses.forEach(ref -> {
                    InetAddress addr = ref.getAddress();
                    if (!allowedAddresses.contains(addr)) {
                        if (addr instanceof Inet6Address) {
                            // dont automatically block ipv6 unless they are disabled
                            if (isIPv6Disabled) {
                                blockedAddresses.add(addr);
                            } else {
                                // ensure they are added to allowed, since they are bindable and not disabled
                                allowedAddresses.add(addr);
                            }
                        } else if (addr instanceof Inet4Address) {
                            blockedAddresses.add(addr);
                        }
                    }
                });
                logger.debug("Allowed: {} blocked: {}", allowedAddresses, blockedAddresses);
            } else if (blockedAddressesStr != null) {
                for (String addressStr : blockedAddressesStr) {
                    InetAddress address;
                    try {
                        address = InetAddress.getByName(addressStr);
                    } catch (Exception e) {
                        logger.warn("Failed to add a blocked address: {}", addressStr, e);
                        continue;
                    }
                    if (blockedAddresses == null) {
                        blockedAddresses = new ArrayList<>();
                    }
                    blockedAddresses.add(address);
                }
                logger.debug("Blocked: {}", blockedAddresses);
            }
        }
    }

    /**
     * @return the list of all local IP addresses from all allowed network interfaces, which are allowed addresses.
     */
    public static List<InetAddress> getAllAllowedAddresses() {
        List<InetAddress> addresses = new LinkedList<>();
        boolean isIPv6Disabled = StackProperties.getBoolean(StackProperties.DISABLE_IPv6, true);
        boolean isIPv6LinkLocalDisabled = StackProperties.getBoolean(StackProperties.DISABLE_LINK_LOCAL_ADDRESSES, false);
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (NetworkUtils.isInterfaceLoopback(iface) || !NetworkUtils.isInterfaceUp(iface) || !isInterfaceAllowed(iface)) {
                    continue;
                }
                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress address = ifaceAddresses.nextElement();
                    if (!isAddressAllowed(address)) {
                        continue;
                    }
                    if (isIPv6Disabled && address instanceof Inet6Address) {
                        continue;
                    }
                    if (isIPv6LinkLocalDisabled && address instanceof Inet6Address && address.isLinkLocalAddress()) {
                        continue;
                    }
                    addresses.add(address);
                }
            }
        } catch (SocketException se) {
            logger.warn("Failed to get network interfaces", se);
        }
        return addresses;
    }

    /**
     * @return the list of all local IP addresses from all allowed network interfaces, which are allowed addresses.
     */
    public static List<AddressRef> getAvailableHostAddresses() {
        Set<AddressRef> addresses = new HashSet<>();
        boolean isIPv6Disabled = StackProperties.getBoolean(StackProperties.DISABLE_IPv6, true);
        boolean isIPv6LinkLocalDisabled = StackProperties.getBoolean(StackProperties.DISABLE_LINK_LOCAL_ADDRESSES, false);
        // holder of bindable addresses
        final Set<InetAddress> bindableAddresses = new HashSet<>();
        // gather network interfaces
        List<NetworkInterface> nics = Collections.emptyList();
        try {
            // get network interfaces
            nics = Collections.list(NetworkInterface.getNetworkInterfaces());
            // collect all the addresses that could possibly be bound on this system
            nics.forEach(iface -> {
                if (!NetworkUtils.isInterfaceLoopback(iface) || NetworkUtils.isInterfaceUp(iface) || isInterfaceAllowed(iface)) {
                    Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                    while (ifaceAddresses.hasMoreElements()) {
                        InetAddress addr = ifaceAddresses.nextElement();
                        String interfaceName = iface.getDisplayName();
                        logger.info("Address {} is bindable on interface: {}", addr, interfaceName);
                        bindableAddresses.add(addr);
                        // if the address is bindable and not on an `lo` interface, add to addresses list
                        if (!interfaceName.startsWith("lo")) {
                            // IPv6 addresses display with a suffix might be confusing if string compare is done
                            if (isIPv6Disabled && addr instanceof Inet6Address) {
                                logger.debug("IPv6 address disabled: {}", addr);
                                continue;
                            }                        
                            addresses.add(new AddressRef(addr, NetworkUtils.isInterfaceVirtual(iface)));
                        }
                    }
                }
            });
        } catch (Exception se) {
            logger.warn("Exception collecting network interfaces", se);
        }
        // White list from the configuration
        String[] allowedAddressesStr = StackProperties.getStringArray(StackProperties.ALLOWED_ADDRESSES, ";");
        if (allowedAddressesStr != null) {
            for (int i = 0; i < allowedAddressesStr.length; i++) {
                try {
                    InetAddress addr = InetAddress.getByName(allowedAddressesStr[i]);
                    if (bindableAddresses.contains(addr)) {
                        addresses.add(new AddressRef(addr, false));
                    } else {
                        logger.info("Address is not bindable on existing interfaces: {}", addr);
                    }
                } catch (UnknownHostException e) {
                    logger.warn("Unknown host address during initial lookup", e);
                }
            }
            // bust-out early if we've got proper allowed addresses
            if (!addresses.isEmpty()) {
                return new ArrayList<AddressRef>(addresses);
            }
        }
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (NetworkUtils.isInterfaceLoopback(iface) || !NetworkUtils.isInterfaceUp(iface) || !isInterfaceAllowed(iface)) {
                    continue;
                }
                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress address = ifaceAddresses.nextElement();
                    if (!isAddressAllowed(address)) {
                        continue;
                    }
                    if (isIPv6Disabled && address instanceof Inet6Address) {
                        continue;
                    }
                    if (isIPv6LinkLocalDisabled && address instanceof Inet6Address && address.isLinkLocalAddress()) {
                        continue;
                    }
                    addresses.add(new AddressRef(address, NetworkUtils.isInterfaceVirtual(iface)));
                }
            }
        } catch (SocketException se) {
            logger.warn("Failed to get network interfaces", se);
        }
        return new ArrayList<AddressRef>(addresses);
    }

    public void harvest(Component component, int port, Transport transport) throws IllegalArgumentException, IOException {
        logger.debug("harvest {} port: {}", transport, port);
        logger.trace("Starting socket creation loop");
        availableHostAddresses.forEach(addrRef -> {
            logger.debug("addr: {}", addrRef);
            // grab the address
            InetAddress addr = addrRef.getAddress();
            // ensure address is allowed first
            if (isAddressAllowed(addr)) {
                IceSocketWrapper iceSocket = null;
                try {
                    if (transport == Transport.UDP) {
                        iceSocket = createDatagramSocket(addr, port);
                    } else if (transport == Transport.TCP) {
                        iceSocket = createServerSocket(addr, port);
                    }
                    logger.debug("Socket created/bound: {}", iceSocket);
                    HostCandidate candidate = new HostCandidate(iceSocket, component, transport);
                    candidate.setVirtual(addrRef.isVirtual());
                    StunStack stunStack = candidate.getStunStack();
                    // add the socket wrapper to the stack which gets the bind and listening process started
                    if (stunStack.addSocket(iceSocket, null, true)) { // do socket binding
                        logger.debug("Socket added to stack: {}", iceSocket);
                        component.addLocalCandidate(candidate);
                        component.getComponentSocket().addSocketWrapper(iceSocket);
                    } else {
                        logger.warn("Socket not added to stack: {}", iceSocket);
                    }
                } catch (Throwable t) {
                    // There seems to be a problem with this particular address let's just move on for now and hope we will find better
                    logger.warn("Socket creation failed on: {} transport: {}\nPort: {}", addrRef, transport, port, t);
                }
            } else {
                logger.debug("Address is not allowed: {}", addr);
            }
        });
        logger.trace("Exited socket creation loop");
        if (component.getLocalCandidateCount() == 0) {
            throw new IOException("Failed to bind even a single host candidate for component:" + component + " port=" + port + " transport=" + transport);
        }
        logger.debug("Exit harvest port: {}", port);
    }

    /**
     * Gathers all candidate addresses on the local machine, binds sockets on them and creates {@link HostCandidate}s. The harvester would always
     * try to bind the sockets on the specified preferredPort first. If that fails we will move through all ports between minPort and
     * maxPort and give up if still can't find a free port.
     *
     * @param component the {@link Component} that we'd like to gather candidate addresses for
     * @param preferredPort the port number that should be tried first when binding local Candidate sockets for this Component
     * @param minPort the port number where we should first try to bind before moving to the next one (i.e. minPort + 1)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception
     * @param transport transport protocol used
     * @throws IllegalArgumentException if either minPort or maxPort is not a valid port number, minPort &gt; maxPort or if transport is not supported
     * @throws IOException if an error occurs
     */
    public void harvest(Component component, int preferredPort, int minPort, int maxPort, Transport transport) throws IllegalArgumentException, IOException {
        logger.debug("harvest port: {}", preferredPort);
        if (transport == null || (transport != Transport.UDP && transport != Transport.TCP)) {
            throw new IllegalArgumentException("Transport protocol not supported: " + transport);
        }
        logger.trace("Starting socket creation loop");
        availableHostAddresses.forEach(addrRef -> {
            logger.debug("addr: {}", addrRef);
            // grab the address
            InetAddress addr = addrRef.getAddress();
            // ensure address is allowed first
            if (isAddressAllowed(addr)) {
                IceSocketWrapper iceSocket = null;
                try {
                    if (transport == Transport.UDP) {
                        iceSocket = createDatagramSocket(addr, preferredPort, minPort, maxPort);
                    } else if (transport == Transport.TCP) {
                        iceSocket = createServerSocket(addr, preferredPort, minPort, maxPort, component);
                    }
                    logger.debug("Socket created/bound: {}", iceSocket);
                    HostCandidate candidate = new HostCandidate(iceSocket, component, transport);
                    candidate.setVirtual(addrRef.isVirtual());
                    component.addLocalCandidate(candidate);
                    StunStack stunStack = candidate.getStunStack();
                    // add the socket wrapper to the stack which gets the bind and listening process started
                    stunStack.addSocket(iceSocket, null, true); // do socket binding
                    component.getComponentSocket().addSocketWrapper(iceSocket);
                } catch (Throwable t) {
                    // There seems to be a problem with this particular address let's just move on for now and hope we will find better
                    logger.warn("Socket creation failed on: {} transport: {}\nPorts - preferred: {} min: {} max: {}", addrRef, transport, preferredPort, minPort, maxPort, t);
                }
            } else {
                logger.debug("Address is not allowed: {}", addr);
            }
        });
        logger.trace("Exited socket creation loop");
        if (component.getLocalCandidateCount() == 0) {
            throw new IOException("Failed to bind even a single host candidate for component:" + component + " preferredPort=" + preferredPort + " minPort=" + minPort + " maxPort=" + maxPort);
        }
        logger.debug("Exit harvest port: {}", preferredPort);
    }

    /**
     * Returns a boolean value indicating whether ice4j should allocate a host candidate for the specified interface.
     *
     * @param iface The {@link NetworkInterface}.
     *
     * @return true if the {@link NetworkInterface} is listed in the com.red5pro.ice.harvest.ALLOWED_INTERFACES list. If that list
     * isn't defined, returns true if it's not listed in the com.red5pro.ice.harvest.BLOCKED_INTERFACES list. It returns
     * false otherwise.
     */
    static boolean isInterfaceAllowed(NetworkInterface iface) {
        if (iface == null) {
            throw new IllegalArgumentException("iface cannot be null");
        }
        // gp: use getDisplayName() on Windows and getName() on Linux. Also see NetworkAddressManagementServiceImpl in Jitsi.
        String ifName = (System.getProperty("os.name") == null || System.getProperty("os.name").startsWith("Windows")) ? iface.getDisplayName() : iface.getName();
        String[] allowedInterfaces = getAllowedInterfaces();
        // NOTE The blocked interfaces list is taken into account only if the allowed interfaces list is not defined.
        // getAllowedInterfaces returns null if the array is empty.
        if (allowedInterfaces != null) {
            // A list of allowed interfaces exists.
            return Arrays.asList(allowedInterfaces).contains(ifName);
        } else {
            // A list of allowed interfaces does not exist.
            String[] blockedInterfaces = getBlockedInterfaces();
            // getBlockedInterfaces returns null if the array is empty.
            if (blockedInterfaces != null) {
                // but a list of blocked interfaces exists.
                return !Arrays.asList(blockedInterfaces).contains(ifName);
            }
        }
        return true;
    }

    /**
     * Returns true if address is allowed to be used by this HostCandidateHarvester for the purposes of candidate allocation,
     * and false otherwise.
     *
     * An address is considered allowed, if
     * 1. It is not a loopback address.
     * 2. Either no addresses have explicitly been configured allowed (via the StackProperties.ALLOWED_ADDRESSES property), or address
     * is explicitly configured allowed.
     * 3. address is not explicitly configured blocked (via the StackProperties.BLOCKED_ADDRESSES property).
     *
     * @param address the address to check
     * @return true if address is allowed to be used by this HostCandidateHarvester.
     */
    static boolean isAddressAllowed(InetAddress address) {
        logger.debug("isAddressAllowed: {}", address);
        boolean ret = true;
        if (!address.isLoopbackAddress()) {
            List<InetAddress> allowed = getAllowedAddresses();
            List<InetAddress> blocked = getBlockedAddresses();
            if (allowed != null && allowed.size() > 0) {
                ret = allowed.contains(address);
            }
            if (blocked != null && blocked.size() > 0) {
                ret = ret && !blocked.contains(address);
            }        
        } else {
            // no loop back allowed
            ret = false;
        }
        return ret;
    }

    private IceSocketWrapper createServerSocket(InetAddress laddr, int port) throws IllegalArgumentException, IOException, BindException {
        logger.info("createServerSocket on {}", port);
        TransportAddress localAddress = new TransportAddress(laddr, port, Transport.TCP);
        // we successfully bound to the address so create a wrapper
        IceSocketWrapper sock = IceTransport.getIceHandler().lookupBinding(localAddress);
        // create a new socket since there isn't one registered for the local address
        if (sock == null) {
            sock = IceSocketWrapper.build(localAddress, null);
        }
        // return the socket
        return sock;
    }

    /**
     * Creates a ServerSocket and binds it to the specified localAddress and a port in the range specified by the minPort and maxPort parameters.
     *
     * @param laddr the address that we'd like to bind the socket on
     * @param preferredPort the port number that we should try to bind to first
     * @param minPort the port number where we should first try to bind before moving to the next one (i.e. minPort + 1)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception
     *
     * @return the newly created Socket
     *
     * @throws IllegalArgumentException if either minPort or maxPort is not a valid port number or if minPort &gt; maxPort
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets
     * @throws BindException if we couldn't find a free port between minPort and maxPort before reaching the maximum allowed number of retries
     */
    private IceSocketWrapper createServerSocket(InetAddress laddr, int preferredPort, int minPort, int maxPort, Component component) throws IllegalArgumentException, IOException, BindException {
        // make sure port numbers are valid
        checkPorts(preferredPort, minPort, maxPort);
        int bindRetries = StackProperties.getInt(StackProperties.BIND_RETRIES, StackProperties.BIND_RETRIES_DEFAULT_VALUE);
        int port = preferredPort;
        for (int i = 0; i < bindRetries; i++) {
            try {
                TransportAddress localAddress = new TransportAddress(laddr, port, Transport.TCP);
                // we successfully bound to the address so create a wrapper
                IceSocketWrapper sock = IceTransport.getIceHandler().lookupBinding(localAddress);
                // create a new socket since there isn't one registered for the local address
                if (sock == null) {
                    sock = IceSocketWrapper.build(localAddress, null);
                }
                // return the socket
                return sock;
            } catch (Exception se) {
                logger.warn("Retrying a bind because of a failure to bind to address {} and port {}", laddr, port, se);
            }
            port++;
            if (port > maxPort) {
                port = minPort;
            }
        }
        throw new BindException("Could not bind to any port between " + minPort + " and " + (port - 1));
    }

    private IceSocketWrapper createDatagramSocket(InetAddress laddr, int port) throws IllegalArgumentException, IOException, BindException {
        logger.info("createDatagramSocket on {}", port);
        TransportAddress localAddress = new TransportAddress(laddr, port, Transport.UDP);
        // we successfully bound to the address so create a wrapper
        IceSocketWrapper sock = IceTransport.getIceHandler().lookupBinding(localAddress);
        // create a new socket since there isn't one registered for the local address
        if (sock == null) {
            sock = IceSocketWrapper.build(localAddress, null);
        } else {
            localAddress = null;
        }
        // return the socket
        return sock;
    }

    /**
     * Creates a DatagramSocket and binds it to the specified localAddress and a port in the range specified by the
     * minPort and maxPort parameters. We first try to bind the newly created socket on the preferredPort port number
     * (unless it is outside the [minPort, maxPort] range in which case we first try the minPort) and then proceed incrementally upwards
     * until we succeed or reach the bind retries limit. If we reach the maxPort port number before the bind retries limit, we will then
     * start over again at minPort and keep going until we run out of retries.
     *
     * @param laddr the address that we'd like to bind the socket on
     * @param preferredPort the port number that we should try to bind to first
     * @param minPort the port number where we should first try to bind before moving to the next one (i.e. minPort + 1)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception
     *
     * @return the newly created DatagramSocket
     *
     * @throws IllegalArgumentException if either minPort or maxPort is not a valid port number or if minPort &gt; maxPort
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets
     * @throws BindException if we couldn't find a free port between minPort and maxPort before reaching the maximum allowed number of retries
     */
    private IceSocketWrapper createDatagramSocket(InetAddress laddr, int preferredPort, int minPort, int maxPort) throws IllegalArgumentException, IOException, BindException {
        // make sure port numbers are valid
        //checkPorts(preferredPort, minPort, maxPort);
        int mx = Math.min(maxPort, 65535);
        int mn = Math.max(minPort, 1024);
        if (preferredPort >= mn && preferredPort <= mx) {
            int bindRetries = StackProperties.getInt(StackProperties.BIND_RETRIES, StackProperties.BIND_RETRIES_DEFAULT_VALUE);
            int port = preferredPort;
            logger.info("Bind on {} retries:{}", port, bindRetries);
            for (int i = 0; i < bindRetries; i++) {
                try {
                    TransportAddress localAddress = new TransportAddress(laddr, port, Transport.UDP);
                    // we successfully bound to the address so create a wrapper
                    IceSocketWrapper sock = IceTransport.getIceHandler().lookupBinding(localAddress);
                    // create a new socket since there isn't one registered for the local address
                    if (sock == null) {
                        sock = IceSocketWrapper.build(localAddress, null);
                    } else {
                        localAddress = null;
                    }
                    // return the socket
                    return sock;
                } catch (Exception se) {
                    logger.warn("Retrying a bind because of a failure to bind to address {}:{}", laddr, port, se);
                }
                // increment for the next port attempt
                port++;
                if (port > maxPort) {
                    port = minPort;
                }
            }
            throw new BindException("Could not bind to any port between " + minPort + " and " + (port - 1));
        }
        throw new BindException("Could not bind preferred port, its not between " + minPort + " and " + maxPort);
    }

    /**
     * Checks if the different ports are correctly set. If not, throws an IllegalArgumentException.
     *
     * @param preferredPort the port number that we should try to bind to first
     * @param minPort the port number where we should first try to bind before moving to the next one (i.e. minPort + 1)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception
     * @throws IllegalArgumentException if either minPort or maxPort is not a valid port number or if minPort is
     * greater than maxPort
     */
    private void checkPorts(int preferredPort, int minPort, int maxPort) throws IllegalArgumentException {
        // make sure port numbers are valid
        if (!NetworkUtils.isValidPortNumber(minPort) || !NetworkUtils.isValidPortNumber(maxPort)) {
            throw new IllegalArgumentException("minPort (" + minPort + ") and maxPort (" + maxPort + ") should be integers between 1024 and 65535");
        }
        // make sure minPort comes before maxPort.
        if (minPort > maxPort) {
            throw new IllegalArgumentException("minPort (" + minPort + ") should be less than orequal to maxPort (" + maxPort + ")");
        }
        // if preferredPort is not in the allowed range, place it at min.
        if (minPort > preferredPort || preferredPort > maxPort) {
            throw new IllegalArgumentException("preferredPort (" + preferredPort + ") must be between minPort (" + minPort + ") and maxPort (" + maxPort + ")");
        }
    }

    /**
     * Returns the statistics describing how well the various harvests of this harvester went.
     *
     * @return The {@link HarvestStatistics} describing this harvester's harvests
     */
    public HarvestStatistics getHarvestStatistics() {
        return harvestStatistics;
    }

    /**
     * Initializes the host candidate interface filters stored in the com.red5pro.ice.harvest.ALLOWED_INTERFACES and
     * com.red5pro.ice.harvest.BLOCKED_INTERFACES properties.
     *
     * @throws java.lang.IllegalStateException if there were errors during host candidate interface filters initialization.
     */
    public static void initializeInterfaceFilters() {
        // We want this method to run only once.
        if (interfaceFiltersInitialized.compareAndSet(false, true)) {
            // Initialize the allowed interfaces array.
            allowedInterfaces = StackProperties.getStringArray(StackProperties.ALLOWED_INTERFACES, ";");
            // getStringArray returns null if the array is empty.
            if (allowedInterfaces != null) {
                // Validate the allowed interfaces array.
                // 1. Make sure the allowedInterfaces list contains interfaces that exist on the system.
                for (String iface : allowedInterfaces) {
                    try {
                        NetworkInterface.getByName(iface);
                    } catch (SocketException e) {
                        throw new IllegalStateException("there is no networkinterface with the name " + iface, e);
                    }
                }
                // the allowedInterfaces array is not empty and its items represent valid interfaces => there's at least one listening interface.
            } else {
                // NOTE The blocked interfaces list is taken into account only if the allowed interfaces list is not defined => initialize the
                // blocked interfaces only if the allowed interfaces list is not defined.
                // Initialize the blocked interfaces array.
                blockedInterfaces = StackProperties.getStringArray(StackProperties.BLOCKED_INTERFACES, ";");
                // getStringArray returns null if the array is empty.
                if (blockedInterfaces != null) {
                    // Validate the blocked interfaces array.
                    // 1. Make sure the blockedInterfaces list contains interfaces that exist on the system.
                    for (String iface : blockedInterfaces) {
                        try {
                            NetworkInterface.getByName(iface);
                        } catch (SocketException e) {
                            throw new IllegalStateException("there is no network interface with the name " + iface, e);
                        }
                    }
                    // 2. Make sure there's at least one allowed interface.
                    Enumeration<NetworkInterface> allInterfaces;
                    try {
                        allInterfaces = NetworkInterface.getNetworkInterfaces();
                    } catch (SocketException e) {
                        throw new IllegalStateException("could not get the list of the available network interfaces", e);
                    }
                    int count = 0;
                    while (allInterfaces.hasMoreElements()) {
                        allInterfaces.nextElement();
                        count++;
                    }
                    if (blockedInterfaces.length >= count) {
                        throw new IllegalStateException("all network interfaces are blocked");
                    }
                }
            }
        }
    }
}
