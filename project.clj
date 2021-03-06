(defproject lens-sds-query "0.6"
  :description "Lens Study Data Store Query Service"
  :url "https://github.com/alexanderkiel/lens-sds-query"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[bidi "1.25.0"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [clj-time "0.11.0"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [com.stuartsierra/component "0.3.0"]
                 [environ "1.0.1"]
                 [http-kit "2.1.18"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.371"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.reader "1.0.0-beta1"]
                 [org.omcljs/om "1.0.0-alpha30"
                  :exclusions [org.clojure/clojurescript
                               com.cognitect/transit-cljs
                               cljsjs/react-dom
                               cljsjs/react]]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [prismatic/plumbing "0.5.2"]
                 [prismatic/schema "1.0.4"]
                 [ring/ring-core "1.4.0"
                  :exclusions [commons-fileupload]]]

  :profiles {:dev [:datomic-free :dev-common :base :system :user :provided]
             :dev-pro [:datomic-pro :dev-common :base :system :user :provided]

             :dev-common
             {:source-paths ["dev"]
              :dependencies [[org.clojure/tools.namespace "0.2.4"]
                             [org.clojure/test.check "0.9.0"]
                             [criterium "0.4.3"]
                             [juxt/iota "0.2.0"]]
              :global-vars {*print-length* 20}}

             :datomic-free
             {:dependencies [[com.datomic/datomic-free "0.9.5350"
                              :exclusions [org.slf4j/slf4j-nop commons-codec
                                           com.amazonaws/aws-java-sdk
                                           joda-time]]]}

             :datomic-pro
             {:repositories [["my.datomic.com" "https://my.datomic.com/repo"]]
              :dependencies [[com.datomic/datomic-pro "0.9.5350"
                              :exclusions [org.slf4j/slf4j-nop
                                           org.apache.httpcomponents/httpclient
                                           commons-codec
                                           joda-time]]
                             [com.basho.riak/riak-client "1.4.4"
                              :exclusions [com.fasterxml.jackson.core/jackson-annotations
                                           com.fasterxml.jackson.core/jackson-core
                                           com.fasterxml.jackson.core/jackson-databind
                                           commons-codec]]
                             [org.apache.curator/curator-framework "2.6.0"
                              :exclusions [io.netty/netty log4j
                                           com.google.guava/guava]]]}

             :production
             {:main lens.core}})
