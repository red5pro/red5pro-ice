/* See LICENSE.md for license information */
package com.red5pro.ice.harvest;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.red5pro.ice.Agent;
import com.red5pro.ice.CandidateExtendedType;
import com.red5pro.ice.CandidateTcpType;
import com.red5pro.ice.Component;
import com.red5pro.ice.IceMediaStream;
import com.red5pro.ice.IceProcessingState;
import com.red5pro.ice.LocalCandidate;
import com.red5pro.ice.ServerReflexiveCandidate;
import com.red5pro.ice.StackProperties;
import com.red5pro.ice.TcpHostCandidate;
import com.red5pro.ice.Transport;
import com.red5pro.ice.TransportAddress;

/**
 * An implementation of {@link AbstractTcpListener} which acts as a {@link CandidateHarvester}. Sessions are accepted if their ufrag matches
 * a {@link Component} registered with this harvester, and the known addresses of {@link AbstractTcpListener} are added as local host candidates (with type
 * "tcp" and tcptype "passive") when harvesting.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 */
public class TcpHarvester extends AbstractTcpListener implements CandidateHarvester {

    private static final Logger logger = Logger.getLogger(TcpHarvester.class.getName());

    /**
     * The constant which specifies how often to perform purging on {@link #components}.
     */
    private static final int PURGE_INTERVAL = 20;

    /**
     * Maps a local "ufrag" to the single Component instance with that "ufrag".
     *
     * We only keep weak references, because we do not want to prevent Components from being freed.
     */
    private final Map<String, WeakReference<Component>> components = new HashMap<>();

    /**
     * Maps a public address to a local address. Used to add additional candidates with type "srflx" when harvesting.
     */
    private final Map<InetAddress, InetAddress> mappedAddresses = new HashMap<>();

    /**
     * Sets of additional ports, for which server reflexive candidates will be added.
     */
    private final Set<Integer> mappedPorts = new HashSet<>();

    /**
     * A counter used to decide when to purge {@link #components}.
     */
    private int purgeCounter = 0;

    /**
     * Whether to advertise candidates with type "ssltcp" (if true) or "tcp" (if false).
     */
    private final boolean ssltcp;

    /**
     * Manages statistics about harvesting time.
     */
    private HarvestStatistics harvestStatistics = new HarvestStatistics();

    /**
     * Initializes a new TcpHarvester, which is to listen on port number port on all IP addresses on all available interfaces.
     *
     * @param port the port to listen on.
     * @throws IOException when {@link StackProperties#ALLOWED_ADDRESSES} or {@link StackProperties#BLOCKED_ADDRESSES} contains invalid values, or
     * if an I/O error occurs.
     */
    public TcpHarvester(int port) throws IOException {
        super(port);
        this.ssltcp = false;
        addMappedAddresses();
    }

    /**
     * Initializes a new TcpHarvester, which is to listen on port number port on all IP addresses on all available interfaces.
     *
     * @param port the port to listen on.
     * @param ssltcp true to use ssltcp; otherwise, false
     * @throws IOException when {@link StackProperties#ALLOWED_ADDRESSES} or {@link StackProperties#BLOCKED_ADDRESSES} contains invalid values, or
     * if an I/O error occurs.
     */
    public TcpHarvester(int port, boolean ssltcp) throws IOException {
        super(port, Collections.list(NetworkInterface.getNetworkInterfaces()));
        this.ssltcp = ssltcp;
        addMappedAddresses();
    }

    /**
     * Initializes a new TcpHarvester, which is to listen on port number port on all the IP addresses on the specified NetworkInterfaces.
     *
     * @param port the port to listen on.
     * @param interfaces the interfaces to listen on.
     * @param ssltcp true to use ssltcp; otherwise, false
     * @throws IOException when {@link StackProperties#ALLOWED_ADDRESSES} or {@link StackProperties#BLOCKED_ADDRESSES} contains invalid values, or
     * if an I/O error occurs.
     */
    public TcpHarvester(int port, List<NetworkInterface> interfaces, boolean ssltcp) throws IOException {
        super(port, interfaces);
        this.ssltcp = ssltcp;
        addMappedAddresses();
    }

    /**
     * Initializes a new TcpHarvester, which is to listen on the specified list of TransportAddresses.
     *
     * @param transportAddresses the transport addresses to listen on.
     * @throws IOException when {@link StackProperties#ALLOWED_ADDRESSES} or {@link StackProperties#BLOCKED_ADDRESSES} contains invalid values, or
     * if an I/O error occurs.
     */
    public TcpHarvester(List<TransportAddress> transportAddresses) throws IOException {
        super(transportAddresses);
        this.ssltcp = false;
        addMappedAddresses();
    }

