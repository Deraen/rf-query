(ns rf-query.core
  (:require
   [uix.core :as uix]
   [re-frame.core :as rf]
   [rf-query.use-subscribe :refer [use-subscribe]]))

(defn ts []
  (.getTime (js/Date.)))

(defn is-stale [query-state query-def]
  (or (:is-invalidated query-state)
      (> (ts) (+ (:ts query-state)
                 (:state-time query-def 0)))))

(defn is-active [query-state]
  (pos? (:uses query-state)))

(defn make-initial-state [query-def]
  {:ts (ts)
   :is-initial-fetching true
   :is-fetching true
   :is-invalidated false
   :data nil
   :error nil
   ;; How does TSQ keep track of which queries are active?
   ;; - counter
   ;; - ID (random, useRef or something) for each use-query and keep track of set of registered use-queries?
   :uses 1})

(defn refetch-query [query-state query-def]
  (assoc query-state
         ;; Should this be the ts when the response was received or initialized?
         :ts (ts)
         :is-fetching true
         :is-invalidated false
         :uses (inc (:uses query-state))))

(defn new-response [query-state response]
  (assoc query-state
         :is-fetching false
         :is-initial-fetching false
         :error nil
         :data response))

(defn new-error [query-state error]
  (assoc query-state
         :is-fetching false
         :is-initial-fetching false
         :error error
         :data nil))

(rf/reg-event-fx ::run
  (fn [_ [_ {:keys [query] :as query-def}]]
    (-> (query)
        (.then (fn [response]
                 (rf/dispatch [::success query-def response])))
        (.catch (fn [error]
                  (rf/dispatch [::error query-def error]))))
    nil))

(rf/reg-event-fx ::success
  (fn [{:keys [db]} [_ {query-key :key} response]]
    {:db (update db ::queries update query-key new-response response)}))

(rf/reg-event-fx ::error
  (fn [{:keys [db]} [_ {query-key :key} response]]
    {:db (update db ::queries update query-key new-error response)}))

(rf/reg-event-fx ::decrement-uses
  (fn [{:keys [db]} [_ {query-key :key :as query-def}]]
    (let [query-state (get (::queries db) query-key)]
      {:db (update db ::queries update query-key update :uses dec)
       :fx [[:dispatch-later {:ms (:gc-time query-def 5000)
                              :dispatch [::maybe-cleanup query-def (:ts query-state)]}]]})))

;; Remove the query-state from app-db after gc-time of when it becomes unused (use-query unmount)
(rf/reg-event-fx ::maybe-cleanup
  (fn [{:keys [db]} [_ {query-key :key} old-ts]]
    (let [query-state (get (::queries db) query-key)]
      (if (and (zero? (:uses query-state))
               (= old-ts (:ts query-state)))
        {:db (update db ::queries dissoc query-key)}
        nil))))

(rf/reg-event-fx ::invalidate
  (fn [{:keys [db]} [_ query-key]]
    {:db (update db ::queries update query-key assoc :is-invalidated true)}))

(rf/reg-event-fx ::maybe-run-query
  (fn [{:keys [db]} [_ {query-key :key, :as query-def}]]
    (let [query-state (get (::queries db) query-key)]
      (cond
        (nil? query-state)
        {:db (update db ::queries assoc query-key (make-initial-state query-def))
         :fx [[:dispatch [::run query-def]]]}

        ;; refetch-on-mount option?
        (and (is-stale query-state query-def)
             (not (:is-fetching query-state)))
        {:db (update db ::queries update query-key refetch-query query-def)
         :fx [[:dispatch [::run query-def]]]}

        :else
        {:db (update db ::queries update query-key update :uses inc)}))))

(rf/reg-sub ::get-state
  (fn [db [_ {query-key :key}]]
    (dissoc (get (::queries db) query-key) :ts)))

(defn use-query [query-def]
  ;; NOTE: query-def should be stable
  (let [query-state (use-subscribe [::get-state query-def])
        is-invalidated (:is-invalidated query-state)]
    (uix/use-effect (fn []
                      (rf/dispatch [::maybe-run-query query-def])
                      (fn []
                        (rf/dispatch [::decrement-uses query-def])
                        nil))
                    [query-def is-invalidated])
    query-state))

(defn invalidate-query [query-key]
  (rf/dispatch [::invalidate query-key]))
