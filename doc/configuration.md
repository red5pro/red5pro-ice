# General

This file describes some of the properties which are used to configure ICE.

## Interfaces and IP addresses

* com.red5pro.ice.harvest.ALLOWED_INTERFACES

Default: all interfaces are allowed.

This property can be used to specify a ";"-separated list of interfaces which are allowed to be used for candidate
allocations. If not specified, all interfaces are considered allowed, unless they are explicitly blocked (see below).

* com.red5pro.ice.harvest.BLOCKED_INTERFACES

Default: no interfaces are blocked.

This property can be used to specify a ";"-separated list of interfaces which are not allowed to be used for candidate
allocations.

* com.red5pro.ice.harvest.ALLOWED_ADDRESSES

Default: all addresses are allowed.

This property can be used to specify a ";"-separated list of IP addresses which are allowed to be used for candidate
allocations. If not specified, all addresses are considered allowed, unless they are explicitly blocked (see below).

* com.red5pro.ice.harvest.BLOCKED_ADDRESSES

Default: no addresses are blocked.

This property can be used to specify a ";"-separated list of IP addresses which are not allowed to be used for candidate allocations.

* com.red5pro.ice.BIND_RETRIES

Default: 1

Amount of times to retry binding to a local address.

* com.red5pro.ipv6.DISABLED

Type: boolean

Default: false

This property can be used to disable binding on IPv6 addresses.

## Mapping harvesters

Ice4j uses the concept of "mapping harvesters" to handle known IP address mappings. A set of mapping harvesters is
configured once when the library initializes, and each of them contains a pair of IP addresses (local and public).

When an ICE Agent gathers candidates, it uses the set of mapping harvesters to obtain ```srflx``` candidates without
the use to e.g. a STUN server dynamically.

Mapping harvesters preserve the port number of the original candidate, so they should only be used when port numbers
are preserved.

Ice4j implements three types of mapping harvesters: one with a pre-configured pair of addresses, one two which discover
addresses dynamically using the AWS API and STUN.

* com.red5pro.ice.harvest.NAT_HARVESTER_LOCAL_ADDRESS
* com.red5pro.ice.harvest.NAT_HARVESTER_PUBLIC_ADDRESS

Default: none

Configures the addresses of the pre-configured mapping harvester.

* com.red5pro.ice.harvest.DISABLE_AWS_HARVESTER

Default: false

Explicitly disables the AWS mapping harvester. By default the harvester is enabled if ice4j detects that it is running
in the AWS network.

* com.red5pro.ice.harvest.FORCE_AWS_HARVESTER

Default: false

Force the use of the AWS mapping harvester, even if ice4j did not detect that it is running in the AWS network.

* com.red5pro.ice.harvest.STUN_MAPPING_HARVESTER_ADDRESSES

Default: none

A comma-separated list of STUN server addresses to use for mapping harvesters. Each STUN server address is an
ip_address:port pair. Example: `stun1.example.com:12345,stun2.example.com:23456`

* com.red5pro.ice.TERMINATION_DELAY

Waits the specified period of time or the default of three seconds and then moves an Agent into the terminated state
and frees all non-nominated candidates.

* com.red5pro.ice.TA_PACE_TIMER

Ta pace timer in milliseconds. RFC 5245 says that Ta is: Ta_i = (stun_packet_size / rtp_packet_size) * rtp_ptime.

* SKIP_REMOTE_PRIVATE_HOSTS

Whether or not to skip remote candidates originating from private network hosts; default is not to allow them.

## Acceptor configuration

### Shared NIO acceptor or per-instance acceptor

To utilize an instance of `IoAcceptor` for each `StunStack`, the `NIO_SHARED_MODE` property must be configured as `false`, to spawn a single static `IoAcceptor` for all `StunStack` instances, use the default value of `true`.

Besides sharedAcceptor property, acceptor strategy can inform stun stack how to manage acceptors per transport type.
Generally there will be one acceptor per transport type, TCP and UDP.

* 0 = one acceptor per socket
* 1 = one acceptor for each type (UDP,TCP) for each user-session
* 2 = one acceptor for each type(UDP,TCP) per application

Mode 2 is the same as shared mode. If shared mode is activated, `ACCEPTOR_STRATEGY` will be overridden.

* com.red5pro.ice.ACCEPTOR_STRATEGY

### Acceptor timeout

