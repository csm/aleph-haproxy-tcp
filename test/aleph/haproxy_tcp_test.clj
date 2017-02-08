(ns aleph.haproxy-tcp-test
  (:require [aleph.haproxy-tcp :refer :all]
            [aleph.netty :as netty]
            [aleph.tcp :as tcp]
            [clojure.test :refer :all]
            [manifold.stream :as s]
            [manifold.deferred :as d])
  (:import [java.net ServerSocket InetSocketAddress InetAddress]))

(deftest test-proxy-proto
  (testing "that proxied connections are handled"
    (let [remote-addr (volatile! nil)]
      (with-open [server (start-server (fn [conn conn-info]
                                         (future
                                           @(s/take! (s/source-only conn))
                                           (vreset! remote-addr (:remote-addr conn-info))
                                           (s/close! conn)))
                                       {:socket-address (InetSocketAddress. (InetAddress/getByName "127.0.0.1") 0)})]
        (let [port (netty/port server)
              client @(tcp/client {:host "127.0.0.1" :port port})
              wait-close (d/deferred)]
          (s/on-closed client (fn [] (d/success! wait-close true)))
          (is (true? @(s/put! client (.getBytes "PROXY TCP4 1.1.1.1 2.2.2.2 1234 1234\r\n"))))
          (is (true? @(s/put! client (.getBytes "foo"))))
          (deref wait-close 5000 ::timeout))
        (is (= "1.1.1.1" @remote-addr))))))