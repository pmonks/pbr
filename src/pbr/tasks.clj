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

(ns pbr.tasks
  "Standard implementations of common tools.build tasks."
  (:require [clojure.string          :as s]
            [clojure.java.io         :as io]
            [clojure.pprint          :as pp]
            [clojure.java.shell      :as sh]
            [clojure.data.json       :as json]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [antq.core               :as aq]
            [antq.upgrade            :as au]
            [clj-kondo.core          :as kd]
;            [eastwood.lint           :as ew]
;            [codox.main              :as cx]
;            [nvd.task                :as nvd]
            [tools-convenience.api   :as tc]
            [tools-pom.tasks         :as pom]))

(def ^:private ver-clj-check   {:git/sha "518d5a1cbfcd7c952f548e6dbfcb9a4a5faf9062"}) ; Latest version of https://github.com/athos/clj-check
(def ^:private ver-test-runner {:git/tag "v0.5.1" :git/sha "dfb30dd"})                ; Latest version of https://github.com/cognitect-labs/test-runner
(def ^:private ver-logback     {:mvn/version "1.4.1"})
(def ^:private ver-slf4j       {:mvn/version "2.0.1"})
(def ^:private ver-eastwood    {:mvn/version "1.3.0"})
(def ^:private ver-codox       {:mvn/version "0.10.8"})

; Utility functions

(defn is-directory?
  "Returns true if f (a File or String) is a directory, or a symlink to a directory, or false otherwise."
  [f]
  (let [d (io/file f)]
    (.isDirectory (.getCanonicalFile d))))

(defn delete-dir
  "Deletes the given directory and all of its contents, recursively.  May throw IOException.  Note: this is a utility fn, not a task fn."
  [dir]
  (let [d (io/file dir)]
    (when (and (.exists d)
               (is-directory? d))
      (doall (map io/delete-file (reverse (file-seq d)))))))

(defn default-basis
  "See https://clojure.github.io/tools.build/clojure.tools.build.api.html#var-create-basis for details"
  []
  (b/create-basis {}))

(defn src-dirs
  "Returns the source directories (a sequence of Strings) for the default basis. Note: this is a utility fn, not a task fn."
  []
  (get (default-basis) :paths ["src"]))

