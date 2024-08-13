/* See LICENSE.md for license information */
package com.red5pro.ice.security;

/**
 * The {@link CredentialsAuthority} interface is implemented by applications
 * in order to allow the stack to verify the integrity of incoming messages
 * containing the MessageIntegrityAttribute.
 *
 * @author Emil Ivov
 */
public interface CredentialsAuthority {
    /**
     * Returns the key (password) that corresponds to the specified local
     * username or user frag,  an empty array if there was no password for that
     * username or null if the username is not a local user name
     * recognized by this CredentialsAuthority.
     *
     * @param username the local user name or user frag whose credentials we'd
     * like to obtain.
     *
     * @return the key (password) that corresponds to the specified local
     * username or user frag,  an empty array if there was no password for that
     * username or null if the username is not a local user name
     * recognized by this CredentialsAuthority.
     */
    public byte[] getLocalKey(String username);

    /**
     * Returns the key (password) that corresponds to the specified remote
     * username or user frag,  an empty array if there was no password for that
     * username or null if the username is not a remote user name
     * recognized by this CredentialsAuthority.
     *
     * @param username the remote user name or user frag whose credentials we'd
     * like to obtain.
     * @param media the media name that we want to get remote key.
     *
     * @return the key (password) that corresponds to the specified remote
     * username or user frag,  an empty array if there was no password for that
     * username or null if the username is not a remote user name
     * recognized by this CredentialsAuthority.
     */
    public byte[] getRemoteKey(String username, String media);

    /**
     * Verifies whether username is currently known to this authority
     * and returns true if so. Returns false otherwise.
     *
     * @param username the user name whose validity we'd like to check.
     *
     * @return true if username is known to this
     * CredentialsAuthority and false otherwise.
     */
    public boolean checkLocalUserName(String username);
}
