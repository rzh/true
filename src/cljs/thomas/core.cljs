
(ns thomas.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [thomas.u :as u]
            [ajax.core :refer [GET PUT ajax-request edn-request-format edn-response-format]]
            )
  )

(enable-console-print!)



(def app-state (atom {:count 0
                      :state :upload-screen
                        ;; :upload-screen
                        ;; :show-exec-screen
                        ;; :show-query-screen
                        ;; :show-quick-check-screen
                        ;; :show-perf-check-screen

                      :queries [{:shape "new_form_1"
                                 :sample "this is a sample"
                                 :quickcheck-results {:result "pass"}
                                 :perfcheck-results {:avg "100ms"
                                                     :throughput 10000}
                                 }

                                {:shape "new_form_2"
                                 :sample "this is a sample"
                                 :quickcheck-results {:result "failed"}
                                 :perfcheck-results {:avg "1ms"
                                                     :throughput 20000}
                                 }

                                ]
                      :quickcheck-results [{:shape "something"
                                            :result "pass"}
                                           {:shape "something else"
                                            :result "fail"}]

                      :perfcheck-results [{:shape "something"
                                            :result "fail"}
                                           {:shape "something else"
                                            :result "pass"}]
                      }))


(defn status-bar-active? [s]
  (if (= "100.00%" (u/progress-percentage s)) "" " progress-striped active")
  )

(defn render-status-bar [status]
  (dom/div #js {:className (str "progress progress-info " (status-bar-active? status)) }
    (dom/div
     #js {:className "progress-bar bar"
          :style #js {:width (u/progress-percentage status)}}
     )))

(defn result-row-color
  [r]
  (case r
    "fail" "danger"
    "")
  )

