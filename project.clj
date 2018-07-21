(defproject io.hosaka/oscillator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories ^:replace [["releases" "https://artifactory.i.hosaka.io/artifactory/libs-release"]
                           ["snapshots" "https://artifactory.i.hosaka.io/artifactory/libs-snapshot"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.nrepl "0.2.13"]

                 [io.hosaka/common "1.2.1"]
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-api "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]

                 [yogthos/config "1.1.1"]

                 [clojure.java-time "0.3.2"]

                 [com.novemberain/langohr "5.0.0"]]
  :main ^:skip-aot io.hosaka.oscillator
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["env/dev/resources" "resources"]
                   :env {:dev true}}})
