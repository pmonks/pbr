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

#_{:clj-kondo/ignore [:unused-namespace]}   ; Because there are various namespaces each project's pbr.clj script may want to use
(ns pbr.build
  "PBR turnkey build script.

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
            [tools-licenses.tasks    :as lic]
            [pbr.tasks               :as pbr]))

(defn- set-opts [_] (throw (ex-info "Default set-opts fn called. Did you forget to redefine it in your pbr.clj script?" {})))
(load-file "./pbr.clj")  ; Load the project's own pbr.clj script (that must redefine set-opts)

(defn clean
  "Clean up the project."
  [opts]
  (-> opts
      (set-opts)
      (bb/clean)))

(defn check
  "Check the code by AOT compiling it (and throwing away the result)."
  [opts]
  (-> opts
      (set-opts)
      (pbr/check)))

(defn outdated
  "Check for outdated dependencies (using antq)."
  [opts]
  (-> opts
      (set-opts)
      (pbr/antq-outdated)))

(defn upgrade
  "Upgrade any outdated dependencies (using antq). NOTE: does not prompt for confirmation!"
  [opts]
  (-> opts
      (set-opts)
      (pbr/antq-upgrade)))

(defn test
  "Run the tests."
  [opts]
  (-> opts
      (set-opts)
      (pbr/run-tests)))

(defn nvd
  "Run an NVD vulnerability check"
  [opts]
  (-> opts
      (set-opts)
      (pbr/nvd)))

(defn kondo
  "Run the clj-kondo linter."
  [opts]
  (-> opts
      (set-opts)
      (pbr/kondo)))

(defn eastwood
  "Run the eastwood linter."
  [opts]
  (-> opts
      (set-opts)
      (pbr/eastwood)))

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
;    (try (nvd opts) (catch Exception _))     ; This is exceptionally slow, and inappropriate for every CI build
    (lint opts)))

(defn licenses
  "Attempts to list all licenses for the transitive set of dependencies of the project, as SPDX license identifiers."
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

(defn pom
  "Generates a comprehensive pom.xml for the project."
  [opts]
  (-> opts
      (set-opts)
      (pbr/pom)))

(defn jar
  "Generates a library JAR for the project."
  [opts]
  (-> opts
      (set-opts)
      (pbr/pom)
      (pbr/jar)))

(defn uber
  "Create an uber jar."
  [opts]
  (-> opts
      (set-opts)
      (pbr/uber)))

(defn uberexec
  "Creates an executable uber jar. NOTE: does not bundle a JRE, though one is still required."
  [opts]
  (-> opts
      (set-opts)
      (pbr/uberexec)))

(defn install
  "Install the library locally e.g. so it can be tested by downstream dependencies"
  [opts]
  (jar opts)
  (-> opts
      (set-opts)
      (bb/install)))

(defn deploy
  "Deploys the library JAR to Clojars."
  [opts]
  (-> opts
      (set-opts)
      (pbr/deploy)))

(defn docs
  "Generates documentation (using codox)."
  [opts]
  (-> opts
      (set-opts)
      (pbr/codox)))
