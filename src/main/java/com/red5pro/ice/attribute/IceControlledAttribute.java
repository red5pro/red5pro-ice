/* See LICENSE.md for license information */
package com.red5pro.ice.attribute;

/**
 * An {@link IceControlAttribute} implementation representing the
 * ICE-CONTROLLED ICE {@link Attribute}s.
 */
public final class IceControlledAttribute extends IceControlAttribute {
    /**
     * Constructs an ICE-CONTROLLING attribute.
     */
    public IceControlledAttribute() {
        super(false);
    }
}
