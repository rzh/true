(defproject thomas "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2197"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [com.novemberain/monger "2.0.0-rc1"]
                 [compojure "1.1.6"]
                 [ring/ring "1.3.0"]
                 [cljs-ajax "0.2.6"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [fogus/ring-edn "0.2.0"]
                 [ring/ring-json "0.3.1"]
                 [om "0.5.0"]]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["resources"]


  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/clj" "src/cljs"]
              :compiler {
                :output-to "resources/public/js/main.js"
                :output-dir "resources/public/js/out"
                :optimizations :none
                :source-map true}}]})
