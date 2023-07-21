/* See LICENSE.md for license information */
package com.red5pro.ice.harvest;

import com.red5pro.ice.TransportAddress;
import com.red5pro.ice.nio.IceTransport;
import com.red5pro.ice.socket.IceSocketWrapper;
import com.red5pro.ice.stunclient.SimpleAddressDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MappingCandidateHarvester} which uses a STUN servers to discover its public IP address.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public class StunMappingCandidateHarvester extends MappingCandidateHarvester {

    private static final Logger logger = LoggerFactory.getLogger(StunMappingCandidateHarvester.class);

    /**
     * The list of servers we will use to discover our public address.
     */
    private TransportAddress stunServerAddress;

    /**
     * Initializes a new {@link StunMappingCandidateHarvester} instance with a given local address and a STUN server address. Note that the actual
     * discovery of the public address needs to be initiated to a separate call to {@link #discover()}.
     * @param localAddress The local address.
     * @param stunServerAddress The address of the STUN server.
     */
    public StunMappingCandidateHarvester(TransportAddress localAddress, TransportAddress stunServerAddress) {
        face = localAddress;
        this.stunServerAddress = stunServerAddress;
    }

    /**
     * Attempts to discover the the public address (mask) via the STUN server.
     * Note that this will block until we either receive a response from the STUN server, or a timeout occurs.
     */
    public void discover() {
        try {
            SimpleAddressDetector sad = new SimpleAddressDetector(stunServerAddress);
            sad.start();
            // check for existing binding before creating a new one
            IceSocketWrapper localSocket = IceTransport.getIceHandler().lookupBinding(face);
            // create a new socket since there isn't one registered for the local address
            if (localSocket == null) {
                localSocket = IceSocketWrapper.build(face, null);
            }
            mask = sad.getMappingFor(localSocket);
            if (mask != null) {
                logger.info("Discovered public address {} from STUN server {} using local address {}", mask, stunServerAddress, face);
            }
        } catch (Exception exc) {
            //whatever happens, we just log
            logger.info("We failed to obtain addresses for the following reason", exc);
        }
    }
}
