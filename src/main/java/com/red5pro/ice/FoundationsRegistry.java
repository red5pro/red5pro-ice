/* See LICENSE.md for license information */
package com.red5pro.ice;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * We FoundationsRegistrys to keep track of and generate new foundations within the lifetime of a single Agent.
 *
 * @author Emil Ivov
 */
public class FoundationsRegistry {

    /**
     * The foundation number that was last assigned to a Candidate
     */
    private AtomicInteger lastAssignedFoundation = new AtomicInteger(0);

    /**
     * The foundation number that was last assigned to a PEER-REFLEXIVE RemoteCandidate
     */
    private AtomicInteger lastAssignedRemoteFoundation = new AtomicInteger(10000);

    /**
     * Contains mappings between a type+baseIP+server+transport Strings and the foundation that has been assigned to them.
     */
    private ConcurrentMap<String, String> foundations = new ConcurrentHashMap<>();

    /**
     * Assigns to candidate the foundation that corresponds to its base, type and transport properties or a new one if no foundation has
     * been generated yet for the specific combination.
     *
     * @param candidate the Candidate that we'd like to assign a foundation to
     */
    public void assignFoundation(Candidate<?> candidate) {
        //create the foundation key String
        CandidateType candidateType = candidate.getType();
        String type = candidateType.toString();
        String base = candidate.getBase().getTransportAddress().getHostAddress();
        String server;
        switch (candidateType) {
            case SERVER_REFLEXIVE_CANDIDATE:
                TransportAddress serverAddress = candidate.getStunServerAddress();
                server = (serverAddress == null) ? "" : serverAddress.getHostAddress();
                break;
            case RELAYED_CANDIDATE:
                server = candidate.getRelayServerAddress().getHostAddress();
                break;
            default:
                server = null;
                break;
        }
        String transport = candidate.getTransport().toString();
        StringBuilder foundationStringBuff = new StringBuilder(type);
        foundationStringBuff.append(base);
        if (server != null) {
            foundationStringBuff.append(server);
        }
        foundationStringBuff.append(transport);
        String foundationString = foundationStringBuff.toString();
        String foundationValue = foundations.get(foundationString);
        //obtain a new foundation number if we don't have one for this kind of candidates.
        if (foundationValue == null) {
            foundationValue = Integer.toString(lastAssignedFoundation.incrementAndGet());
            foundations.put(foundationString, foundationValue);
        }
        candidate.setFoundation(foundationValue);
    }

    /**
     * Returns an (as far as you care) random foundation that could be assigned to a learned PEER-REFLEXIVE candidate.
     *
     * @return  a foundation String that could be assigned to a learned PEER-REFLEXIVE candidate.
     */
    public String obtainFoundationForPeerReflexiveCandidate() {
        return Integer.toString(lastAssignedRemoteFoundation.getAndIncrement());
    }

    /**
     * Returns the number of foundation Strings that are currently tracked by the registry.
     *
     * @return the number of foundation Strings that are currently tracked by this registry.
     */
    public int size() {
        return foundations.size();
    }
}
