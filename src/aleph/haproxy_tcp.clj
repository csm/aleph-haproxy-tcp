(ns aleph.haproxy-tcp
  (:require [aleph.tcp]
            [potemkin.collections :as p]
            [aleph.netty :as netty]
            [clojure.tools.logging :as log]
            [manifold.stream :as s])
  (:import [io.netty.channel Channel ChannelPipeline ChannelHandler]
           [java.net InetSocketAddress]
           [io.netty.handler.ssl SslHandler]
           [java.io EOFException]
           [io.netty.handler.codec.haproxy HAProxyMessageDecoder HAProxyMessage]))

(defrecord ProxiedChannel [^Channel channel proxy-info])

(p/def-derived-map ProxiedTcpConnection [^ProxiedChannel ch]
  :server-name (some-> ch :channel ^InetSocketAddress (.localAddress) .getHostName)
  :server-port (some-> ch :channel ^InetSocketAddress (.localAddress) .getPort)
  :remote-addr (if-let [^HAProxyMessage m (deref (:proxy-info ch))]
                 (.sourceAddress m)
                 (some-> ch :channel ^InetSocketAddress (.remoteAddress) .getAddress .getHostAddress))
  :proxy-info (some-> ch :proxy-info deref)
  :ssl-session (some-> ch :channel ^ChannelPipeline (.pipeline) ^SslHandler (.get "ssl-handler") .engine .getSession))

(defn- ^ChannelHandler server-channel-handler
  [handler {:keys [raw-stream?] :as options}]
  (let [in (atom nil)
        proxy-info (atom nil)]
    (netty/channel-handler

      :exception-caught
      ([_ ctx ex]
        (when-not (instance? EOFException ex)
          (log/warn ex "error in TCP server")))

      :channel-inactive
      ([_ ctx]
        (s/close! @in))

      :channel-active
      ([_ ctx]
        (let [ch (.channel ctx)]
          (handler
            (doto
              (s/splice
                (netty/sink ch true netty/to-byte-buf)
                (reset! in (netty/source ch)))
              (reset-meta! {:aleph/channel ch}))
            (->ProxiedTcpConnection (->ProxiedChannel ch proxy-info)))))

      :channel-read
      ([_ ctx msg]
        (if (instance? HAProxyMessage msg)
          (reset! proxy-info msg)
          (netty/put!
            (.channel ctx) @in
            (if raw-stream?
              msg
              (netty/release-buf->array msg))))))))

(defn start-server
  "Identical to `aleph.tcp/start-server`, but will also install a `io.netty.handler.codec.haproxy.HAProxyMessageDecoder`
   into the pipeline. The connection object passed in to your handler function will return the
   right value for `:remote-addr` when the connection is proxied; i.e., that address will be
   whatever was specified in the PROXY header, if any was sent, instead of the remote address
   of the socket."
  [handler
   {:keys [port socket-address ssl-context bootstrap-transform pipeline-transform epoll?]
    :or {bootstrap-transform identity
         pipeline-transform identity
         epoll? false}
    :as options}]
  (netty/start-server
    (fn [^ChannelPipeline pipeline]
      (.addFirst pipeline "haproxy" (HAProxyMessageDecoder.))
      (.addLast pipeline "handler" (server-channel-handler handler options))
      (pipeline-transform pipeline))
    ssl-context
    bootstrap-transform
    nil
    (if socket-address
      socket-address
      (InetSocketAddress. port))
    epoll?))