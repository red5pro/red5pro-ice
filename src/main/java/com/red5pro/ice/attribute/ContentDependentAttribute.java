/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

import com.red5pro.ice.stack.*;

/**
 * ContentDependentAttributes have a value that depend on the content
 * of the message. The {@link MessageIntegrityAttribute} and {@link
 * FingerprintAttribute} are two such attributes.
 * <p>
 * Rather than encoding them via the standard {@link Attribute#encode()} method,
 * the stack would use the one from this interface.
 * </p>
 *
 * @author Emil Ivov
 */
public interface ContentDependentAttribute {
    /**
     * Returns a binary representation of this attribute.
     *
     * @param stunStack the StunStack in the context of which the
     * request to encode this ContentDependentAttribute is being made
     * @param content the content of the message that this attribute will be
     * transported in
     * @param offset the content-related offset where the actual
     * content starts.
     * @param length the length of the content in the content array.
     *
     * @return a binary representation of this attribute valid for the message
     * with the specified content.
     */
    public byte[] encode(StunStack stunStack, byte[] content, int offset, int length);
}
