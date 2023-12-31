/* See LICENSE.md for license information */
package com.red5pro.ice;

/**
 * Contains the nomination strategies currently supported by this
 * implementation's {@link DefaultNominator} class. Applications can either
 * pick one of these strategies or select NONE in case they want to
 * handle nominations themselves.
 * <p>
 * Note that NominationStrategies are an ice4j concept and they are not
 * mentioned in RFC 5245.
 *
 * @author Emil Ivov
 */
public enum NominationStrategy {
    /**
     * Indicates that ice4j's nominator should nominate valid pairs and that
     * the application will be handling this.
     */
    NONE("None"),

    /**
     * The strategy consists in nominating the first candidate pair that's
     * confirmed as valid.
     */
    NOMINATE_FIRST_VALID("NominateFirstValid"),

    /**
     * The strategy consists in nominating the highest priority valid pair once
     * all checks in a list have completed.
     */
    NOMINATE_HIGHEST_PRIO("NominateHighestPriority"),

    /**
     * The strategy consists in nominating the first host or server reflexive
     * that's confirmed as valid pair. When a relayed candidate pair is
     * validated first, a timer is armed and only if no host or server
     * reflexive pair gets validated prior to timeout, the relayed ones
     * gets nominated.
     */
    NOMINATE_FIRST_HOST_OR_REFLEXIVE_VALID("NominateFirstHostOrReflexiveValid"),

    /**
     * The strategy consists in nominating the pair that showed the best
     * shortest round trip time once all checks in a list completed.
     */
    NOMINATE_BEST_RTT("NominateBestRTT");

    /**
     * The name of this strategy.
     */
    private final String strategyName;

    /**
     * Creates a NominationStrategy instance with the specified name.
     *
     * @param name the name of the NominationStrategy that we'd like
     * to create.
     */
    private NominationStrategy(String name) {
        this.strategyName = name;
    }

    /**
     * Returns the name of this NominationStrategy.
     *
     * @return the name of this NominationStrategy.
     */
    @Override
    public String toString() {
        return strategyName;
    }
}
