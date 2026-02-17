package com.red5pro.ice;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class contains a number of property names and their default values that we use to configure the behavior of the ice4j stack.
 *
 * @author Emil Ivov
 */
public class StackProperties {

    private static final Logger logger = LoggerFactory.getLogger(StackProperties.class);

    /**
     * The name of the property containing the number of binds that we should should execute in case a port is already bound to (each retry would be on
     * a new random port).
     */
    public static final String BIND_RETRIES = "com.red5pro.ice.BIND_RETRIES";

    /**
     * The default number of binds that we would try implementation should execute in case a port is already bound to (each
     * retry would be on a different port).
     */
    public static final int BIND_RETRIES_DEFAULT_VALUE = 3;

    /**
     * The name of the property that tells if we should bind to the wildcard address instead of the (usually more specific) harvest candidate
     * addresses.
     *
     * The wildcard is a special local IP address. It usually means "any".
     */
    public static final String BIND_WILDCARD = "com.red5pro.ice.BIND_WILDCARD";

    /**
     * How often a STUN Binding request used for consent freshness check will be sent(value in milliseconds).
     */
    public static final String CONSENT_FRESHNESS_INTERVAL = "com.red5pro.ice.CONSENT_FRESHNESS_INTERVAL";

    /**
     * The maximum number of retransmissions of a STUN Binding request without a valid STUN Binding response after which consent freshness is to be
     * considered unconfirmed according to &quot;STUN Usage for Consent Freshness&quot;.
     */
    public static final String CONSENT_FRESHNESS_MAX_RETRANSMISSIONS = "com.red5pro.ice.CONSENT_FRESHNESS_MAX_RETRANSMISSIONS";

    /**
     * The number of milliseconds without a valid STUN Binding response after which a STUN Binding request is to be retransmitted according to
     * &quot;STUN Usage for Consent Freshness&quot;. This is the final value for the back-off strategy.
     * {@link #CONSENT_FRESHNESS_ORIGINAL_WAIT_INTERVAL} defines the initial interval for the first request sent. Value in milliseconds.
     */
    public static final String CONSENT_FRESHNESS_MAX_WAIT_INTERVAL = "com.red5pro.ice.CONSENT_FRESHNESS_MAX_WAIT_INTERVAL";

    /**
     * The number of milliseconds without a valid STUN Binding response after which a STUN Binding request is to be retransmitted according to
     * &quot;STUN Usage for Consent Freshness&quot;. This is the original value or back-off strategy. {@link #CONSENT_FRESHNESS_MAX_WAIT_INTERVAL} sets
     * the upper limit. Value in milliseconds.
     */
    public static final String CONSENT_FRESHNESS_ORIGINAL_WAIT_INTERVAL = "com.red5pro.ice.CONSENT_FRESHNESS_WAIT_INTERVAL";

    /**
     * The number of milliseconds a client transaction should wait before retransmitting, after it has sent a request for the first time.
     */
    public static final String FIRST_CTRAN_RETRANS_AFTER = "com.red5pro.ice.FIRST_CTRAN_RETRANS_AFTER";

    /**
     * The maximum number of milliseconds that an exponential client retransmission timer can reach.
     */
    public static final String MAX_CTRAN_RETRANS_TIMER = "com.red5pro.ice.MAX_CTRAN_RETRANS_TIMER";

    /**
     * Indicates whether a client transaction should be kept after a response is received rather than destroying it which is the default.
     */
    public static final String KEEP_CRANS_AFTER_A_RESPONSE = "com.red5pro.ice.KEEP_CRANS_AFTER_A_RESPONSE";

    /**
     * The maximum number of retransmissions a client transaction should send.
     */
    public static final String MAX_CTRAN_RETRANSMISSIONS = "com.red5pro.ice.MAX_RETRANSMISSIONS";

    /**
     * The name of the System property that allows us to set a custom maximum for check list sizes.
     */
    public static final String MAX_CHECK_LIST_SIZE = "com.red5pro.ice.MAX_CHECK_LIST_SIZE";

    /**
     * The value of the SOFTWARE attribute that ice4j should include in all outgoing messages.
     */
    public static final String SOFTWARE = "com.red5pro.ice.SOFTWARE";

