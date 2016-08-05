(defproject hazelcap "0.1.0"
  :description "Break Hazelcast using Jepsen"
  :url "http://hazelcast.org"
  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :repositories {"sonatype snapshots" "https://oss.sonatype.org/content/repositories/snapshots"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [jepsen "0.1.2"]
                 [com.hazelcast/hazelcast-client "3.8-SNAPSHOT"]
                 ])

