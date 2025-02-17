# Utility

## IceTransport Servlet

The IceTransport servlet provides a way to view and manipulate the ICE transport layer. It is a servlet that can be used to view the current state of the ICE transport layer, close sockets, and more. The servlet is not part of the Red5 Pro server, but is available as a separate [download](../src/test/resources/icetransport.jsp).

### Usage

The IceTransport servlet is available at the following URL (replace `[server address]` with the address of the server):

```http
https://[server address]/live/icetransport.jsp?get-bindings=true
```

Response:

```json
{"results":[{"acceptorId":"2827e7b5-da57-4674-8920-f93afa013f65","bindings":["159.203.108.185:49350/udp","10.17.0.8:49350/udp"]}],"call":"get-bindings","count":1}
```

### Parameters Examples

To close all sockets with transport type and port on any local address: `icetransport.jsp?close-socket={int}&type={ "tcp" | "udp" }`

To close a socket with host address, transport type, and port: `icetransport.jsp?close-socket-address={host}&port={int}&type={ "tcp" | "udp" }`
