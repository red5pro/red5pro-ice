# NIO Configuration

The following ice4j adjustments are available in the NIO version via environmental / system properties.

## Shared NIO acceptor or per-instance acceptor

To utilize an instance of `IoAcceptor` for each `StunStack`, the `NIO_SHARED_MODE` property must be configured as `false`, to spawn a single static `IoAcceptor` for all `StunStack` instances, use the default value of `true`.

## Send buffer

Configuration of the send buffer is handled via the `SO_SNDBUF` property. The default is 1500 and any target amount should take MTU size ~1500 into account.

## Receive buffer

Configuration of the receive buffer is handled via the `SO_RCVBUF` property. The default is 1500 and any target amount should take MTU size ~1500 into account.

## QoS / Traffic class

The traffic class setting for the internal sockets is handled via the `TRAFFIC_CLASS` property. The default is 0, which mean no configuration. RFC 1349 defines the values as follows:
 * IPTOS_LOWCOST (0x02)
 * IPTOS_RELIABILITY (0x04)
 * IPTOS_THROUGHPUT (0x08)
 * IPTOS_LOWDELAY (0x10)

Low delay + High throughput (0x18)

[Click here for additional details](https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html#setTrafficClass-int-)

## Send and Receive idle timeout

Send or receive may be detected as idle if they exceed the configured (in seconds) `SO_TIMEOUT` property which is defaulted to 30 seconds.

## Acceptor timeout

Timeout in seconds to wait for a bind or unbind operation to complete, the `ACCEPTOR_TIMEOUT` property is modifiable from the default of 2 seconds.

## Aggressive Acceptor reset

To prevent a possible deadlock caused by a failed bind or unbind event making the acceptor unresponsive, the `ACCEPTOR_RESET` option allows the acceptor to be reset on-the-fly.

## I/O thread count

Setting the I/O thread count is handled via the `NIO_WORKERS` property. The default priority is 16 and should not exceed the CPU core count; lastly, this is only used for TCP.

## Blocking or Non-blocking I/O

Setting the `IO_BLOCKING` to `true` will configure the internal services to use blocking I/O with TCP, instead of the default non-blocking implementation. This does not affect UDP connections.

## Private network host candidate handling

To skip the addition of `RemoteCandidate` instances originating on private networks on a `Component`, set `SKIP_REMOTE_PRIVATE_HOSTS` to `true`; otherwise the default value `false` or not-to-skip will be used.

# Server Startup

To add the options to your Red5 / Red5 Pro server startup, update the `JAVA_OPTS` line like so:

```
export JAVA_OPTS="$SECURITY_OPTS $JAVA_OPTS $JVM_OPTS $TOMCAT_OPTS $NATIVE -DSO_RCVBUF=3000 -DIO_THREAD_PRIORITY=6 -DNIO_SELECTOR_SLEEP_MS=10"
```

# IceDatagram Acceptor bind/unbind timeout

Timeout in seconds to wait for a bind or unbind "request" to complete for UDP, the `BIND_REQUEST_TIMEOUT` property is modifiable from the default of 3 seconds.

# Socket linger for TCP sockets

Specify a linger-on-close timeout. This option disables/enables immediate return from a `close()` of a TCP Socket. Enabling this option with a non-zero Integer timeout means that a `close()` will block pending the transmission and acknowledgement of all data written to the peer, at which point the socket is closed gracefully. Upon reaching the linger timeout, the socket is closed forcefully, with a TCP RST. Enabling the option with a timeout of zero does a forceful close immediately. If the specified timeout value exceeds 65,535 it will be reduced to 65,535. Valid only for *TCP*; linger time is in seconds, the default is -1 (disabled)

The `SO_LINGER` property is modifiable from the default of -1.

[Additional Info](https://stackoverflow.com/questions/3757289/when-is-tcp-option-so-linger-0-required#13088864)
