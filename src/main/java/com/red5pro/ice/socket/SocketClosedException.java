/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal. Copyright @ 2015-2016 Atlassian Pty Ltd Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */
package com.red5pro.ice.socket;

import java.net.*;

/**
 * An exception that indicates that a socket is closed.
 */
public class SocketClosedException extends SocketException {
    private static final long serialVersionUID = -2571217415633483512L;

    /**
     * Initializes a new {@link SocketClosedException}.
     */
    public SocketClosedException() {
        // Keep the same message as the one used by jdk, since existing code
        // might be matching against the string.
        super("Socket closed");
    }

    /**
     * Initializes a new {@link SocketClosedException}.
     * 
     * @param message
     */
    public SocketClosedException(String message) {
        super(message);
    }

}
