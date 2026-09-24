(ns bbtl.tasks-test
  (:require [charm.message :as msg]
            [charm.program :as program]
            [bbtl.tasks :as tasks]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- temp-dir []
  (str (Files/createTempDirectory
        "bbtl-tasks-test-"
        (make-array FileAttribute 0))))

(defn- cleanup! [path]
  (let [file (io/file path)]
    (when (.exists file)
      (doseq [child (reverse (file-seq file))]
        (io/delete-file child true)))))

(defn- write-bb-edn! [dir contents]
  (let [path (str dir "/bb.edn")]
    (spit path contents)
    path))

(defn- strip-ansi [s]
  (str/replace s #"\u001B\[[0-9;]*m" ""))

(defn- line-widths [rendered]
  (map count (str/split-lines (strip-ansi rendered))))

(deftest discover-preserves-public-task-order-without-evaluation
  (let [dir (temp-dir)
        path (write-bb-edn!
              dir
              "{:tasks {:require [[clojure.string :as str]]
                        build {:doc \"Builds artifacts\" :task (println :build)}
                        -private {:doc \"Hidden\" :task (println :private)}
                        :keyword-task {:doc \"Hidden keyword task\" :task (println :keyword)}
                        \"string-task\" {:doc \"Hidden string task\" :task (println :string)}
                        test {:task (println :test)}
                        deploy {:doc (+ 1 2) :task (throw (ex-info \"no\" {}))}
                        lint (println :lint)}}")]
    (try
      (is (= {:status :ok
              :path   path
              :tasks  [{:name "build" :doc "Builds artifacts"}
                       {:name "test" :doc ""}
                       {:name "deploy" :doc ""}
                       {:name "lint" :doc ""}]}
             (tasks/discover path)))
      (finally
        (cleanup! dir)))))

(deftest discover-reports-missing-and-invalid-bb-edn
  (let [dir (temp-dir)]
    (try
      (testing "missing"
        (let [path (str dir "/missing.edn")]
          (is (= {:status :missing :path path}
                 (tasks/discover path)))))
      (testing "invalid"
        (let [path (write-bb-edn! dir "{:tasks {broken}")]
          (is (= :invalid (:status (tasks/discover path))))
          (is (= path (:path (tasks/discover path))))
          (is (seq (:error (tasks/discover path))))))
      (finally
        (cleanup! dir)))))

(deftest discover-rejects-invalid-task-map-shapes-and-keeps-empty-configs-quiet
  (let [dir (temp-dir)]
    (try
      (doseq [[contents expected-status]
              [["[]" :invalid]
               ["{:tasks []}" :invalid]]]
        (let [path (write-bb-edn! dir contents)]
          (is (= expected-status (:status (tasks/discover path))))))
      (doseq [contents ["{}" "{:tasks {}}" "{:tasks {-private {:doc \"hidden\"}}}"]]
        (let [path (write-bb-edn! dir contents)]
          (is (= {:status :ok :path path :tasks []}
                 (tasks/discover path)))))
      (finally
        (cleanup! dir)))))

(deftest discover-does-not-evaluate-task-forms
  (let [dir       (temp-dir)
        marker    (str dir "/evaluated")
        path      (write-bb-edn!
                   dir
                   (str "{:tasks {test {:task (spit "
                        (pr-str marker)
                        " \"executed\")}}}"))]
    (try
      (is (= :ok (:status (tasks/discover path))))
      (is (false? (.exists (io/file marker))))
      (finally
        (cleanup! dir)))))

(deftest run-picker-propagates-selection-and-cancellation-exit-codes
  (let [selected-task {:name "test" :doc ""}
        received      (atom nil)]
    (with-redefs-fn {#'program/run (fn [_] {:selected-task selected-task})
                     #'bbtl.tasks/run-task! (fn [task-name]
                                              (reset! received task-name)
                                              42)}
      #(do
         (is (= 42 (tasks/run-picker! [selected-task])))
         (is (= "test" @received)))))
  (with-redefs [program/run (constantly {:exit-code 0})]
    (is (= 0 (tasks/run-picker! [{:name "test" :doc ""}]))))
  (with-redefs [program/run (constantly {:exit-code 130})]
    (is (= 130 (tasks/run-picker! [{:name "test" :doc ""}])))))

(deftest task-output-starts-on-a-fresh-line
  (let [output (java.io.StringWriter.)]
    (with-redefs [bbtl.tasks/start-task-process! (constantly 0)]
      (binding [*out* output]
        (is (= 0 (#'bbtl.tasks/run-task! "install")))))
    (is (= "\n" (str output)))))

(deftest picker-key-events-control-exit-and-selection
  (let [state {:tasks        [{:name "test" :doc ""}
                              {:name "deploy" :doc ""}]
               :selected-idx 0}]
    (let [[escaped command] (#'tasks/picker-update state (msg/key-press :escape))]
      (is (= 0 (:exit-code escaped)))
      (is (= program/quit-cmd command)))
    (let [[cancelled command] (#'tasks/picker-update state (msg/key-press "c" :ctrl true))]
      (is (= 130 (:exit-code cancelled)))
      (is (= program/quit-cmd command)))
    (let [[selected command]
          (#'tasks/picker-update (assoc state :selected-idx 1)
                                 (msg/key-press :enter))]
      (is (= {:name "deploy" :doc ""} (:selected-task selected)))
      (is (= program/quit-cmd command)))))

(deftest picker-layout-stays-bounded-on-narrow-and-short-terminals
  (let [tasks  [{:name "very-long-task-name-that-wraps"
                 :doc  "A long description that should wrap without exceeding the picker width."}
                {:name "test" :doc "Runs tests"}
                {:name "deploy" :doc "Deploys the project"}]
        states [{:tasks tasks :selected-idx 0 :scroll-offset 0
                 :term-width 20 :term-height 24}
                {:tasks tasks :selected-idx 2 :scroll-offset 0
                 :term-width 80 :term-height 10}
                {:tasks (vec (concat tasks tasks tasks tasks))
                 :selected-idx 8 :scroll-offset 5
                 :term-width 59 :term-height 12}]]
    (doseq [state states]
      (let [rendered (#'tasks/picker-view state)
            widths   (line-widths rendered)
            limit    (min 80 (:term-width state))]
        (is (every? #(<= % limit) widths))
        (is (<= (count widths) (:term-height state)))))))

(deftest picker-shows-counted-overflow-around-the-focused-task
  (let [state {:tasks (mapv (fn [index]
                              {:name (str "task-" index)})
                            (range 12))
               :selected-idx 8
               :scroll-offset 0
               :animation-phase :done
               :term-width 59
               :term-height 12}
        rendered (#'tasks/picker-view state)
        plain (strip-ansi rendered)]
    (is (<= (count (str/split-lines plain)) (:term-height state)))
    (is (str/includes? plain "> task-8"))
    (is (str/includes? rendered "\033[2m ↑ 5 more\033[0m"))
    (is (str/includes? rendered "\033[2m ↓ 3 more\033[0m"))))

(deftest picker-bounds-wrapped-task-content-to-its-row-budget
  (let [state {:tasks (mapv (fn [index]
                              {:name (str "very-" (apply str (repeat 10 "long-")) index)
                               :doc (apply str (repeat 8 "long documentation "))})
                            (range 8))
               :selected-idx 5
               :animation-phase :done
               :term-width 20
               :term-height 10}
        rendered (#'tasks/picker-view state)]
    (is (<= (count (str/split-lines rendered)) (:term-height state)))
    (is (str/includes? (strip-ansi rendered) "> very-long-"))))

(deftest picker-styles-cta-hint-and-active-task
  (let [state           {:tasks [{:name "test" :doc "Runs tests"}
                                 {:name "deploy" :doc "Deploys the project"}]
                         :selected-idx 0
                         :scroll-offset 0
                         :term-width 59
                         :term-height 24}
        initial         (#'tasks/picker-view state)
        [moved-state _] (#'tasks/picker-update state (msg/key-press :down))
        moved           (#'tasks/picker-view moved-state)
        [up-state _]    (#'tasks/picker-update state (msg/key-press :up))
        moved-up        (#'tasks/picker-view up-state)
        [j-state _]     (#'tasks/picker-update state (msg/key-press "j"))
        moved-with-j    (#'tasks/picker-view j-state)]
    (is (not (str/starts-with? initial "\n")))
    (is (str/includes? initial
                       (str "\033[1m" tasks/bb-task-cta "\033[0m"
                            "\033[2m (Use arrow keys)\033[0m")))
    #_(is (str/includes? initial "\033[2m──────────────────────────\033[0m"))
    (is (not (str/includes? initial "\n\n\033[1m> test")))
    (is (str/includes? initial "Use arrow keys"))
    (is (str/includes? initial "\033[1m> test    Runs tests\033[0m"))
    (is (str/includes?
         (#'tasks/picker-view
          (assoc state :tasks [{:name "ci:deploy"
                                :doc "Run the CI pipeline and deploy the JAR."}]))
         "> ci:deploy  Run the CI pipeline and deploy the JAR."))
    (is (not (str/includes? moved "Use arrow keys")))
    (is (not (str/includes? moved-with-j "Use arrow keys")))
    (is (str/includes? moved-up "Use arrow keys"))
    (is (str/includes? moved
                       (str "\033[1m" tasks/bb-task-cta "\033[0m")))))

(deftest selected-task-view-uses-default-cta-and-one-empty-line
  (let [rendered (#'tasks/selected-task-view {:name "test"})]
    (is (str/starts-with? rendered
                          (str "\033[1m" tasks/bb-task-cta "\033[0m")))
    (is (= 2 (count (re-seq #"\n" rendered))))
    (is (str/includes? rendered "\033[1mtest\033[0m"))))

(deftest tasks-gap-controls-spacing-around-and-between-tasks
  (let [state {:tasks         [{:name "test" :doc "Runs tests"}
                               {:name "deploy" :doc "Deploys the project"}]
               :selected-idx  0
               :scroll-offset 0
               :term-width    59
               :term-height   24}]
    (with-redefs [tasks/tasks-gap 0]
      (is (= [(str tasks/bb-task-cta " (Use arrow keys)")
              "> test    Runs tests"
              "  deploy  Deploys the project"]
             (str/split-lines (strip-ansi (#'tasks/picker-view state))))))
    (with-redefs [tasks/tasks-gap 1]
      (is (= [(str tasks/bb-task-cta " (Use arrow keys)")
              ""
              "> test    Runs tests"
              ""
              "  deploy  Deploys the project"]
             (str/split-lines (strip-ansi (#'tasks/picker-view state))))))
    (with-redefs [tasks/tasks-gap 2]
      (is (= [(str tasks/bb-task-cta " (Use arrow keys)")
              ""
              ""
              "> test    Runs tests"
              ""
              ""
              "  deploy  Deploys the project"]
             (str/split-lines (strip-ansi (#'tasks/picker-view state))))))))

(deftest picker-animation-types-cta-and-reveals-tasks
  ;; reveal-animation? defaults to false (picker paints the full list on the
  ;; first frame). Force it on here to exercise the reveal mechanics.
  (with-redefs [tasks/reveal-animation? true]
   (let [task-list [{:name "test" :doc "Runs tests"}
                   {:name "deploy" :doc "Deploys the project"}]
        [initial command] (#'tasks/picker-init task-list)
        tick             (msg/key-press tasks/tasks-animation-tick)
        advance          (fn [state]
                           (first (#'tasks/picker-update state tick)))
        first-secondary  (nth (iterate advance initial)
                              (inc (count tasks/bb-task-cta)))
        first-default    (advance first-secondary)
        second-secondary (advance first-default)
        typing-state     (nth (iterate advance initial)
                              (count tasks/bb-task-cta))
        initial-view     (#'tasks/picker-view initial)
        typing-view      (#'tasks/picker-view typing-state)
        cta-view         (#'tasks/picker-view first-secondary)
        first-view       (#'tasks/picker-view first-default)
        second-view      (#'tasks/picker-view second-secondary)]
    (is (= :cmd (:type command)))
    (is (= "" initial-view))
    (is (str/ends-with? typing-view "\033[2m (Use arrow keys)\033[0m"))
    (is (str/starts-with? cta-view
                          (str "\033[1m" tasks/bb-task-cta "\033[0m"
                               "\033[2m (Use arrow keys)\033[0m")))
    (is (str/includes? cta-view "\033[2m> test"))
    (is (str/includes? first-view "\033[1m> test"))
    (is (not (str/includes? first-view "\033[2m  deploy")))
    (is (str/includes? second-view "\033[2m  deploy"))
    (is (str/includes? second-view "\033[1m> test")))))

(deftest picker-starts-on-a-new-line-after-the-cli-prompt
  (let [output (java.io.StringWriter.)]
    (with-redefs [program/run (constantly {:exit-code 0})]
      (binding [*out* output]
        (is (= 0 (tasks/run-picker! [])))))
    (is (= "" (str output)))))