Timeout in seconds to wait for a bind or unbind operation to complete, the `ACCEPTOR_TIMEOUT` property is modifiable from the default of 2 seconds.

### Aggressive Acceptor reset

To prevent a possible deadlock caused by a failed bind or unbind event making the acceptor unresponsive, the `ACCEPTOR_RESET` option allows the acceptor to be reset on-the-fly.

### Blocking or Non-blocking I/O

Setting the `IO_BLOCKING` to `true` will configure the internal services to use blocking I/O with TCP, instead of the default non-blocking implementation. This does not affect UDP connections.

### I/O thread count

Setting the I/O thread count is handled via `NIO_PROCESSOR_POOL_SIZE` property. The default is the number of available processors multiplied by two.

Specifies if the TCP `NioSocketAcceptor` uses a shared pool of `IoProcessor` or if each `NioSocketAcceptor` has its own.
It also specifies if both TCP and UDP acceptors will use a shared thread pool.

* com.red5pro.ice.NIO_USE_PROCESSOR_POOLS

Default: true

Number of processors created for the shared `IoProcessor` pool.

* com.red5pro.ice.NIO_PROCESSOR_POOL_SIZE

Default: (Available processors * 2)

When `NIO_USE_PROCESSOR_POOLS` is `true`, this is the number of `IoProcessor` that `NioSocketAcceptor` will use. This should not exceed the CPU core count.

Periodic worker that checks for abandoned or fouled `IceSocket` sessions.

* com.red5pro.ice.ICE_SWEEPER_INTERVAL

Default: 60 _note this is in seconds, not like other properties in milliseconds_

If a session is suspected of being abandoned, `ICE_SWEEPER_TIMEOUT` is the number of seconds before the sweeper will take action to free resources.

* com.red5pro.ice.ICE_SWEEPER_TIMEOUT

Default: 60 _note this is in seconds, not like other properties in milliseconds_

### Buffers

Configuration of the send buffer is handled via the `SO_SNDBUF` and `SO_RCVBUF` properties. The defaults are 1500 for both and any target amount should take MTU size ~1500 into account.

### QoS / Traffic class

The traffic class setting for the internal sockets is handled via the `TRAFFIC_CLASS` property. The default is 0, which mean no configuration. RFC 1349 defines the values as follows:

* IPTOS_LOWCOST (0x02)
* IPTOS_RELIABILITY (0x04)
* IPTOS_THROUGHPUT (0x08)
* IPTOS_LOWDELAY (0x10)

Low delay + High throughput (0x18)

[Click here for additional details](https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html#setTrafficClass-int-)

### Send and Receive idle timeout

Send or receive may be detected as idle if they exceed the configured (in seconds) `SO_TIMEOUT` property which is defaulted to 30 seconds.

### Private network host candidate handling

To skip the addition of `RemoteCandidate` instances originating on private networks on a `Component`, set `SKIP_REMOTE_PRIVATE_HOSTS` to `true`; otherwise the default value `false` or not-to-skip will be used.

### IceDatagram Acceptor bind/unbind timeout

Timeout in seconds to wait for a bind or unbind "request" to complete for UDP, the `BIND_REQUEST_TIMEOUT` property is modifiable from the default of 3 seconds.

### Keep-alives

The name of the property that can be used to disable STUN keep alives.

* com.red5pro.ice.NO_KEEP_ALIVES

Default: false (keep-alives are enabled)

### Socket linger for TCP sockets

Specify a linger-on-close timeout. This option disables/enables immediate return from a `close()` of a TCP Socket. Enabling this option with a non-zero Integer timeout means that a `close()` will block pending the transmission and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout of zero does a forceful close immediately. If the specified timeout value exceeds 65,535 it will be reduced to 65,535. Valid only for *TCP*; linger time is in seconds, the default is -1 (disabled)

The `SO_LINGER` property is modifiable from the default of -1.

[Additional Info](https://stackoverflow.com/questions/3757289/when-is-tcp-option-so-linger-0-required#13088864)

## Server Startup

To add the options to your Red5 / Red5 Pro server startup, update the `JAVA_OPTS` line like so:

```sh
export JAVA_OPTS="$SECURITY_OPTS $JAVA_OPTS $JVM_OPTS $TOMCAT_OPTS $NATIVE -DSO_RCVBUF=3000 -DIO_THREAD_PRIORITY=6 -DNIO_SELECTOR_SLEEP_MS=10"
```
