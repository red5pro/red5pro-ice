/* See LICENSE.md for license information */
package com.red5pro.ice;

/**
 * An enumeration of strategies for selecting which candidate pairs to
 * keep alive.
 */
public enum KeepAliveStrategy
{
    /**
     * Only keep alive the selected pair.
     */
    SELECTED_ONLY("selected_only"),

    /**
     * Keep alive the selected pair and any TCP pairs.
     */
    SELECTED_AND_TCP("selected_and_tcp"),

    /**
     * Keep alive all succeeded pairs.
     */
    ALL_SUCCEEDED("all_succeeded");

    private String name;

    KeepAliveStrategy(String name)
    {
        this.name = name;
    }

    /**
     * @return the {@link KeepAliveStrategy} with name equal to the given
     * string, or {@code null} if there is no such strategy.
     * @param string the name of the strategy.
     */
    public static KeepAliveStrategy fromString(String string)
    {
        for (KeepAliveStrategy strategy : KeepAliveStrategy.values())
        {
            if (strategy.name.equals(string))
                return strategy;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return name;
    }
}
