(defproject api-test-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.2"]
                 [ring-server "0.5.0"]
                 [reagent "1.0.0"]
                 [reagent-utils "0.3.3"]
                 [ring "1.9.1"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1.7"]
                 [metosin/reitit "0.5.12"]
                 [metosin/jsonista "0.3.1"] ]

  :plugins [[lein-environ "1.1.0"]
            [lein-asset-minifier "0.4.6"
             :exclusions [org.clojure/clojure]]]

  :ring {:handler api-test-example.handler/app
         :uberwar-name "api-test-example.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "api-test-example.jar"
  :main api-test-example.server
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]

  :profiles {:dev {:repl-options {:init-ns api-test-example.server}
                   :dependencies [[cider/piggieback "0.5.2"]
                                  [binaryage/devtools "1.0.2"]
                                  [ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.9.1"]
                                  [prone "2020-01-17"]
                                  [nrepl "0.8.3"]
                                  [pjstadig/humane-test-output "0.10.0"] ]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
