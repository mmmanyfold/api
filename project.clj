(defproject mmmanyfold "0.1.0-SNAPSHOT"

  :description "An API for mmmanyfold projects"
  :url "http://www.mmmanyfold.com/"

  :dependencies [[bouncer "1.0.0"]
                 [camel-snake-kebab "0.4.0"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [compojure "1.5.1"]
                 [cprop "0.1.9"]
                 [luminus-http-kit "0.1.4"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.1"]
                 [markdown-clj "0.9.91"]
                 [metosin/compojure-api "1.1.9"]
                 [metosin/ring-http-response "0.8.0"]
                 [mount "0.1.10"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.webjars.bower/tether "1.3.7"]
                 [org.webjars/bootstrap "4.0.0-alpha.5"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/jquery "3.1.1"]
                 [ring-middleware-format "0.7.0"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-defaults "0.2.1"]
                 [selmer "1.10.0"]
                 [ring-cors "0.1.8"]
                 [ring/ring-json "0.4.0"]
                 [clj-facebook-graph "0.4.0"]
                 [org.clojure/core.async "0.2.395"]
                 [cheshire "5.6.3"]
                 [environ "1.0.2"]
                 [metosin/compojure-api "1.1.9"]
                 [nilenso/mailgun "0.2.3"]
                 [funcool/cuerdas "2.0.2"]]

  :min-lein-version "2.0.0"

  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main mmmanyfold.core

  :plugins [[lein-cprop "1.0.1"]
            [lein-auto "0.1.3"]]

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "mmmanyfold.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:dependencies [[prone "1.1.4"]
                                 [ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.5.0"]
                                 [pjstadig/humane-test-output "0.8.1"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.14.0"]]
                  
                  :source-paths ["env/dev/clj" "test/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
