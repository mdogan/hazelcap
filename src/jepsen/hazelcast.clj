(ns jepsen.hazelcast
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [jepsen [db :as db]
             [core :as core]
             [checker :as checker]
             [client :as client]
             [control :as c]
             [generator :as gen]
             [nemesis :as nemesis]
             [tests :as tests]
             [util :refer [timeout]]]
            [jepsen.control.util :as cu]
            [jepsen.control.net :as net]
            [jepsen.os.debian :as debian]
            [knossos.model :as model])
  (:import (java.io ByteArrayInputStream)))

(defn prepare-members
    "Prepare members section"
    [nodes]
    (str/join
        (map (fn [node] (str "<member>" node ":5701</member>")) nodes)))

(defn prepare-config
    "Replace server addresses"
    [test node]
    (let [nodes (:nodes test)]
        (-> "hazelcast.xml"
          io/resource
          slurp
          (str/replace #"<!-- PUBLIC-ADDRESS -->" node)
          (str/replace #"<!-- MEMBERS -->" (prepare-members nodes))
          (str/replace #"<!-- INTERFACE -->" (net/local-ip))
    )))

(defn fetch-jars
  "fetch jars from repo and returns the classpath"
  [version node]
  (let [hz-jar (str "hazelcast-all-" version ".jar")
        log4j-version "1.2.17"
        log4j-jar (str "log4j-" log4j-version ".jar")]

    (when-not (cu/exists? hz-jar)
      (info node "Fetching " hz-jar)
      (c/exec :wget (str "https://repo1.maven.org/maven2/com/hazelcast/hazelcast-all/" version "/" hz-jar)))

    (when-not (cu/exists? log4j-jar)
      (info node "Fetching " log4j-jar)
      (c/exec :wget (str "https://repo1.maven.org/maven2/log4j/log4j/" log4j-version "/" log4j-jar)))

    (info node "Fetching jars done ")
    (str hz-jar ":" log4j-jar ":.")
    ))

(defn db
  "Hazelcast DB for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]

      (debian/install-jdk8!)
      (info node "JDK8 installed")

      (c/su
        (info node "Uploading hazelcast.xml")
        (c/exec :echo (prepare-config test node) :> "hazelcast.xml")
        (c/exec :echo (slurp (io/resource "hazelcast-log4j.properties")) :> "log4j.properties")

        (let [classpath (fetch-jars version node)]
            (c/exec :rm :-rf "hazelcast.log")
            (cu/start-daemon! {:logfile "hazelcast.log" :pidfile "hazelcast.pid" :chdir "/"}
                            "/usr/bin/java"
                              "-server" "-Xms2G" "-Xmx2G"
                              "-cp" classpath
                              "com.hazelcast.core.server.StartServer")
            )

        (core/synchronize test)
        (info node "Hazelcast is ready")))

    (teardown! [_ test node]
      (info node "tearing down Hazelcast")
      (c/su (cu/stop-daemon! "hazelcast.pid"))
      )

     db/LogFiles
     (log-files [_ test node]
       ["/hazelcast.log"])
      ))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(defn prepare-client-config
  "client config"
  [node]
  (ByteArrayInputStream. (.getBytes (str/replace (slurp (io/resource "hazelcast-client.xml"))
    #"<!-- MEMBER -->" (str "<address>" node ":5701</address>"))))
  )

(defn new-client-config
  "new client config object"
  [node]
  (.build (com.hazelcast.client.config.XmlClientConfigBuilder. (prepare-client-config node)))
  )

(defn start-client
  "new client"
  [config]
  (com.hazelcast.client.HazelcastClient/newHazelcastClient config)
  )

(defn client
  "A client for a single compare-and-set register"
  [client]
  (reify client/Client
    (setup! [_ test node]
      (let [config (new-client-config node) hz-client (start-client config)]
        (client hz-client)
        ))

    (invoke! [this test op]
        )

    (teardown! [_ test]
      (info "Client is shutting down " client)
      (.shutdown client)
        )
    ))


(defn hz-test
  [version]
  (let [config (clojure.edn/read-string (slurp (io/resource "config.edn")))]
      (assoc tests/noop-test
        :name "hazelcast"
        :ssh (:ssh config)
        :nodes (:nodes config)
        :os debian/os
        :db (db version)
        :client (client nil)
        ;:nemesis (nemesis/partition-random-halves)
        ;:generator (->> (gen/mix [r w cas])
        ;                (gen/stagger 1)
        ;                (gen/nemesis
        ;                  (gen/seq (cycle [(gen/sleep 5)
        ;                                   {:type :info, :f :start}
        ;                                   (gen/sleep 5)
        ;                                   {:type :info, :f :stop}])))
        ;                (gen/time-limit 60))
        ;:model (model/cas-register 0)
        ;:checker (checker/compose
        ;           {:perf   (checker/perf)
        ;            :linear checker/linearizable})
        )
  ))
