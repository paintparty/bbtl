(ns bbtl.core-test
  (:require [bbtl.core :as core]
            [bbtl.tasks :as tasks]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(deftest cli-help-does-not-preflight-or-discover
  (let [err        (java.io.StringWriter.)
        discovered (atom false)
        output     (with-redefs-fn
                     {#'core/executable-available?
                      (fn [_] (throw (ex-info "preflight should not run" {})))
                      #'tasks/discover
                      (fn [_]
                        (reset! discovered true)
                        {:status :ok :tasks []})}
                     #(binding [*err* err]
                        (with-out-str
                          (is (= 0 (core/run-cli! "--help"))))))]
    (is (str/includes? output "Usage:"))
    (is (false? @discovered))
    (is (= "" (str err)))))

(deftest cli-version-flags-print-the-bbtl-version
  (doseq [flag ["-version" "--version" "version"]]
    (let [err    (java.io.StringWriter.)
          output (binding [*err* err]
                   (with-out-str
                     (is (= 0 (core/run-cli! flag)))))]
      (is (= (str "bbtl " core/version "\n") output))
      (is (= "" (str err))))))

(deftest cli-rejects-unsupported-arguments
  (doseq [argument ["wat" "tasks" "t"]]
    (let [err    (java.io.StringWriter.)
          output (binding [*err* err]
                   (with-out-str
                     (is (= 1 (core/run-cli! argument)))))]
      (is (= "" output))
      (is (str/includes? (str err) "Usage:")))))

(deftest cli-reports-missing-bb-before-discovery
  (let [err        (java.io.StringWriter.)
        discovered (atom false)]
    (with-redefs-fn {#'core/executable-available? (constantly false)
                     #'tasks/discover (fn [_]
                                        (reset! discovered true)
                                        {:status :ok :tasks []})}
      #(let [output (binding [*err* err]
                      (with-out-str
                        (is (= 1 (core/run-cli!)))))]
         (is (= "" output))
         (is (false? @discovered))
         (is (str/includes? (str err) "Required executable not found: bb"))
         (is (str/includes? (str err) "https://babashka.org/"))))))

(deftest cli-reports-discovery-errors-and-empty-task-list
  (with-redefs-fn {#'core/executable-available? (constantly true)}
    #(do
       (let [err (java.io.StringWriter.)]
         (with-redefs [tasks/discover (constantly {:status :missing})]
           (let [output (binding [*err* err]
                          (with-out-str
                            (is (= 1 (core/run-cli!)))))]
             (is (= "" output))
             (is (str/includes? (str err) "No bb.edn (with tasks) was found in:")))))
       (let [err (java.io.StringWriter.)]
         (with-redefs [tasks/discover
                       (constantly {:status :invalid
                                    :path   "/tmp/project/bb.edn"
                                    :error  "Map entry is missing a value"})]
           (let [output (binding [*err* err]
                          (with-out-str
                            (is (= 1 (core/run-cli!)))))]
             (is (= "" output))
             (is (str/includes? (str err) "Invalid bb.edn:"))
             (is (str/includes? (str err) "/tmp/project/bb.edn"))
             (is (str/includes? (str err) "Map entry is missing a value")))))
       (with-redefs [tasks/discover
                     (constantly {:status :ok
                                  :path   "/tmp/project/bb.edn"
                                  :tasks  []})]
         (let [err    (java.io.StringWriter.)
               output (binding [*err* err]
                        (with-out-str
                          (is (= 0 (core/run-cli!)))))]
           (is (str/includes? output "No public bb tasks found in:"))
           (is (str/includes? output "/tmp/project/bb.edn"))
           (is (= "" (str err))))))))

(deftest cli-propagates-picker-exit-code
  (with-redefs-fn {#'core/executable-available? (constantly true)
                   #'tasks/discover (constantly {:status :ok
                                                 :path   "/tmp/project/bb.edn"
                                                 :tasks  [{:name "test" :doc ""}]})
                   #'tasks/run-picker! (fn [task-list]
                                         (is (= [{:name "test" :doc ""}] task-list))
                                         42)}
    #(is (= 42 (core/run-cli!)))))
