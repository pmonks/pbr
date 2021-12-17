| | | |
|---:|:---:|:---:|
| [**main**](https://github.com/pmonks/pbr/tree/main) | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |
| [**dev**](https://github.com/pmonks/pbr/tree/dev)  | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/pbr)](https://clojars.org/com.github.pmonks/pbr/) [![Open Issues](https://img.shields.io/github/issues/pmonks/pbr.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/pbr.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little [tools.build](https://github.com/clojure/tools.build) task library that supports the author's personal GitHub workflow.  It is not expected to be especially relevant for other developers' workflows.

If you're looking for the convenience functions, and the pom.xml and license tasks that used to be part of PBR, as of PBR v2.0 they've been refactored into their own micro-libraries to better facilitate reuse and contribution:

* [com.github.pmonks/tools-convenience](https://github.com/pmonks/tools-convenience/) - tools.build convenience fns
* [com.github.pmonks/tools-pom](https://github.com/pmonks/tools-pom/) - pom.xml-related build tasks
* [com.github.pmonks/tools-licenses](https://github.com/pmonks/tools-licenses/) - license-related build tasks

## Features

### Build script

PBR provides a default `./build.clj` script that provides all of the tasks I typically need in my build scripts.  It allows customisation by loading a `./pbr.clj` file, which must contain a `set-opts` fn where various project specific opts can be set.  You can look at [PBR's own `pbr.clj` file](https://github.com/pmonks/pbr/blob/main/pbr.clj) for an idea of what this must contain.

This script also assumes your `deps.edn` includes _at least_ the following aliases:

```edn
{:deps
   {
    ; Your project's dependencies
   }
 :aliases
   {; ---- TOOL ALIASES ----

    ; clj -T:build <taskname>
    :build
      {:deps       {io.github.seancorfield/build-clj {:git/tag "v0.6.3" :git/sha "9b8e09b"}
                    com.github.pmonks/pbr            {:mvn/version "LATEST_CLOJARS_VERSION"}}
       :ns-default build}


    ; ---- MAIN FUNCTION ALIASES ----

    ; clj -M:check
    :check
      {:extra-deps {com.github.athos/clj-check {:git/sha "518d5a1cbfcd7c952f548e6dbfcb9a4a5faf9062"}}
       :main-opts  ["-m" "clj-check.check"]}

    ; clj -M:test or clj -X:test
    :test {:extra-paths ["test"]
           :extra-deps  {io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}
           :main-opts   ["-m" "cognitect.test-runner"]
           :exec-fn     cognitect.test-runner.api/test}

    ; clj -M:kondo
    :kondo
      {:extra-deps {clj-kondo/clj-kondo {:mvn/version "2021.12.16"}}
       :main-opts  ["-m" "clj-kondo.main" "--lint" "src"]}

    ; clj -M:eastwood
    :eastwood
      {:extra-deps {jonase/eastwood {:mvn/version "1.0.0"}}
       :main-opts  ["-m" "eastwood.lint" "{:source-paths,[\"src\"]}"]}

    ; clj -M:outdated
    :outdated
      {:extra-deps {com.github.liquidz/antq {:mvn/version "1.3.0"}}
       :main-opts  ["-m" "antq.core" "--skip=pom"]}

    ; clj -X:codox
    :codox
      {:extra-deps {codox/codox {:mvn/version "0.10.8"}}
       :exec-fn    codox.main/generate-docs
       :exec-args  {:source-paths ["src"]
                    :source-uri   "https://github.com/pmonks/pbr/blob/main/{filepath}#L{line}"}}
   }}
```

If you fail to include some of these aliases, PBR _will_ break.  Note also that you must express an explicit dependency on `io.github.seancorfield/build-clj` in your build alias, as that project [doesn't publish artifacts to Clojars](https://github.com/seancorfield/build-clj/issues/11), and transitive git coordinate dependencies are not supported by tools.deps.

### Additional build tasks

PBR also provides some additional build task fns:

1. `deploy-info` - generate an EDN file containing deployment info for your code (build date/time and git commit SHA & (optionally) tag).
2. `release` - perform a release by tagging the current code, optionally updating the deploy-info.edn file, and creating a PR from a development branch to a production branch.
3. `deploy` - perform a deployment by constructing a comprehensive pom.xml file, building a JAR, and deploying them to clojars.

#### API Documentation

[API documentation is available here](https://pmonks.github.io/pbr/).

#### Worked example

For a worked example of using the library's build tasks, see [the default build script](https://github.com/pmonks/pbr/blob/main/src/build.clj).

## FAQ

[//]: # (Comment: Every Question in this list has two spaces at the end THAT MUST NOT BE REMOVED!!)

**Q.** Why "PBR"?  
**A.** Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

**Q.** Does PBR use itself for its own build tasks?  
**A.** Why yes it does!  You can see how it sneakily references itself [here](https://github.com/pmonks/pbr/blob/main/deps.edn#L33).

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/pbr/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/pbr/issues)

[Code of Conduct](https://github.com/pmonks/pbr/blob/main/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

The repository has two permanent branches: `main` and `dev`.  **All development must occur either in branch `dev`, or (preferably) in feature branches off of `dev`.**  All PRs must also be submitted against `dev`; the `main` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `main` will be rejected.

This model allows otherwise unrelated changes to be batched up in the `dev` branch, integration tested there, and then released en masse to the `main` branch, which will trigger automated generation and deployment of the release (Codox docs to github.io, JARs to Clojars, etc.).

## License

Copyright © 2021 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