    /**
     * The name of the property that tells the stack whether or not it should let the application see retransmissions of incoming requests.
     */
    public static final String PROPAGATE_RECEIVED_RETRANSMISSIONS = "com.red5pro.ice.PROPAGATE_RECEIVED_RETRANSMISSIONS";

    /**
     * A property that allows us to specify whether we would expect link local IPv6 addresses to be able to reach globally routable ones.
     */
    public static final String ALLOW_LINK_TO_GLOBAL_REACHABILITY = "com.red5pro.ice.ALLOW_LINK_TO_GLOBAL_REACHABILITY";

    /**
     * The name of the property that allows us to tell the stack to always sign STUN messages with a FINGERPRINT attribute.
     */
    public static final String ALWAYS_SIGN = "com.red5pro.ice.ALWAYS_SIGN";

    /**
     * Tells the stack whether to reject all incoming requests that do not carry a MESSAGE-INTEGRITY header.
     */
    public static final String REQUIRE_MESSAGE_INTEGRITY = "com.red5pro.ice.REQUIRE_MESSAGE_INTEGRITY";

    /**
     * The name of the property that can be used to specify the number of milliseconds that we must wait after ICE processing
     * enters a COMPLETED state and before we free candidates and move into the TERMINATED state.
     */
    public static final String TERMINATION_DELAY = "com.red5pro.ice.TERMINATION_DELAY";

    /**
     * The name of the property that can be used to disable STUN keep alives.
     * Set to true to disable.
     */
    public static final String NO_KEEP_ALIVES = "com.red5pro.ice.NO_KEEP_ALIVES";

    /**
     * Specifies if the TCP NioSocketAcceptors use a shared pool of IoProcessors or if each NioSocketAcceptor has its own.
     * It also specifies if both TCP and UDP acceptors will use a shared thread pool.
     */
    public static final String NIO_USE_PROCESSOR_POOLS = "com.red5pro.ice.NIO_USE_PROCESSOR_POOLS";

    /**
     * When NIO_USE_PROCESSOR_POOLS is true, this is the number of IOProcessors NioSocketAcceptors will use.
     */
    public static final String NIO_PROCESSOR_POOL_SIZE = "com.red5pro.ice.NIO_PROCESSOR_POOL_SIZE";

    /**
     * Periodic worker that checks for abandoned or fouled IceSocket sessions.
     */
    public static final String ICE_SWEEPER_INTERVAL = "com.red5pro.ice.ICE_SWEEPER_INTERVAL";

    /**
     * If a session is suspected of being abandoned, ICE_SWEEPER_TIMEOUT is the number of seconds before the sweeper
     * will take action to free resources.
     */
    public static final String ICE_SWEEPER_TIMEOUT = "com.red5pro.ice.ICE_SWEEPER_TIMEOUT";

    /**
     * Besides sharedAcceptor property, acceptor strategy can inform stun stack how to manage acceptors per Transport
     * type, TCP and UDP. The value can be 0, 1, or 2:
     *
     * 0 = one acceptor per socket
     * 1 = one acceptor for each type(UDP,TCP) for each user-session
     * 2 = one acceptor for each type(UDP,TCP) per application
     *
     * Mode 2 is the same as shared mode. If shared mode is activated, ACCEPTOR_STRATEGY will be overridden.
     */
    public static final String ACCEPTOR_STRATEGY = "com.red5pro.ice.ACCEPTOR_STRATEGY";

    /**
     * THIS PROPERTY IS CURRENTLY UNUSED. IF YOU WANT TO SPEED UP NOMINATIONS THEN CONSIDER SPEEDING UP TRANSACTION
     * FAILURE FOR THE TIME BEING.
     * The maximum number of milliseconds that we should wait for a check list to complete before nominating one of its
     * valid pairs (unless there are none in which case we may have to wait until one appears or the whole list fails).
     * Default value is -1 which causes the nominator to wait until the check list completes or fails.
     */
    public static final String NOMINATION_TIMER = "com.red5pro.ice.NOMINATION_TIMER";

    /**
     * The name of the property used to disabled IPv6 support.
     */
    public static final String DISABLE_IPv6 = "com.red5pro.ice.ipv6.DISABLED";

