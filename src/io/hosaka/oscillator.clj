(ns io.hosaka.oscillator
  (:require [com.stuartsierra.component :as component]
            [manifold.deferred          :as d]
            [manifold.stream            :as s]
            [java-time                  :as time]
            [config.core                :refer [env]]
            [io.hosaka.common.rabbitmq  :refer [new-rabbitmq publish]])
  (:gen-class))

(def time-zones
  {"UTC" "UTC"
   "CST" "US/Central"})

(def time-fields
  '(:am-pm-of-day
   :clock-hour-of-am-pm
   :clock-hour-of-day
   :day-of-month
   :day-of-quarter
   :day-of-week
   :day-of-year
   :hour-of-am-pm
   :hour-of-day
   :minute-of-day
   :minute-of-hour
   :modified-julian-day
   :month-of-year
   :quarter-of-year
   :second-of-day
   :second-of-minute
   :week-based-year
   :week-of-week-based-year
   :year
   ))

(defn get-time-fields [t]
  (let [values (apply time/as t time-fields)]
    (loop [time-fields time-fields,
           values values,
           kv []]
      (if (empty? values)
        (apply hash-map kv)
        (recur (rest time-fields)
               (rest values)
               (conj kv (first time-fields) (first values)))))))

(defn tick [rabbitmq now]
  (doall
   (map
    (fn [[tz-label tz]]
      (let [date-time (time/offset-date-time now tz)
            topic (str tz-label (time/format "/mm/H/ee/dd/MM" date-time))]
        (println topic)
        (publish rabbitmq "oscillator" topic (get-time-fields date-time))))
    time-zones)))

(defrecord Oscillator [rabbitmq stream]
  component/Lifecycle
  (start [this]
    (let [stream (s/periodically 60000 time/instant)]
      (s/consume (partial tick rabbitmq) stream)
      (assoc this :stream stream)))

  (stop [this]
    this))

(defn new-oscillator []
  (component/using
   (map->Oscillator {})
   [:rabbitmq]))


(defn init-system [env]
   (component/system-map
    :oscillator (new-oscillator)
    :rabbitmq (new-rabbitmq env)))

(defonce system (atom {}))

(defn -main
  [& args]
  (let [semaphore (d/deferred)]

    (reset! system (init-system env))

    (swap! system component/start)

    (deref semaphore)

    (component/stop @system)

    (shutdown-agents)))
