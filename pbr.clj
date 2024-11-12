;
; Copyright © 2021 Peter Monks
;
; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.
;
; SPDX-License-Identifier: MPL-2.0
;

#_{:clj-kondo/ignore [:unresolved-namespace]}
(defn set-opts
  [opts]
  (assoc opts
         :lib          'com.github.pmonks/pbr
         :version      (pbr/calculate-version 2 0)
         :prod-branch  "release"         
         :ignore-deps? true
         :write-pom    true
         :validate-pom true
         :pom          {:description      "Peter's Build Resources for Clojure tools.build projects."
                        :url              "https://github.com/pmonks/pbr"
                        :licenses         [:license   {:name "MPL-2.0" :url "https://www.mozilla.org/en-US/MPL/2.0/"}]
                        :developers       [:developer {:id "pmonks" :name "Peter Monks" :email "pmonks+pbr@gmail.com"}]
                        :scm              {:url                  "https://github.com/pmonks/pbr"
                                           :connection           "scm:git:git://github.com/pmonks/pbr.git"
                                           :developer-connection "scm:git:ssh://git@github.com/pmonks/pbr.git"
                                           :tag                  (tc/git-tag-or-hash)}
                        :issue-management {:system "github" :url "https://github.com/pmonks/pbr/issues"}}
         :codox        {:namespaces ['pbr.tasks]}
         :antq         {:transitive false}))   ; This locks up in this project