    /**
     * The name of the allowed interfaces property which specifies the allowed interfaces for host candidate allocations.
     */
    public static final String ALLOWED_INTERFACES = "com.red5pro.ice.harvest.ALLOWED_INTERFACES";

    /**
     * The name of the property which, if set to true, specifies that remote candidates with private IP addresses should be ignored.
     */
    public static final String SKIP_REMOTE_PRIVATE_HOSTS = "SKIP_REMOTE_PRIVATE_HOSTS";

    /**
     * The name of the property which, if set to true, specifies that remote candidates with non-public IP addresses should be ignored.
     * This includes RFC1918, CGNAT (100.64.0.0/10), loopback, link-local, and other non-routable ranges.
     */
    public static final String SKIP_REMOTE_NON_PUBLIC_HOSTS = "SKIP_REMOTE_NON_PUBLIC_HOSTS";

    /**
     * The name of the property which, if set to true, specifies that remote candidates with CGNAT IP addresses (100.64.0.0/10)
     * should be ignored.
     */
    public static final String SKIP_REMOTE_CGNAT = "SKIP_REMOTE_CGNAT";

    /**
     * The name of the property which, if set to true, prioritizes valid pairs using network-cost before candidate priority.
     * Note: This follows the expired draft-thatcher-ice-network-cost guidance and should only be enabled when peers support it.
     */
    public static final String USE_NETWORK_COST = "com.red5pro.ice.USE_NETWORK_COST";

    /**
     * The name of the allowed interfaces property which specifies the blocked interfaces for host candidate allocations.
     */
    public static final String BLOCKED_INTERFACES = "com.red5pro.ice.harvest.BLOCKED_INTERFACES";

    /**
     * The name of the property which specifies a ";"-separated list of IP addresses that are allowed to be used for host candidate allocations.
     */
    public static final String ALLOWED_ADDRESSES = "com.red5pro.ice.harvest.ALLOWED_ADDRESSES";

    /**
     * The name of the property which, if set to true, specifies that IPv6 link local addresses should not be used for candidate allocations.
     */
    public static final String DISABLE_LINK_LOCAL_ADDRESSES = "com.red5pro.ice.harvest.DISABLE_LINK_LOCAL_ADDRESSES";

    /**
     * The name of the property which specifies a ";"-separated list of IP addresses that are not allowed to be used for host candidate allocations.
     *
     * NOTE: this is currently only supported by {@link com.red5pro.ice.harvest.TcpHarvester}.
     */
    public static final String BLOCKED_ADDRESSES = "com.red5pro.ice.harvest.BLOCKED_ADDRESSES";

    /**
     * The name of the property which specifies whether the dynamic port UDP host harvester should be used by Agent instances.
     */
    public static final String USE_DYNAMIC_HOST_HARVESTER = "com.red5pro.ice.harvest.USE_DYNAMIC_HOST_HARVESTER";

    /**
     * Timeout, in seconds, of how long to wait for an individual harvest before timing out
     */
    public static final String HARVESTING_TIMEOUT = "com.red5pro.ice.harvest.HARVESTING_TIMEOUT";

    /**
     * Priority modifier applied to UDP transport candidates.
     * <p>
     * <b>WARNING: Non-Standard Extension</b>
     * <p>
     * This is a Red5 Pro extension and is NOT part of RFC 8445. The RFC specifies that candidate
     * priority should be computed solely using the formula:
     * <pre>
     * priority = (2^24 * type_preference) + (2^8 * local_preference) + (256 - component_id)
     * </pre>
     * Adding transport-based modifiers may cause interoperability issues with strict RFC implementations.
     * <p>
     * Use cases for this modifier:
     * <ul>
     *   <li>Force preference for UDP over TCP in mixed environments</li>
     *   <li>Work around network configurations that favor one transport</li>
     * </ul>
     * <p>
     * Default value: 0 (no modification, RFC-compliant behavior)
     */
    public static final String UDP_PRIORITY_MODIFIER = "UDP_PRIORITY_MODIFIER";

