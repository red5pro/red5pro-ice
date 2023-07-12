/* See LICENSE.md for license information */
package test.signalling;

/**
 * A simple signalling callback interface where we deliever newly received
 * signalling from the {@link Signalling}
 *
 * @author Emil Ivov
 */
public interface SignallingCallback
{
    /**
     * Processes the specified signalling string
     *
     * @param signalling the signalling string to process
     */
    public void onSignalling(String signalling);
}
