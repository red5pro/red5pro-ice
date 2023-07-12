/* See LICENSE.md for license information */
package com.red5pro.ice.stack;

/**
 * The interface which interested implementers will use in order
 * to track and log packets send and received by this stack.
 * 
 * @author Damian Minkov
 */
public interface PacketLogger
{
    /**
     * Logs a incoming or outgoing packet.
     *
     * @param sourceAddress the source address of the packet.
     * @param sourcePort the source port.
     * @param destinationAddress the destination address of the packet.
     * @param destinationPort the destination port.
     * @param packetContent the content of the packet.
     * @param sender whether or not we are sending the packet.
     */
    public void logPacket(
            byte[] sourceAddress,
            int sourcePort,
            byte[] destinationAddress,
            int destinationPort,
            byte[] packetContent,
            boolean sender);

    /**
     * Checks whether the logger is enabled. 
     * @return true if the logger is enabled, false
     *  otherwise.
     */
    public boolean isEnabled();
}