    /**
     * Priority modifier applied to TCP transport candidates.
     * <p>
     * <b>WARNING: Non-Standard Extension</b>
     * <p>
     * This is a Red5 Pro extension and is NOT part of RFC 8445. The RFC specifies that candidate
     * priority should be computed solely using the formula:
     * <pre>
     * priority = (2^24 * type_preference) + (2^8 * local_preference) + (256 - component_id)
     * </pre>
     * Adding transport-based modifiers may cause interoperability issues with strict RFC implementations.
     * <p>
     * Use cases for this modifier:
     * <ul>
     *   <li>Force preference for TCP over UDP in firewall-heavy environments</li>
     *   <li>Work around network configurations that block or penalize UDP</li>
     * </ul>
     * <p>
     * Default value: 0 (no modification, RFC-compliant behavior)
     */
    public static final String TCP_PRIORITY_MODIFIER = "TCP_PRIORITY_MODIFIER";

    /**
     * Enable TURN TCP allocations (RFC 6062). Default: true.
     */
    public static final String TURN_ENABLE_TCP = "com.red5pro.ice.turn.ENABLE_TCP";

    /**
     * Enable TURN TLS allocations (RFC 6062). Default: false.
     */
    public static final String TURN_ENABLE_TLS = "com.red5pro.ice.turn.ENABLE_TLS";

    /**
     * Requested address family for TURN allocations (RFC 6156).
     * Valid values: "ipv4", "ipv6". Default: unset (no attribute).
     */
    public static final String TURN_REQUESTED_ADDRESS_FAMILY = "com.red5pro.ice.turn.REQUESTED_ADDRESS_FAMILY";

    /**
     * Include DONT-FRAGMENT attribute in Allocate requests. Default: false.
     */
    public static final String TURN_DONT_FRAGMENT = "com.red5pro.ice.turn.DONT_FRAGMENT";

    /**
     * Allocation lifetime in seconds for TURN Allocate/Refresh. Default: 600.
     */
    public static final String TURN_ALLOCATION_LIFETIME = "com.red5pro.ice.turn.ALLOCATION_LIFETIME";

    /**
     * Follow 300 Try Alternate responses for TURN. Default: true.
     */
    public static final String TURN_TRY_ALTERNATE = "com.red5pro.ice.turn.TRY_ALTERNATE";

    /**
     * Request EVEN-PORT in Allocate. Default: false.
     */
    public static final String TURN_USE_EVEN_PORT = "com.red5pro.ice.turn.USE_EVEN_PORT";

    /**
     * Set the R-flag on EVEN-PORT to request port reservation. Default: false.
     */
    public static final String TURN_EVEN_PORT_RFLAG = "com.red5pro.ice.turn.EVEN_PORT_RFLAG";

    /**
     * Maximum time (in ms) the PaceMaker will continue starting new connectivity checks.
     * Individual STUN transactions continue their retransmission schedules independently.
     * Default: 3000ms.
     */
    public static final String CHECKLIST_TIMEOUT = "com.red5pro.ice.CHECKLIST_TIMEOUT";

    /**
     * Grace period (in ms) before marking a checklist as FAILED after all checks complete
     * without valid pairs. This is separate from {@link #CHECKLIST_TIMEOUT} which controls
     * how long the PaceMaker runs.
     * Default: 3000ms.
     */
    public static final String CHECKLIST_COMPLETION_TIMEOUT = "com.red5pro.ice.CHECKLIST_COMPLETION_TIMEOUT";

    /**
     * Time in milliseconds without a consent freshness confirmation before an established
     * ICE session transitions from COMPLETED to DISCONNECTED. This enables temporary
     * disconnection detection with recovery, rather than going straight to FAILED.
     * Default: 5000ms (matches pion/ice behavior).
     */
    public static final String DISCONNECTED_TIMEOUT = "com.red5pro.ice.DISCONNECTED_TIMEOUT";

    /**
     * Additional time in milliseconds after entering DISCONNECTED state before the session
     * transitions to FAILED (terminal). If consent freshness is confirmed within this window,
     * the session recovers back to COMPLETED.
     * Default: 25000ms (matches pion/ice behavior).
     */
    public static final String FAILED_TIMEOUT = "com.red5pro.ice.FAILED_TIMEOUT";

    /**
     * Ta pace timer in milliseconds.
     */
    public static final String TA = "com.red5pro.ice.TA_PACE_TIMER";

