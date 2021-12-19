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

#_{:clj-kondo/ignore [:unused-namespace]}   ; Because there are various namespaces the pbr script may want to use
(ns build
  "PBR generic build script.

For more information, run:

clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.string          :as s]
            [clojure.set             :as set]
            [clojure.pprint          :as pp]
            [clojure.java.io         :as io]
            [clojure.java.shell      :as sh]
            [clojure.tools.build.api :as b]
            [org.corfield.build      :as bb]
            [tools-convenience.api   :as tc]
            [tools-pom.tasks         :as pom]
            [tools-licenses.tasks    :as lic]
            [pbr.tasks               :as pbr]))

(declare set-opts)

(load-file "./pbr.clj")

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

(defn test
  "Run the tests."
  [opts]
  (bb/run-tests (set-opts opts)))

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
  (let [opts (set-opts opts)]
    (outdated opts)
    (try (check opts) (catch Exception _))   ; Ignore errors until https://github.com/athos/clj-check/issues/4 is fixed
    (test opts)
    (lint opts)))

(defn licenses
  "Attempts to list all licenses for the transitive set of dependencies of the project, using SPDX license expressions."
  [opts]
  (-> opts
      (set-opts)
      (lic/licenses)))

(defn check-asf-policy
  "Checks this project's dependencies' licenses against the ASF's 3rd party license policy (https://www.apache.org/legal/resolved.html)."
  [opts]
  (-> opts
      (set-opts)
      (lic/check-asf-policy)))

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
      (pom/pom)
      (bb/jar)))

(defn uber
  "Create an uber jar."
  [opts]
  (-> opts
      (set-opts)
      (pom/pom)
      (bb/uber)))

(defn install
  "Install the library locally e.g. so it can be tested by downstream dependencies"
  [opts]
  (jar opts)
  (bb/install (set-opts opts)))

(defn deploy
  "Deploys the library JAR to Clojars."
  [opts]
  (-> opts
      (set-opts)
      (pbr/deploy)))

(defn docs
  "Generates codox documentation"
  [_]
  (tc/ensure-command "clojure")
  (tc/exec "clojure -Srepro -X:codox"))