    /**
     * Initializes a new TcpHarvester, which is to listen on the specified list of TransportAddresses.
     *
     * @param transportAddresses the transport addresses to listen on.
     * @param ssltcp true to use ssltcp; otherwise, false
     * @throws IOException when {@link StackProperties#ALLOWED_ADDRESSES} or {@link StackProperties#BLOCKED_ADDRESSES} contains invalid values, or
     * if an I/O error occurs.
     */
    public TcpHarvester(List<TransportAddress> transportAddresses, boolean ssltcp) throws IOException {
        super(transportAddresses);
        this.ssltcp = ssltcp;
        addMappedAddresses();
    }

    /**
     * Adds the mapped addresses known from {@link MappingCandidateHarvesters}.
     */
    private void addMappedAddresses() {
        for (MappingCandidateHarvester harvester : MappingCandidateHarvesters.getHarvesters()) {
            addMappedAddress(harvester.getMask().getAddress(), harvester.getFace().getAddress());
        }
    }

    /**
     * Adds a mapping between publicAddress and localAddress.
     * This means that on harvest, along with any host candidates that have publicAddress, a server reflexive candidate will be added (with
     * the same port as the host candidate).
     *
     * @param publicAddress the public address.
     * @param localAddress the local address.
     */
    public void addMappedAddress(InetAddress publicAddress, InetAddress localAddress) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Adding a mapped address: " + localAddress + " => " + publicAddress);
        }
        mappedAddresses.put(publicAddress, localAddress);
    }

    /**
     * Adds port as an additional port. When harvesting, additional server reflexive candidates will be added with this port.
     *
     * @param port the port to add.
     */
    public void addMappedPort(int port) {
        mappedPorts.add(port);
    }

    /**
     * Creates and returns the list of LocalCandidates which are to be added by this TcpHarvester to a specific Component.
     *
     * @param component the Component for which to create candidates.
     * @return the list of LocalCandidates which are to be added by this TcpHarvester to a specific Component.
     */
    private List<LocalCandidate> createLocalCandidates(Component component) {
        List<TcpHostCandidate> hostCandidates = new LinkedList<>();
        // Add the host candidates for the addresses we really listen on
        for (TransportAddress transportAddress : localAddresses) {
            TcpHostCandidate candidate = new TcpHostCandidate(transportAddress, component);
            candidate.setTcpType(CandidateTcpType.PASSIVE);
            if (ssltcp) {
                candidate.setSSL(true);
            }
            hostCandidates.add(candidate);
        }
        // Add srflx candidates for any mapped addresses
        List<LocalCandidate> mappedCandidates = new LinkedList<>();
        for (Map.Entry<InetAddress, InetAddress> mapping : mappedAddresses.entrySet()) {
            InetAddress localAddress = mapping.getValue();
            for (TcpHostCandidate base : hostCandidates) {
                TransportAddress baseTransportAddress = base.getTransportAddress();
                if (localAddress.equals(baseTransportAddress.getAddress())) {
                    InetAddress publicAddress = mapping.getKey();
                    ServerReflexiveCandidate mappedCandidate = new ServerReflexiveCandidate(new TransportAddress(publicAddress, baseTransportAddress.getPort(), Transport.TCP), base, base.getStunServerAddress(), CandidateExtendedType.STATICALLY_MAPPED_CANDIDATE);
                    if (base.isSSL()) {
                        mappedCandidate.setSSL(true);
                    }
                    mappedCandidate.setTcpType(CandidateTcpType.PASSIVE);
                    mappedCandidates.add(mappedCandidate);
                }
            }
        }
        // Add srflx candidates for mapped ports
        List<LocalCandidate> portMappedCandidates = new LinkedList<>();
        for (TcpHostCandidate base : hostCandidates) {
            for (Integer port : mappedPorts) {
                ServerReflexiveCandidate portMappedCandidate = new ServerReflexiveCandidate(new TransportAddress(base.getTransportAddress().getAddress(), port, Transport.TCP), base, base.getStunServerAddress(), CandidateExtendedType.STATICALLY_MAPPED_CANDIDATE);
                if (base.isSSL()) {
                    portMappedCandidate.setSSL(true);
                }
                portMappedCandidate.setTcpType(CandidateTcpType.PASSIVE);
                portMappedCandidates.add(portMappedCandidate);
            }
        }
        // Mapped ports for mapped addresses
        for (LocalCandidate mappedCandidate : mappedCandidates) {
            TcpHostCandidate base = (TcpHostCandidate) mappedCandidate.getBase();
            for (Integer port : mappedPorts) {
                ServerReflexiveCandidate portMappedCandidate = new ServerReflexiveCandidate(new TransportAddress(mappedCandidate.getTransportAddress().getAddress(), port, Transport.TCP), base, base.getStunServerAddress(), CandidateExtendedType.STATICALLY_MAPPED_CANDIDATE);
                if (base.isSSL()) {
                    portMappedCandidate.setSSL(true);
                }
                portMappedCandidate.setTcpType(CandidateTcpType.PASSIVE);
                portMappedCandidates.add(portMappedCandidate);
            }
        }
        LinkedList<LocalCandidate> allCandidates = new LinkedList<>();
        allCandidates.addAll(hostCandidates);
        allCandidates.addAll(mappedCandidates);
        allCandidates.addAll(portMappedCandidates);
        return allCandidates;
    }

    /**
     * Returns the Component instance, if any, for a given local &quot;ufrag&quot;.
     *
     * @param localUfrag the local &quot;ufrag&quot;
     * @return the Component instance, if any, for a given local &quot;ufrag&quot;.
     */
    private Component getComponent(String localUfrag) {
        synchronized (components) {
            WeakReference<Component> wr = components.get(localUfrag);
            if (wr != null) {
                Component component = wr.get();
                if (component == null) {
                    components.remove(localUfrag);
                }
                return component;
            }
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Saves a (weak) reference to Component, so that it can be notified if/when a socket for one of it LocalCandidates is accepted.
     * <p>
     * The method does not perform any network operations and should return quickly.
     * <br>
     */
    @Override
    public Collection<LocalCandidate> harvest(Component component) {
        IceMediaStream stream = component.getParentStream();
        Agent agent = stream.getParentAgent();
        if (stream.getComponentCount() != 1 || agent.getStreamCount() != 1) {
            // TcpHarvester only works with streams with a single component, and agents with a single stream. This is because we use the local "ufrag" to de-multiplex the
            // accept()-ed sockets between the known components.
            logger.info("More than one Component for an Agent, cannot harvest.");
            return new LinkedList<>();
        }
        List<LocalCandidate> candidates = createLocalCandidates(component);
        for (LocalCandidate candidate : candidates) {
            component.addLocalCandidate(candidate);
        }
        synchronized (components) {
            components.put(agent.getLocalUfrag(), new WeakReference<>(component));
            purgeComponents();
        }
        return candidates;
    }

    /**
     * Removes entries from {@link #components} for which the WeakReference has been cleared.
     */
    private void purgeComponents() {
        ++purgeCounter;
        if (purgeCounter % PURGE_INTERVAL == 0) {
            synchronized (components) {
                for (Iterator<WeakReference<Component>> i = components.values().iterator(); i.hasNext();) {
                    if (i.next().get() == null) {
                        i.remove();
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void acceptSession(Socket socket, String ufrag, DatagramPacket pushback) throws IOException, IllegalStateException {
        Component component = getComponent(ufrag);
        if (component == null) {
            throw new IllegalStateException("No component found for ufrag " + ufrag);
        }
        addSocketToComponent(socket, component, pushback);
    }

    /**
     * Makes socket available to component and pushes back datagramPacket into the STUN socket.
     *
     * @param socket the Socket.
     * @param component the Component.
     * @param datagramPacket the DatagramPacket to push back.
     * @throws IllegalStateException if the ICE state is incorrect, or an appropriate candidate could not be found.
     * @throws IOException if creation of some of the required socket instances failed.
     */
    private void addSocketToComponent(Socket socket, Component component, DatagramPacket datagramPacket) throws IOException, IllegalStateException {
        IceProcessingState state = component.getParentStream().getParentAgent().getState();
        if (logger.isLoggable(Level.FINE) && !IceProcessingState.WAITING.equals(state) && !IceProcessingState.RUNNING.equals(state)) {
            logger.fine("Adding a socket to an Agent in state " + state);
        }
        /*
         * // Socket to add to the candidate IceSocketWrapper candidateSocket = new IceTcpSocketWrapper(socket.getChannel()); TcpHostCandidate candidate = findCandidate(component,
         * socket); if (candidate == null) { throw new IOException("Failed to find the local candidate for socket: " + socket); }
         * component.getParentStream().getParentAgent().getStunStack().addSocket(candidateSocket); candidate.addSocket(candidateSocket); // TODO: Maybe move this code to the
         * candidate. component.getComponentSocket().setSocket(candidateSocket); // the socket is not our responsibility anymore. It is up to the candidate/component to close/free
         * it.
         */
    }

    /**
     * Searches among the local candidates of Component for a TcpHostCandidate with the same transport address as the
     * local transport address of socket.
     *
     * We expect to find such a candidate, which has been added by this TcpHarvester while harvesting.
     *
     * @param component the Component to search.
     * @param socket the Socket to match the local transport address of.
     * @return a TcpHostCandidate among the local candidates of Component with the same transport address as the local
     * address of Socket, or null if no such candidate exists.
     */
    @SuppressWarnings("unused")
    private TcpHostCandidate findCandidate(Component component, Socket socket) {
        InetAddress localAddress = socket.getLocalAddress();
        int localPort = socket.getLocalPort();
        for (LocalCandidate candidate : component.getLocalCandidates()) {
            TransportAddress transportAddress = candidate.getTransportAddress();
            if (candidate instanceof TcpHostCandidate && Transport.TCP.equals(transportAddress.getTransport()) && localPort == transportAddress.getPort() && localAddress.equals(transportAddress.getAddress())) {
                return (TcpHostCandidate) candidate;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHostHarvester() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public HarvestStatistics getHarvestStatistics() {
        return harvestStatistics;
    }

}
