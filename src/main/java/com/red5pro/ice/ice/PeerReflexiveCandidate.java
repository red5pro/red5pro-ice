/* See LICENSE.md for license information */
package com.red5pro.ice.ice;

import com.red5pro.ice.*;
import com.red5pro.ice.socket.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.red5pro.ice.Transport;

/**
 * Peer Reflexive Candidates are candidates whose IP address and port are a binding explicitly allocated by a NAT for an agent when it sent a STUN
 * Binding request through the NAT to its peer.
 * <p>
 * Peer Reflexive Candidates are generally allocated by NATs with endpoint dependent mapping also known as Symmetric NATs. PeerReflexiveCandidates
 * are generally preferred to relayed ones. RFC 5245 explains this with better security ... although simply avoiding a relay would probably be
 * enough of a reason for many.
 *
 * @author Emil Ivov
 */
public class PeerReflexiveCandidate extends LocalCandidate {

    private static final Logger logger = LoggerFactory.getLogger(PeerReflexiveCandidate.class);

    /**
     * Creates a PeerReflexiveCandidate instance for the specified transport address and properties.
     *
     * @param transportAddress  the transport address that this candidate is encapsulating
     * @param parentComponent the Component that this candidate belongs to
     * @param base the base of a peer reflexive candidate base is the local candidate of the candidate pair from which the STUN check was sent
     * @param priority the priority of the candidate
     */
    public PeerReflexiveCandidate(TransportAddress transportAddress, Component parentComponent, LocalCandidate base, long priority) {
        super(transportAddress, parentComponent, CandidateType.PEER_REFLEXIVE_CANDIDATE, CandidateExtendedType.STUN_PEER_REFLEXIVE_CANDIDATE, base);
        super.setBase(base);
        super.priority = priority;
        if (transportAddress.getTransport() != Transport.UDP) {
            super.setTcpType(base.getTcpType());
        }
        logger.debug("ctor - addr: {} comp: {} related candidate: {}", transportAddress, parentComponent, base);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IceSocketWrapper getCandidateIceSocketWrapper() {
        return getBase().getCandidateIceSocketWrapper();
    }
}
