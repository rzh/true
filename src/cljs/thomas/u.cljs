(ns thomas.u
   (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [ajax.core :refer [GET PUT ajax-request edn-request-format edn-response-format]]
            ))

(defn progress-percentage
  "from :count to percentage of bar"
  [c]
  (str (case c
         0 "0.00"
         1 "25.00"
         2 "50.00"
         3 "75.00"
         "100.00") "%"))



;;-- render funcs
(defn render-status-table [status]
  (dom/table #js {:className "table table-bordered"}
    (dom/tbody nil
      (dom/tr nil
        (dom/td #js {:style #js {:width "75%"}} "Upload Data")
        (dom/td nil (if (< status 1) "in progress" "done")))  ;; tr

      (dom/tr nil
        (dom/td #js {:style #js {:width "75%"}} "Analyze Data")
        (dom/td nil (if (< status 2) "in progress" "done")))  ;; tr

      (dom/tr nil
        (dom/td #js {:style #js {:width "75%"}} "Quick Check New Queries")
        (dom/td nil (if (< status 3) "in progress" "done")))  ;; tr

      (dom/tr nil
        (dom/td #js {:style #js {:width "75%"}} "Perf Test New Queries")
        (dom/td nil (if (< status 4) "in progress" "done")))  ;; tr

      )))
