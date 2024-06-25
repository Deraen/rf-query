(ns example.main
  (:require [rf-query.core :as q :refer [use-query]]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom :as udom]
            [reagent-dev-tools.core :as dev-tools]))

(defn example-query [entity-id]
  {:key ["entity" entity-id]
   :query (fn [_]
            ;; NOTE: What about something that looks and works more like a re-frame event/fx?
            (js/Promise. (fn [resolve]
                           (js/setTimeout (fn []
                                            (resolve [{:hello (repeat (rand-int 10) "world")}]))
                                          (+ 200 (rand-int 1000))))))})

(defui main []
  (let [[entity-id set-entity-id] (uix/use-state 5)
        query (uix/use-memo (fn []
                              (example-query entity-id))
                            [entity-id])
        ;; NOTE: Needs to use use-memo now?
        response (use-query query)]
    ($ :div
       "hello " entity-id
       ($ :pre (pr-str response))
       ($ :button
          {:onClick (fn [_e] (set-entity-id (rand-int 100)))}
          "Change entity")
       ($ :button
          {:onClick (fn [_e] (q/invalidate-query ["entity" entity-id]))}
          "Invalidate"))))

(defonce root (uix.dom/create-root (js/document.getElementById "app")))

(defn restart! []
  (uix.dom/render-root ($ main) root))

(restart!)

(when ^boolean goog.DEBUG
  (dev-tools/start! {:state-atom re-frame.db/app-db}))