(defn test-dirs
  "Returns the test directories (a sequence of Strings) for the given opts.  Note: these directories may not exist. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :test-paths ["test"]))

(defn target-dir
  "Returns the target dir (a String) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :target "target"))

(defn classes-dir
  "Returns the classes dir (a String) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (str (target-dir opts) "/classes"))

(defn pom-file-name
  "Returns the pom file name (without path) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :pom-file "pom.xml"))

(defn jar-file-name
  "Returns the jar file name (without path) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :jar-file (str (name (:lib opts)) "-" (:version opts) ".jar")))

(defn fq-jar-file-name
  "Returns the fully-qualified jar file name (with path) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (str (target-dir opts) "/" (jar-file-name opts)))

(defn uberjar-file-name
  "Returns the uberjar file name (without path) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :uber-file (str (name (:lib opts)) "-standalone.jar")))

(defn fq-uberjar-file-name
  "Returns the fuly-qualified uberjar file name (with path) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (str (target-dir opts) "/" (uberjar-file-name opts)))

(defn dev-branch
  "Returns the name of the dev branch (a String) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :dev-branch "dev"))

(defn prod-branch
  "Returns the name of the prod branch (a String) for the given opts. Note: this is a utility fn, not a task fn."
  [opts]
  (get opts :prod-branch "main"))

(defn github-url
  "Returns the base GitHub URL for the given lib (a namespaced symbol), or nil if it can't be determined. Note: this is a utility fn, not a task fn."
  [lib]
  (when lib
    (let [ns (namespace lib)
          nm (name lib)]
      (when (and (not (s/blank? ns))
                 (s/starts-with? ns "com.github.")
                 (not (s/blank? nm)))
        (str "https://github.com/" (s/replace ns "com.github." "") "/" nm)))))


; Task functions

(defn clean
  "Deletes the target directory. opts includes:

  :target -- opt: a string specifying the name of the target directory (defaults to \"target\")"
  [opts]
  (let [target-dir (target-dir opts)]
    (println "ℹ️ Cleaning" (str target-dir "..."))
    (delete-dir target-dir))
  opts)

(defn check
  "Check the code by compiling it (and throwing away the result). No options."
  [opts]
  (println "ℹ️ Checking (compiling) project...")
  ; Note: we do this this way to get around tools.deps lack of support for transitive dependencies that are git coords
  (tc/clojure "-Sdeps"
              (str "{:aliases {:check {:extra-deps {com.github.athos/clj-check " (pr-str ver-clj-check) "} :main-opts [\"-m\" \"clj-check.check\"]}}}")
              "-M:check")
  opts)

(defn- outdated-deps
  "Utility fn to determine outdated dependencies using antq."
  [opts]
  (let [user-opts (:antq opts)
        antq-opts (merge {:directory     ["."]
                          :reporter      "table"}
                         user-opts
                         {:ignore-locals true                                                ; Always ignore local-only deps
                          :skip          (concat ["pom" "leiningen"] (:skip user-opts))})    ; Always skip pom.xml and project.clj files
        deps      (aq/fetch-deps antq-opts)]
    (aq/antq antq-opts deps)))    ; NOTE: DO NOT RETURN opts HERE!  THIS IS NOT A BUILD TASK FN!

(defn antq-outdated
  "Determine outdated dependencies, via antq. opts includes:

  :antq -- opt: a map containing antq-specific configuration options. Sadly these aren't really documented anywhere obvious, but they are passed into this fn: https://github.com/liquidz/antq/blob/main/src/antq/core.clj#L230"
  [opts]
  (println "ℹ️ Checking for outdated dependencies...")
  (let [old-deps (outdated-deps opts)]
    (when (seq old-deps)
      (throw (ex-info "Outdated dependencies found" {:outdated-deps old-deps}))))
  opts)

(defn antq-upgrade
  "Unconditionally upgrade any outdated dependencies, via antq. opts includes:

  :antq -- opt: a map containing antq-specific configuration options. Sadly these aren't really documented anywhere obvious, but they are passed into this fn: https://github.com/liquidz/antq/blob/main/src/antq/core.clj#L230"
  [opts]
  (println "ℹ️ Upgrading outdated dependencies...")
  (let [old-deps (outdated-deps opts)]
    (when (seq old-deps)
      (au/upgrade! old-deps (assoc (:antq opts) :force true :ignore-locals true))))
  opts)

(defn run-tests
  "Runs unit tests (if any). opts includes:

  :test-paths -- opt: a sequence of paths containing test code (defaults to [\"test\"])
  :test-deps  -- opt: a dep map of dependencies to add while testing"
  [opts]
  (let [test-paths (vec (test-dirs opts))
        test-deps  (merge {'io.github.cognitect-labs/test-runner ver-test-runner
                           'ch.qos.logback/logback-classic       ver-logback
                           'org.slf4j/slf4j-api                  ver-slf4j
                           'org.slf4j/jcl-over-slf4j             ver-slf4j
                           'org.slf4j/log4j-over-slf4j           ver-slf4j
                           'org.slf4j/jul-to-slf4j               ver-slf4j}
                          (:test-deps opts))]
    (println "ℹ️ Running unit tests from" (str (s/join ", " test-paths) "..."))
    ; Note: we do this this way to get around tools.deps lack of support for transitive dependencies that are git coords
    (tc/clojure "-Sdeps"
                (str "{:aliases {:test {:extra-paths " (pr-str test-paths) " "
                                       ":extra-deps  " (pr-str test-deps) " "
                                       ":main-opts   [\"-m\" \"cognitect.test-runner\"] "
                                       ":exec-fn     cognitect.test-runner.api/test}}}")
                "-X:test"))
  opts)

(defn pom
  "Generates a comprehensive pom.xml file. See https://github.com/pmonks/tools-pom/ for opts"
  [opts]
  (println "ℹ️ Generating comprehensive pom.xml file...")
  (pom/pom opts)
  opts)

(defn- prep-classes-dir!
  "Prepares the classes directory for subsequent tasks (e.g. jarring, uberjarring, etc.).  Returns nil."
  [opts]
  (let [classes-dir (classes-dir opts)
        src-dirs    (src-dirs)
        prep-opts   (assoc opts :basis (default-basis))]
    (b/copy-dir {:src-dirs src-dirs :target-dir classes-dir})   ; Note: copy-dir should really be called "copy-dirs"...
    (when (:main prep-opts)
      (b/compile-clj (assoc prep-opts :ns-compile [(:main prep-opts)])))
    (pom         prep-opts)
    (b/write-pom prep-opts))   ; Writes a .pom and pom.properties into target/classes/META-INF, based on the ./pom.xml we just generated
  nil)

(defn jar
  "Generates a library JAR for the project. opts includes:

  :main   -- opt: the name of the JAR's main class (defaults to nil)
  :target -- opt: a string specifying the name of the target directory (defaults to \"target\")
  -- opts from the `pom` task --"
  [opts]
  (let [jar-opts (assoc opts :src-pom   (get opts :src-pom (pom-file-name opts))   ; Ensure we tell write-pom to use the pom.xml we generate as a template
                             :class-dir (classes-dir opts)
                             :jar-file  (get opts :jar-file (fq-jar-file-name opts)))]
    (println "ℹ️ Constructing library jar" (str (:jar-file jar-opts) "..."))
    (prep-classes-dir! jar-opts)
    (b/jar             jar-opts))
  opts)

(defn uber
  "Create an uber jar. opts includes:

  -- opts from the `pom` task --
  -- opts from the `tools.build/uber` task --"
  [opts]
  (let [uber-opts (assoc opts :src-pom           (get opts :src-pom (pom-file-name opts)) ; Ensure we tell write-pom to use the pom.xml we generate as a template
                              :class-dir         (classes-dir opts)
                              :uber-file         (get opts :uber-file (fq-uberjar-file-name opts))
                              :basis             (default-basis)
                              :conflict-handlers {"^data_readers.clj[c]?$" :data-readers
                                                  "^META-INF/services/.*" :append
                                                  "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$" :append-dedupe
                                                  "Log4j2Plugins\\.dat" :warn   ; Addition to the default map for Log4J2
                                                  :default :ignore})]
    (println "ℹ️ Constructing uberjar" (str (:uber-file uber-opts) "..."))
    (prep-classes-dir! uber-opts)
    (b/uber uber-opts))
  opts)

(defn uberexec
  "Creates an executable uber jar (note: does not bundle a JRE, and one is still required). opts includes:

  -- opts from the `pom` task --
  -- opts from the `uber` task --"
  [opts]
  (let [uber-file-name     (fq-uberjar-file-name opts)
        uberexec-file-name (s/replace uber-file-name ".jar" "")]
    (uber opts)
    (println "ℹ️ Constructing executable uberjar" (str uberexec-file-name "..."))
    ; Magical cross-platform script that gets prepended to the JAR file
    (spit uberexec-file-name (str ":; java -jar $0 \"$@\" #\r\n"
                                  ":; exit $? #\r\n"
                                  "\r\n"
                                  "@ECHO OFF\r\n"
                                  "SET ARGS=%1\r\n"
                                  "SHIFT\r\n"
                                  ":nextarg\r\n"
                                  "IF [%1] == [] GOTO done\r\n"
                                  "SET ARGS=%ARGS% %1\r\n"
                                  "SHIFT\r\n"
                                  "GOTO nextarg\r\n"
                                  ":done\r\n"
                                  "java -jar %~f0 %ARGS%\r\n"
                                  "EXIT /b %ERRORLEVEL%\r\n"))
    (with-open [in (io/input-stream uber-file-name)]
      (with-open [out (io/output-stream uberexec-file-name :append true)]
        (io/copy in out)))
    (.setExecutable (io/file uberexec-file-name) true false))
  opts)

(defn install
  "Installs a library JAR in the local Maven cache (typically ~/.m2/repository). opts includes:

  -- opts from the `jar` task --
  -- opts from the `tools.build/install` task --"
  [opts]
  (let [install-opts (assoc opts :basis     (default-basis)
                                 :class-dir (classes-dir opts)
                                 :jar-file  (get opts :jar-file (fq-jar-file-name opts)))]
    (jar install-opts)
    (println "ℹ️ Installing library jar" (:jar-file install-opts) "in local Maven cache...")
    (b/install install-opts))
  opts)

(defn nvd
  "Run the NVD vulnerability checker

  :nvd -- opt: a map containing nvd-clojure-specific configuration options. See https://github.com/rm-hull/nvd-clojure#configuration-options"
  [opts]
  (println "ℹ️ Running NVD vulnerability checker...")
  ; Notes: NVD *cannot* be run in a directory containing a deps.edn, as this "pollutes" the classpath of the JVM it's running in; something it is exceptionally sensitive to.
  ; So we create a temporary directory underneath the current project, and run it there. Yes this is ridiculous.
  (let [output-dir         (str (target-dir opts) "/nvd")
        nvd-opts           (merge {:fail-threshold 11                        ; By default tell NVD not to fail under any circumstances
                                   :output-dir     (str "../" output-dir)}   ; Write to the project's actual target directory
                                  (:nvd opts))
        classpath-to-check (s/replace
                             (s/replace (s/trim (:out (tc/clojure-silent "-Spath" "-A:any:aliases")))
                                        #":[^:]*/org/owasp/dependency-check-core/[\d\.]+/dependency-check-core-[\d\.]+.jar:"   ; Remove dependency-check jar, if present
                                        ":")
                             #":[^:]*/nvd-clojure/nvd-clojure/[\d\.]+/nvd-clojure-[\d\.]+\.jar:"                               ; Remove nvd-clojure jar, if present
                             ":")]
    (delete-dir      output-dir)
    (delete-dir      ".nvd")
    (io/make-parents ".nvd/.")
    (spit ".nvd/nvd-options.json"
          (json/write-str {:delete-config? false
                           :nvd            nvd-opts}))
    (let [nvd-result (sh/sh "clojure"
                            "-J-Dclojure.main.report=stderr"
                            "-Srepro"
                            "-Sdeps"
                            "{:deps {nvd-clojure/nvd-clojure {:mvn/version \"RELEASE\"}}}"
                            "-M"
                            "-m"
                            "nvd.task.check"
                            "nvd-options.json"   ; Note: relative to :dir
                            classpath-to-check
                            :dir ".nvd")]
      ; Note: we don't print stderr, as that's where dependency-check's (voluminous) logs go
      (when-not (s/blank? (:out nvd-result))
        (println (:out nvd-result)))
      (when-not (= 0 (:exit nvd-result))
        (throw (ex-info "NVD failed" nvd-result))))

    (delete-dir ".nvd"))
  opts)

(defn kondo
  "Run the clj-kondo linter. No options."
  [opts]
  (println "ℹ️ Running clj-kondo linter...")
  (kd/print! (kd/run! {:lint (src-dirs)}))
  opts)

(defn eastwood
  "Run the eastwood linter. opts includes:

  :eastwood -- opt: a map containing eastwood-specific configuration options (see https://github.com/jonase/eastwood#running-eastwood-in-a-repl)"
  [opts]
  (println "ℹ️ Running eastwood linter...")
  (let [eastwood-opts (merge {:source-paths (src-dirs)}
                             (:eastwood opts))]
    ; Note: we can't do this, as it's running in the wrong classpath (i.e. the build.tool classpath, not the project classpath)
    ;(ew/eastwood eastwood-opts))
    ; So instead we revert to ye olde dynamic invocation...
    (tc/clojure "-Sdeps"
                (str "{:aliases {:eastwood {:extra-deps {jonase/eastwood " (pr-str ver-eastwood) "} "
                                           ":main-opts  [\"-m\" \"eastwood.lint\" " (pr-str eastwood-opts) "]}}}")
                "-M:eastwood"))
  opts)

(defn deploy-info
  "Writes out a deploy-info EDN file, containing at least :hash and :date keys, and possibly also :repo and :tag keys. opts includes:

  :deploy-info-file -- req: the name of the file to write to (e.g. \"./resources/deploy-info.edn\")"
  [opts]
  (if-let [file-name (:deploy-info-file opts)]
    (let [deploy-info (merge {:hash (tc/git-current-commit)
                              :date (java.time.Instant/now)}
                              (when-let [repo (try (tc/git-remote)      (catch clojure.lang.ExceptionInfo _ nil))] {:repo repo})
                              (when-let [tag  (try (tc/git-nearest-tag) (catch clojure.lang.ExceptionInfo _ nil))] {:tag tag}))]
      (println "ℹ️ Writing deployment information to" (str file-name "..."))
      (io/make-parents file-name)
      (with-open [w (io/writer (io/file file-name))]
        (pp/pprint deploy-info w)))
    (throw (ex-info ":deploy-info-file not provided" (into {} opts))))
  opts)

(defn check-release
  "Check that a release can be made from the current directory, using the provided opts. opts includes:

  :lib        -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version    -- opt: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :dev-branch -- opt: the name of the development branch containing the changes to be PRed (defaults to \"dev\")"
  [opts]
  ; Check for the command line tools we need
  (tc/ensure-command "hub")

  (println "ℹ️ Checking whether a release can be made from the current directory...")

  ; Check that opts map is properly populated
  (when-not (:version opts) (throw (ex-info ":version not provided" (into {} opts))))
  (when-not (:lib opts)     (throw (ex-info ":lib not provided" (into {} opts))))

  ; Check status of working directory
  (let [dev-branch     (dev-branch opts)
        current-branch (tc/git-current-branch)]
    (when-not (= dev-branch current-branch)
      (throw (ex-info (str "Must be on branch '" dev-branch "' to prepare a release, but current branch is '" current-branch "'.") {}))))

  (let [git-status (tc/git :status "--short")]
    (when (not (s/blank? git-status))
      (throw (ex-info (str "Working directory is not clean:\n " git-status "\nPlease commit, revert, or stash these changes before preparing a release.") {}))))

  opts)

(defn release
  "Release a new version of the code via a PR to main. opts includes:

  :lib         -- req: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version     -- req: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :dev-branch  -- opt: the name of the development branch containing the changes to be PRed (defaults to \"dev\")
  :prod-branch -- opt: the name of the production branch where the PR is to be sent (defaults to \"main\")
  :pr-desc     -- opt: a format string used for the PR description with two %s values passed in (%1$s = lib, %2$s = version) (defaults to \"%1$s release v%2$s. See commit log for details of what's included in this release.\")
  -- opts from the `deploy-info` task, if you wish to generate deploy-info --"
  [opts]
  (when-not (:version opts) (throw (ex-info ":version not provided" (into {} opts))))
  (when-not (:lib opts)     (throw (ex-info ":lib not provided" (into {} opts))))

  (let [lib              (:lib opts)
        version          (:version opts)
        dev-branch       (dev-branch opts)
        prod-branch      (prod-branch opts)
        deploy-info-file (:deploy-info-file opts)]

    (println (str "ℹ️ Preparing to release " lib " " version "..."))

    ; Ensure working directory is up to date with prod branch
    (println "ℹ️ Updating working directory...")
    (tc/git :fetch "origin" (str prod-branch ":" prod-branch))
    (tc/git :merge prod-branch)
    (tc/git :pull)

    (println "ℹ️ Checking that a release can be made...")
    (check-release opts)

    (println (str "ℹ️ All good; press any key to continue or Ctrl+C to abort..."))
    (flush)
    (read-line)

    (println "ℹ️ Tagging release as" (str version "..."))
    (tc/git :tag "-f" "-a" version "-m" (str ":gem: Release " version))

    (when deploy-info-file
      (println "ℹ️ Updating" (str deploy-info-file "..."))
      (deploy-info opts)
      (tc/git :add deploy-info-file)  ; Add the file just in case it's never existed before - this is no-op if it's already in the index
      (tc/git :commit "-m" (str ":gem: Release " version) deploy-info-file))

    (println "ℹ️ Pushing tag" version (str "(" (tc/git-tag-commit version) ")..."))
    (tc/git :push)
    (tc/git :push "origin" "-f" "--tags")

    (println "ℹ️ Creating 'release' pull request from" dev-branch " to " prod-branch "...")
    (let [pr-desc-fmt (get opts :pr-desc "%1$s release %2$s. See commit log for details of what's included in this release.")]
      (tc/exec ["hub" "pull-request" "--browse" "-f"
                "-m" (str "Release " version)
                "-m" (format pr-desc-fmt (str lib) (str version))
                "-h" dev-branch "-b" prod-branch]))

    (println "ℹ️ After the PR has been merged, it is highly recommended to:\n"
             "  1. git fetch origin" (str prod-branch ":" prod-branch) "\n"
             "  2. git merge" prod-branch "\n"
             "  3. git pull\n"
             "  4. git push")

    (println "⏹ Done."))
  opts)

(defn deploy
  "Builds and deploys the library's artifacts (pom.xml, JAR) to Clojars (or elsewhere), from the 'production' branch. opts includes:

  :prod-branch -- opt: the name of the production branch where the deployment is to be initiated from (defaults to \"main\")
  -- opts from the `pom task`, though note that :write-pom and :validate-pom are forced to true --
  -- opts from the `jar` task --
  -- opts from `deps-deploy/deploy` (see https://github.com/slipset/deps-deploy/blob/master/src/deps_deploy/deps_deploy.clj#L196-L214) --"
  [opts]
  (let [current-branch (tc/git-current-branch)
        main-branch    (prod-branch opts)]
    (if (= current-branch main-branch)
      (let [version     (tc/git-nearest-tag)
            deploy-opts (assoc opts :version      version
                                    :write-pom    true
                                    :validate-pom true)
            deploy-opts (assoc deploy-opts :artifact       (fq-jar-file-name deploy-opts)
                                           :installer      :remote
                                           :pom-file       (pom-file-name deploy-opts)
                                           :sign-releases? (get deploy-opts :sign-releases? false))]
        (println "ℹ️ Deploying" (:lib deploy-opts) "version" (:version deploy-opts) "to Clojars...")
        (pom       deploy-opts)
        (jar       deploy-opts)
        (dd/deploy deploy-opts))
      (throw (ex-info (str "deploy task must be run from '" main-branch "' branch (current branch is '" current-branch "').") (into {} opts)))))
  opts)

(defn codox
  "Generates codox documentation. opts includes:

  :lib   -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :codox -- opt: a codox options map (see https://github.com/weavejester/codox#project-options). Note that PBR will auto-include the :source-uri option for com.github.* projects"
  [opts]
  (let [paths       (src-dirs)
        github-url  (github-url (:lib opts))
        prod-branch (prod-branch opts)]
    (println "ℹ️ Generating codox API documentation...")
    ; Note: we can't do this, as it's running in the wrong classpath (i.e. the build.tool classpath, not the project classpath)
    ;(cx/generate-docs (merge {:source-paths paths}
    ;                         (when github-url {:source-uri (str github-url "/blob/" prod-branch "/{filepath}#L{line}")})
    ;                         (:codox opts))))
    ; So instead we revert to ye olde dynamic invocation...
    (tc/clojure "-Sdeps"
                (str "{:aliases {:codox {:extra-deps {codox/codox " (pr-str ver-codox) "} "
                                        ":extra-paths " (pr-str paths) " "
                                        ":exec-fn codox.main/generate-docs "
                                        ":exec-args " (pr-str (merge {:source-paths paths}
                                                                     (when github-url {:source-uri (str github-url "/blob/" prod-branch "/{filepath}#L{line}")})
                                                                     (:codox opts))) "}}}")
                "-X:codox"))
  opts)
