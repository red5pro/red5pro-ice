/* See LICENSE.md for license information */
package test.signalling;

import java.net.*;

/**
 * A simple signaling utility that we use for ICE tests.
 *
 * @author Emil Ivov
 */
public class Signalling
{
    /**
     * The socket where we send and receive signaling
     */
//    private final Socket signallingSocket;

//    private final SignallingCallback signallingCallback;

    /**
     * Creates a signaling instance over the specified socket.
     *
     * @param socket the socket that this instance should use for signalling
     */
    public Signalling(Socket socket, SignallingCallback signallingCallback)
    {
//        this.signallingSocket = socket;
//        this.signallingCallback = signallingCallback;
    }

    /**
     * Creates a server signalling object. The method will block until a
     * connection is actually received on
     *
     * @param socketAddress our bind address
     * @param signallingCallback the callback that we will deliver signalling
     * to.
     *
     * @return the newly created Signalling object
     *
     * @throws Throwable if anything goes wrong (which could happen with the
     * socket stuff).
     */
    public static Signalling createServerSignalling(
            InetSocketAddress socketAddress,
            SignallingCallback signallingCallback)
        throws Throwable
    {
//        ServerSocket serverSocket = new ServerSocket(socketAddress);
//        Signalling signalling = new Signalling(socketAddress, signallingCallback);
        return null;
    }
}