(defn form-row-queries []
  (fn [the-item owner]
    (om/component
     (let [{:keys [shape sample quickcheck-results perfcheck-results]} the-item]
       (dom/tr nil
               (dom/td #js {:style #js {:width "45%"}} shape)
               (dom/td nil sample)
               )))))

(defn form-row-quickcheck []
  (fn [the-item owner]
    (om/component
     (let [{:keys [shape result]} the-item]
       (dom/tr #js {:className (result-row-color result)}
               (dom/td #js {:style #js {:width "45%"}} shape)
               (dom/td nil result)
               )))))

(defn form-row-perfcheck []
  (fn [the-item owner]
    (om/component
     (let [{:keys [shape result]} the-item]
       (dom/tr #js {:className (result-row-color  result)}
               (dom/td #js {:style #js {:width "45%"}} shape)
               (dom/td nil result)
               )))))


(defn render-new-query-table
  "render new queries"
  [app]
  (dom/table #js {:className "table center-block"
                  :style #js {:width "90%"}}
             (dom/thead nil
                        (dom/th nil "query")
                        (dom/th nil "sample"))

             (apply dom/tbody nil  (om/build-all (form-row-queries) (:queries app)))
             )
  )

(defn render-quick-check-results-table
  "render quick results"
  [app]
  (dom/table #js {:className "table center-block"
                  :style #js {:width "90%"}}
             (dom/thead nil
                        (dom/th nil "query")
                        (dom/th nil "quick-check results"))

             (apply dom/tbody #js {:className "table"}  (om/build-all (form-row-quickcheck) (:quickcheck-results app)))
             )
  )

(defn render-perf-check-results-table
  "render quick results"
  [app]
  (dom/table #js {:className "table center-block"
                  :style #js {:width "90%"}}
             (dom/thead nil
                        (dom/th nil "query")
                        (dom/th nil "perf-check results"))

             (apply dom/tbody nil  (om/build-all (form-row-perfcheck) (:perfcheck-results app)))
             )
  )


(defn handler-perf-check-results
  [res app]
  (om/transact! app :count inc)  ;; change it to done
  (om/update! app :state :show-perf-check-screen)

  (.log js/console (str "received quick check: " (:result res)))

  )

(defn handler-quick-check-results
  [res app]
  (om/transact! app :count inc)  ;; change it to done
  (om/update! app :state :show-quick-check-screen)

  (.log js/console (str "received quick check: " (:result res)))

  ;; now to get perf-check results
  (GET "/perf-check" {
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler #(handler-perf-check-results % app)
          })

  )

(defn handler-new-queries
  [res app]
  (om/transact! app :count inc)  ;; change it to done
  (om/update! app :state :show-query-screen)

  (.log js/console (str "received new queries: " (:queries res)))

  ;; now to get quick-check results
  (GET "/quick-check" {
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler #(handler-quick-check-results % app)
          })

  )

(defn handler-log-upload
  [res app]
  (om/transact! app :count inc)  ;; change it to done
  (om/update! app :state :show-exec-screen)
  (.log js/console (str "received " (:uploaded res)))

  ;; now to get new queries
  (GET "/new-queries" {
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler #(handler-new-queries % app)
          })
  )

(defn submit-log
  [app]
;;   (ajax-request "/log" :put
;;                 {:params {:log "my log"}
;;                  :handler handler-log-upload
;;                  :format (edn-request-format)
;;                  :response-format (edn-response-format)
;;                  })

  ;;- set screen to render screen
  (om/transact! app :state #(case % :upload-screen :run-test-screen :upload-screen))

  (PUT "/log" {:params {:log "myLog"}
               :format (edn-request-format)
               :response-format (edn-response-format)
               :handler #(handler-log-upload % app)
               })
;;   (js/setInterval
;;     (fn [] (om/transact! app :count #(if (> 10 %) (inc %) %)))
;;     1000)  ;;- this is a stupid hack

  )

(defn render-page-run-test [app]

  (dom/div #js {:style #js {:width "90%"} :className "center-block"}
;;         (dom/div #js {:className "lead"} (case (:count app)
;;            0 "Loading..."
;;            1 "Load -> Analyzing...."
;;            2 "Load -> Analyzed -> Quick Checking..."
;;            3 "Load -> Analyzed -> Quick Check Done -> Perf Testing"
;;            "Load -> Analyzed -> Quick Check Done -> Perf Test Done"))

    (render-status-bar (:count app))
    (u/render-status-table (:count app))

    (dom/hr nil)
    (dom/h1 nil "New Queries Found!")
    (render-new-query-table app)

    (dom/button #js {:onClick #(submit-log app)} "submit")))


(defn render-upload-screen [app]
  (dom/div
   #js {:style #js {:width "90%"} :className "center-block"}
   (dom/h2 nil "upload your log")
   (dom/div #js {:className "form-group"}
     (dom/label nil "Log:")
     (dom/textarea #js {:className "form-control"  :rows "8"} "fadfdfdf")
     (dom/button #js {:type "submit" :className "btn btn-default" :onClick #(submit-log app)} "Submit")
   ) ;; form-group
   ))


(defn render-progress-screen [app]
  (dom/div #js {:style #js {:width "90%"} :className "center-block"}
    (render-status-bar (:count app))
    (u/render-status-table (:count app))
           ))

(defn render-exec-screen [app]
  (render-progress-screen app)
  )

(defn render-query-sceen [app]
  (dom/div nil
    (render-exec-screen app)
    (dom/h3 #js {:style #js {:width "90%"} :className "center-block"} "New Queries")
    (render-new-query-table app)
  ))

(defn render-quick-check-screen [app]
  (dom/div nil
    (render-query-sceen app)
    (dom/h3 #js {:style #js {:width "90%"} :className "center-block"} "Quick-check results for all new queries")
    (render-quick-check-results-table app)
  ))

(defn render-perf-check-screen [app]
  (dom/div nil
    (render-quick-check-screen app)
    (dom/h3 #js {:style #js {:width "90%"} :className "center-block"} "Perf-check results for all new queries")
    (render-perf-check-results-table app)
  ))



;;-- views
(defn animation-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
;;       (js/setInterval
;;         (fn [] (om/transact! app :count #(if (> 10 %) (inc %) %)))
;;         1000)
                )
    om/IRender
    (render [_]
      (dom/div #js {:style #js {:width "90%"} :className "center-block"}
        (case (:state app)
           :upload-screen (render-upload-screen app)
           :show-exec-screen (render-exec-screen app)
           :show-query-screen (render-query-sceen app)
           :show-quick-check-screen (render-quick-check-screen app)
           :show-perf-check-screen (render-perf-check-screen app)
          (render-progress-screen app)))
      )))

(om/root animation-view app-state
  {:target (.getElementById js/document "app")})
