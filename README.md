| | | |
|---:|:---:|:---:|
| [**main**](https://github.com/pmonks/pbr/tree/main) | [![CI](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3ACI+branch%3Amain) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies+branch%3Amain) |
| [**dev**](https://github.com/pmonks/pbr/tree/dev)  | [![CI](https://github.com/pmonks/pbr/workflows/CI/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3ACI+branch%3Adev) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies+branch%3Adev) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/pbr)](https://clojars.org/com.github.pmonks/pbr/) [![Open Issues](https://img.shields.io/github/issues/pmonks/pbr.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/pbr.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little [tools.build](https://github.com/clojure/tools.build) task library that supports the author's personal GitHub workflow.  It is not expected to be especially relevant for other developers' workflows.

## Features

### Task library

PBR includes a library of tools.build tasks that are [documented here](https://pmonks.github.io/pbr/).  These may be used independently of the turnkey build script described next.

### Turnkey build script

PBR also provides a turnkey `build.clj` script that provides all of the tasks I typically need in my build scripts.  It allows customisation via a per-project `./pbr.clj` file, which must contain a `set-opts` fn where various project specific options can be set.  You can look at [PBR's own `pbr.clj` file](https://github.com/pmonks/pbr/blob/main/pbr.clj) for an idea of what this looks like.

Tasks can be listed by running `clojure -A:deps -T:build help/doc`, and are:

* `check` -  Check the code by compiling it.
* `check-asf-policy` - Checks this project's dependencies' licenses against the ASF's 3rd party license policy (https://www.apache.org/legal/resolved.html).
* `check-release` - Check that a release can be done from the current directory.
* `ci` - Run the CI pipeline.
* `clean` - Clean up the project.
* `deploy` - Deploys the library JAR to Clojars.
* `docs` - Generates codox documentation
* `eastwood` - Run the eastwood linter.
* `install` - Install the library locally e.g. so it can be tested by downstream dependencies
* `jar` - Generates a library JAR for the project.
* `kondo` - Run the clj-kondo linter.
* `licenses` - Attempts to list all licenses for the transitive set of dependencies of the project, as SPDX license identifiers.
* `lint` - Run all linters.
* `outdated` - Check for outdated dependencies.
* `release` - Release a new version of the library.
* `test` - Run the tests.
* `uber` - Create an uber jar.

#### deps.edn required by build script

The turnkey build script also assumes your `deps.edn` includes the following:

```edn
{:deps
   { ; Your project's dependencies
   }
 :aliases
   {:build
      {:deps       {io.github.seancorfield/build-clj {:git/tag "v0.6.7" :git/sha "22c2d09"}
                    com.github.pmonks/pbr            {:mvn/version "RELEASE"}}
       :ns-default pbr.build}}}
```

Note that despite not using it directly, you must express an explicit dependency on `io.github.seancorfield/build-clj` in your build alias, as that project [doesn't publish artifacts to Clojars](https://github.com/seancorfield/build-clj/issues/11) and transitive git coordinate dependencies are not supported by tools.deps.

#### Preparing to build with the turnkey script

To prepare your project to use the turnkey build script, you must run the following command first:

```shell
$ clj -A:build -P
```

## FAQ

[//]: # (Comment: Every Question in this list has two spaces at the end THAT MUST NOT BE REMOVED!!)

**Q.** Why "PBR"?  
**A.** Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

**Q.** Does PBR use itself for build tasks?  
**A.** Yes it does!  [You can see how this sneaky self-reference here](https://github.com/pmonks/pbr/blob/main/deps.edn#L35).

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
