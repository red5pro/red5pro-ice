/* See LICENSE.md for license information */
package com.red5pro.ice;

import com.red5pro.ice.harvest.StunCandidateHarvest;
import com.red5pro.ice.socket.IceSocketWrapper;

/**
 * ServerReflexiveCandidates are candidates whose IP address and port are a binding allocated by a NAT for an agent when it sent a packet through
 * the NAT to a server. ServerReflexiveCandidates can be learned by STUN servers using the Binding Request, or TURN servers, which provides both
 * a Relayed and Server Reflexive candidate.
 * <br>
 * This class does not contain a socket itself and in order to send bytes over the network, one has to retrieve the socket of its base.
 *
 * @author Emil Ivov
 */
public class ServerReflexiveCandidate extends LocalCandidate {

    /**
     * The STUN candidate harvest.
     */
    private final StunCandidateHarvest stunHarvest;

    /**
     * Creates a ServerReflexiveCandidate for the specified transport address, and base.
     *
     * @param address the {@link TransportAddress} that this Candidate is representing
     * @param base the {@link HostCandidate} that this server reflexive candidate was obtained through
     * @param stunSrvrAddr the {@link TransportAddress} of the stun server that reflected this candidate
     * @param extendedType The type of method used to discover this candidate ("host", "upnp", "stun peer reflexive", "stun server reflexive", "turn
     * relayed", "google turn relayed", "google tcp turn relayed" or "jingle node")
     */
    public ServerReflexiveCandidate(TransportAddress address, HostCandidate base, TransportAddress stunSrvrAddr, CandidateExtendedType extendedType) {
        this(address, base, stunSrvrAddr, null, extendedType);
    }

    /**
     * Creates a ServerReflexiveCandidate for the specified transport address, and base.
     *
     * @param address the {@link TransportAddress} that this Candidate is representing
     * @param base the {@link HostCandidate} that this server reflexive candidate was obtained through
     * @param stunSrvrAddr the {@link TransportAddress} of the stun server that reflected this candidate
     * @param stunHarvest the {@link StunCandidateHarvest}
     * @param extendedType The type of method used to discover this candidate ("host", "upnp", "stun peer reflexive", "stun server reflexive", "turn
     * relayed", "google turn relayed", "google tcp turn relayed" or "jingle node")
     */
    public ServerReflexiveCandidate(TransportAddress address, HostCandidate base, TransportAddress stunSrvrAddr, StunCandidateHarvest stunHarvest, CandidateExtendedType extendedType) {
        super(address, base.getParentComponent(), CandidateType.SERVER_REFLEXIVE_CANDIDATE, extendedType, base);
        setBase(base);
        setStunServerAddress(stunSrvrAddr);
        this.stunHarvest = stunHarvest;
        computePriority();
    }

    /** {@inheritDoc} */
    @Override
    public IceSocketWrapper getCandidateIceSocketWrapper() {
        return getBase().getCandidateIceSocketWrapper();
    }

    /**
     * Frees resources allocated by this candidate such as itsDatagramSocket, for example. The socket of this
     * LocalCandidate is closed only if it is not the socket of the base of this LocalCandidate.
     */
    @Override
    public void free() {
        super.free();
        if (stunHarvest != null) {
            stunHarvest.close();
        }
    }
}
