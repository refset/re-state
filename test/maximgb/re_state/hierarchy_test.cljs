(ns maximgb.re-state.hierarchy-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs :refer [machine
                                                  fx-action
                                                  interpreter!
                                                  interpreter-start!
                                                  re-state-service]]))

(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest child-parent-interpreter-communication-test
  (testing "Child to parent interpreter communication test"
    (async done
           (let [c (casync/timeout 100)
                 pm (machine {:id      :parent-machine
                              :initial :ready
                              :states {:ready   {:on    {:child-ready :running}}
                                       :running {:entry (fn [] (casync/put! c :ok))}}})

                 cm (machine {:id      :child-machine
                              :initial :ready
                              :states  {:ready {:entry :notify-parent}}}

                             {:actions {:notify-parent (fx-action
                                                        [[re-state-service :parent []]]
                                                        (fn [cofx]
                                                          (let [parent (get-in cofx [re-state-service :parent])]
                                                            {re-state-service [:send! [parent :child-ready]]})))}})
                 pi (interpreter! pm)
                 ci (interpreter! cm pi)]

             (casync/go
               (interpreter-start! pi)
               (interpreter-start! ci)
               (is (= (casync/<! c) :ok) "Child has send message to parent")
               (done))))))