    /**
     * Enables ICE-LITE mode for this agent.
     * <p>
     * ICE-LITE is a simplified ICE implementation defined in RFC 8445 Section 2.5.
     * When enabled, the agent:
     * <ul>
     *   <li>MUST always be in the controlled role (never controlling)</li>
     *   <li>Only gathers host candidates (no STUN/TURN server-reflexive or relayed candidates)</li>
     *   <li>Does not initiate connectivity checks (only responds to incoming checks)</li>
     *   <li>Accepts nominations from the full ICE (controlling) agent</li>
     * </ul>
     * <p>
     * ICE-LITE is suitable for servers with public IP addresses that don't need NAT traversal.
     * <p>
     * Default value: false (full ICE implementation)
     */
    public static final String ICE_LITE = "com.red5pro.ice.ICE_LITE";

    /**
     * Returns the String value of the specified property (minus all
     * encompassing whitespaces)and null in case no property value was mapped
     * against the specified propertyName, or in case the returned property
     * string had zero length or contained whitespaces only.
     *
     * @param propertyName the name of the property that is being queried.
     *
     * @return the result of calling the property's toString method and null in
     * case there was no value mapped against the specified
     * propertyName, or the returned string had zero length or
     * contained whitespaces only.
     */
    public static String getString(String propertyName) {
        Object obj = System.getProperty(propertyName);
        String str;
        if (obj == null) {
            str = null;
        } else {
            str = obj.toString().trim();
            if (str.length() == 0) {
                str = null;
            }
        }
        return str;
    }

    /**
     * Return a default if the property doesnt exist.
     *
     * @param propertyName
     * @param defaultValue
     * @return default value
     */
    public static String getStringOrDefault(String propertyName, String defaultValue) {
        String result = getString(propertyName);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Returns the String array of the specified property, or null in case
     * the returned property string array had zero length.
     *
     * @param propertyName the name of the property that is being queried.
     * @param  regex the delimiting regular expression
     *
     * @return  the array of strings computed by splitting the specified
     * property value around matches of the given regular expression
     */
    public static String[] getStringArray(String propertyName, String regex) {
        String str = getString(propertyName);
        if (str == null) {
            return null;
        }
        String[] parts = str.split(regex);
        // Remove mal-formatted entries.
        List<String> res = new ArrayList<>();
        for (String s : parts) {
            if (s != null && s.trim().length() != 0) {
                res.add(s);
            }
        }
        if (res.size() == 0) {
            return null;
        }
        return res.toArray(new String[res.size()]);
    }

    /**
     * Returns the value of a specific property as a signed decimal integer. If
     * a property with the specified property name exists, its string
     * representation is parsed into a signed decimal integer according to the
     * rules of {@link Integer#parseInt(String)}. If parsing the value as a
     * signed decimal integer fails or there is no value associated with the
     * specified property name, defaultValue is returned.
     *
     * @param propertyName the name of the property to get the value of as a
     * signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the
     * specified property name as a signed decimal integer fails or there is no
     * value associated with the specified property name in the System
     * properties.
     * @return the value of the property with the specified name in the System
     * properties as a signed decimal integer;
     * defaultValue if parsing the value of the specified property name
     * fails or no value is associated among the System properties.
     */
    public static int getInt(String propertyName, int defaultValue) {
        String stringValue = getString(propertyName);
        int intValue = defaultValue;
        if (stringValue != null && stringValue.length() > 0) {
            try {
                intValue = Integer.parseInt(stringValue);
            } catch (NumberFormatException ex) {
                if (logger.isDebugEnabled()) {
                    logger.warn("{} does not appear to be an integer; defaulting to {}", propertyName, defaultValue, ex);
                }
            }
        }
        return intValue;
    }

    /**
     * Gets the value of a specific property as a boolean. If the
     * specified property name is associated with a value, the string
     * representation of the value is parsed into a boolean according
     * to the rules of {@link Boolean#parseBoolean(String)} . Otherwise,
     * defaultValue is returned.
     *
     * @param propertyName the name of the property to get the value of as a
     * boolean
     * @param defaultValue the value to be returned if the specified property
     * name is not associated with a value
     * @return the value of the property with the specified name as a
     * boolean; defaultValue if the property with the
     * specified name is not associated with a value
     */
    public static boolean getBoolean(String propertyName, boolean defaultValue) {
        String str = getString(propertyName);
        return (str == null) ? defaultValue : Boolean.parseBoolean(str);
    }
}
