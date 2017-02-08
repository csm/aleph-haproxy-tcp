# aleph-haproxy-tcp

![travis](https://travis-ci.org/csm/aleph-haproxy-tcp.svg?branch=master) 
[![Clojars Project](https://img.shields.io/clojars/v/com.github.csm/aleph-haproxy-tcp.svg)](https://clojars.org/com.github.csm/aleph-haproxy-tcp)

A small wrapper around [aleph](https://aleph.io) that lets you listen as a TCP server, properly
handling [HAProxy protocol](http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt) messages.

It acts identically to `aleph.tcp/start-server`, but it will intercept the initial HAProxy message,
if any is sent. `:remote-addr` in the connection info passed to your handler function will return
the proxied remote address, not the remote address according to the socket.

## Usage

Available on clojars, add to your dependencies:

```
[com.github.csm/aleph-haproxy-tcp "0.1.1"]
```

Then, `aleph.haproxy-tcp/start-server` should work as a drop-in replacement for `aleph.tcp/start-server`.