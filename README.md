| | | |
|---:|:---:|:---:|
| [**main**](https://github.com/pmonks/pbr/tree/main) | [![CI](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3ACI+branch%3Amain) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies+branch%3Amain) |
| [**dev**](https://github.com/pmonks/pbr/tree/dev)  | [![CI](https://github.com/pmonks/pbr/workflows/CI/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/pbr)](https://clojars.org/com.github.pmonks/pbr/) [![Open Issues](https://img.shields.io/github/issues/pmonks/pbr.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/pbr.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little [tools.build](https://github.com/clojure/tools.build) task library and turnkey build script that supports the author's personal GitHub workflow.  It is not expected to be especially relevant for other developers' workflows, except perhaps as a model for how tools.build can be (somewhat) tamed when working with multiple independent projects that share common build tasks.

## Why?

Because "vanilla" tools.build build scripts impose a _lot_ of unnecessary repetition when one is working on lots of separate projects that need to have the same set of build tasks. A more detailed explanation of the problem is [here](https://ask.clojure.org/index.php/11168/tools-build-are-standard-build-tasks-under-consideration).

### Why not [build-clj](https://github.com/seancorfield/build-clj)?

While build-clj provides standard _implementations_ of common build tasks, it doesn't say anything about standard _interfaces_ to those build tasks (i.e. via the `clj` / `clojure` command line).  This project is focused on providing the latter, though it also happens to provide some of the former as something of a side effect.

The productivity benefits to this approach are two-fold:
1. For project authors: new pbr-based projects require almost no build script development effort, and those scripts have virtually no code duplication
2. For project contributors/consumers: once familiar with the build tasks in one pbr-based project, they are automatically familiar with the build tasks for _all_ pbr-based projects

Furthermore, by providing the exact same build tasks across all pbr-based projects, there is an opportunity for downstream automation to reliably and consistently initiate pbr-based build tasks (this is why platforms such as [jitpack.io only support Leiningen and not tools.build/build-clj](https://docs.jitpack.io/building/)).  Obviously it is implausible in the extreme that such platforms will ever adopt pbr itself, but at least this project can serve as a proof-of-concept of how a design flaw in current versions of tools.build could be addressed.

## Features

### Task library

PBR includes a library of tools.build tasks that are [documented here](https://pmonks.github.io/pbr/).  These may be used independently of the turnkey build script described next.

### Turnkey build script

PBR also provides a turnkey `build.clj` script that provides all of the tasks I typically need in my build scripts.  It allows customisation via a per-project `pbr.clj` file, which must contain a `set-opts` fn where various project specific options can be set.  You can look at [PBR's own `pbr.clj` file](https://github.com/pmonks/pbr/blob/main/pbr.clj) for an idea of what this looks like.

Tasks can be listed by running `clojure -A:deps -T:build help/doc`, and are:

* `check` - Check the code by AOT compiling it (and throwing away the result).
* `check-asf-policy` - Checks this project's dependencies' licenses against the ASF's 3rd party license policy (https://www.apache.org/legal/resolved.html).
* `check-release` - Check that a release can be done from the current directory.
* `ci` - Run the CI pipeline.
* `clean` - Clean up the project.
* `deploy` - Deploys the library JAR to Clojars.
* `docs` - Generates documentation (using codox).
* `eastwood` - Run the eastwood linter.
* `install` - Install the library locally e.g. so it can be tested by downstream dependencies
* `jar` - Generates a library JAR for the project.
* `kondo` - Run the clj-kondo linter.
* `licenses` - Attempts to list all licenses for the transitive set of dependencies of the project, as SPDX license identifiers.
* `lint` - Run all linters.
* `nvd` - Run an NVD vulnerability check.
* `outdated` - Check for outdated dependencies (using antq).
* `pom` - Generates a comprehensive pom.xml for the project.
* `release` - Release a new version of the library.
* `test` - Run the tests.
* `uber` - Create an uber jar.
* `uberexec` - Creates an executable uber jar. NOTE: does not bundle a JRE, though one is still required.
* `upgrade` - Upgrade any outdated dependencies (using antq). NOTE: does not prompt for confirmation!

#### deps.edn required by turnkey build script

To use the turnkey build script, include the following alias in your project's `deps.edn`:

```edn
{:deps { ; Your project's dependencies
       }
 :aliases {:build {:deps        {io.github.clojure/tools.build {:git/tag "v0.9.3" :git/sha "e537cd1"}  ; Or whatever the latest version is - this will go away if/when tools.build gets a proper release
                                 com.github.pmonks/pbr         {:mvn/version "RELEASE"}}
                   :ns-default  pbr.build}}}
```

#### Preparing to build with the turnkey build script

To prepare your project to use the turnkey build script, you must run the following command first:

```shell
$ clj -A:build -P
```

## FAQ

[//]: # (Comment: Every Question in this list has two spaces at the end THAT MUST NOT BE REMOVED!!)

**Q.** Why "PBR"?  
**A.** Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

**Q.** Does PBR use itself for build tasks?  
**A.** Yes it does!  [You can see this sneaky self-reference here](https://github.com/pmonks/pbr/blob/main/deps.edn#L42).

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/pbr/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/pbr/issues)

[Code of Conduct](https://github.com/pmonks/pbr/blob/main/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

This project uses the [git-flow branching strategy](https://nvie.com/posts/a-successful-git-branching-model/), with the caveat that the permanent branches are called `main` and `dev`, and any changes to the `main` branch are considered a release and auto-deployed (JARs to Clojars, API docs to GitHub Pages, etc.).

For this reason, **all development must occur either in branch `dev`, or (preferably) in temporary branches off of `dev`.**  All PRs from forked repos must also be submitted against `dev`; the `main` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `main` will be rejected.

### Build Tasks

`pbr` uses [`tools.build`](https://clojure.org/guides/tools_build). You can get a list of available tasks by running:

```
clojure -A:deps -T:build help/doc
```

Of particular interest are:

* `clojure -T:build test` - run the unit tests
* `clojure -T:build lint` - run the linters (clj-kondo and eastwood)
* `clojure -T:build ci` - run the full CI suite (check for outdated dependencies, run the unit tests, run the linters)
* `clojure -T:build install` - build the JAR and install it locally (e.g. so you can test it with downstream code)

Please note that the `deploy` task is restricted to the core development team (and will not function if you run it yourself).

## License

Copyright © 2021 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
