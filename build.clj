;
; Copyright © 2021 Peter Monks
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns build
  "Build script for PBR.

For more information, run:

clojure -A:deps -T:build help/doc"
  (:require [clojure.tools.build.api :as b]
            [codox.main              :as codox]
            [org.corfield.build      :as bb]
            [pbr.tasks               :as pbr]
            [pbr.convenience         :as pbrc]))

(def lib       'com.github.pmonks/pbr)
(def version   (format "1.0.%s" (b/git-count-revs nil)))

; Utility fns
(defn- set-opts
  [opts]
  (assoc opts
         :lib       lib
         :version   version
         :write-pom true
         :pom       {:description      "Peter's Build Resources for Clojure tools.build projects"
                     :url              "https://github.com/pmonks/pbr"
                     :licenses         [:license   {:name "Apache License 2.0" :url "http://www.apache.org/licenses/LICENSE-2.0.html"}]
                     :developers       [:developer {:id "pmonks" :name "Peter Monks" :email "pmonks+pbr@gmail.com"}]
                     :scm              {:url "https://github.com/pmonks/pbr" :connection "scm:git:git://github.com/pmonks/pbr.git" :developer-connection "scm:git:ssh://git@github.com/pmonks/pbr.git"}
                     :issue-management {:system "github" :url "https://github.com/pmonks/pbr/issues"}}
         :codox     {:source-paths ["src"]
                     :source-uri   "https://github.com/pmonks/pbr/blob/main/{filepath}#L{line}"}))

; Build tasks
(defn clean
  "Clean up the project."
  [opts]
  (bb/clean (set-opts opts)))

(defn check
  "Check the code by compiling it."
  [opts]
  (bb/run-task (set-opts opts) [:check]))

(defn outdated
  "Check for outdated dependencies."
  [opts]
  (bb/run-task (set-opts opts) [:outdated]))

(defn kondo
  "Run the clj-kondo linter."
  [opts]
  (bb/run-task (set-opts opts) [:kondo]))

(defn eastwood
  "Run the eastwood linter."
  [opts]
  (bb/run-task (set-opts opts) [:eastwood]))

(defn lint
  "Run all linters."
  [opts]
  (-> opts
    (kondo)
    (eastwood)))

(defn ci
  "Run the CI pipeline."
  [opts]
  (-> opts
    (outdated)
;    (check)    ; Removed until https://github.com/athos/clj-check/issues/4 is fixed
    (lint)))

(defn licenses
  "Attempts to determine all licenses used by all dependencies in the project."
  [opts]
  (pbr/licenses opts))

(defn check-release
  "Check that a release can be done from the current directory."
  [opts]
  (-> opts
      (set-opts)
      (ci)
      (pbr/check-release)))

(defn release
  "Release a new version of the library."
  [opts]
  (check-release opts)
  (-> opts
      (set-opts)
      (pbr/release)))

(defn jar
  "Generates a library JAR for the project."
  [opts]
  (-> opts
      (set-opts)
      (pbr/pom)
      (bb/jar)))

(defn deploy
  "Deploys the library JAR to Clojars."
  [opts]
  (let [current-branch (pbrc/git-branch)]
    (if (= current-branch "main")
      (let [deploy-opts (assoc (set-opts opts) :version (pbrc/git-nearest-tag))]
        (pbr/pom   deploy-opts)  ; Note: we can't simply call (pom) again here, since it clobbers our custom deploy-opts
        (bb/jar    deploy-opts)  ; Note: we can't simply call (jar) again here, since it clobbers our custom deploy-opts
        (bb/deploy deploy-opts))
      (throw (ex-info (str "deploy task must be run from 'main' branch (current branch is '" current-branch "').") (into {} opts))))))

(defn docs
  "Generates codox documentation"
  [opts]
  (codox/generate-docs (into (:codox (set-opts nil))
                             opts)))
