# Contributing to Gradle

Thank you for contributing to Gradle! This guide explains how to:

* maximize the chance of your changes being accepted
* work on the Gradle code base
* get help if you encounter trouble

## Before you start

Before starting to work on a feature or a bug fix, please open an issue to discuss the use case or bug with us. This can save everyone a lot of time and frustration.

For any non-trivial change, we need to be able to answer these questions:

* Why is this change done? What's the use case?
* For user facing features, what will the API look like?
* What test cases should it have? What could go wrong?
* How will it roughly be implemented? We'll happily provide code pointers to save you time.

We may ask you to answer these questions directly in the GitHub issue or (for large changes) in a shared Google Doc.

If you are looking for good first issues, take a look at the list of [_🌱 onboarding_ issues](https://github.com/gradle/gradle/issues?q=is%3Aopen+is%3Aissue+label%3A%22%F0%9F%8C%B1+onboarding%22+no%3Aassignee) that should be actionable and ready for a contribution.

### Security vulnerabilities

Do not report security vulnerabilities to the public issue tracker. Follow our [Security Vulnerability Disclosure Policy](https://github.com/gradle/gradle/security/policy).

### Follow the Code of Conduct

Contributors must follow the Code of Conduct outlined at [https://gradle.org/conduct/](https://gradle.org/conduct/).

### Additional help

If you run into any trouble, please reach out to us on the issue you are working on.

## Setting up your development environment

In order to make changes to Gradle, you'll need:

* A [Java Development Kit](http://jdk.java.net/) (JDK) **version 11**. Fixed version is required to use [remote cache](#remote-build-cache). 
* A text editor or IDE. We use and recommend [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).  IntelliJ Ultimate will also work. You'll need IntelliJ 2021.2.2 or newer.
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join).

Gradle uses pull requests for contributions. Fork [gradle/gradle](https://github.com/gradle/gradle) and clone your fork. Configure your Git username and email with:

    git config user.name 'First Last'
    git config user.email user@example.com

#### Import Gradle into IntelliJ

To import Gradle into IntelliJ:
- Open the `build.gradle.kts` file with IntelliJ and choose "Open as Project"
- Make sure "Create separate module per source set" is selected
- Make sure  "Use default gradle wrapper" is selected
- Select a Java 11 VM as "Gradle JVM"
- In the "File already exists" dialogue, choose "Yes" to overwrite
- In the "Open Project" dialogue, choose "Delete Existing Project and Import"
- Revert the Git changes to files in the `.idea` folder

NOTE: Due to the project size, the very first import can take a while and IntelliJ might become unresponsive for several seconds during this period.

IntelliJ automatically hides stacktrace elements from the `org.gradle` package, which makes running/debugging tests more difficult. You can disable this behavior by changing IntelliJ Preferences under Editor -> General -> Console. In the "Fold lines that contain" section, remove the `org.gradle` entry.

If you did not have a Java 11 SDK installed before importing the project into IntelliJ and after adding a Java 11 SDK your IntelliJ still uses the wrong SDK version, you might need to invalidate IntelliJ's caches before reloading the project.

## Making your change

### Code change guidelines

All code contributions should contain the following:

* Create unit tests using [Spock](https://spockframework.org/spock/docs/2.0/index.html) for new classes or methods that you introduce.
* Create integration tests that exercise a Gradle build for the bug/feature. 
* Annotate tests that correspond to a bug on GitHub (`@Issue("https://github.com/gradle/gradle/issues/2622")`).
* Add documentation to the User Manual and DSL Reference (under [subprojects/docs/src/docs](subprojects/docs/src/docs/)). You can generate docs by running `./gradlew :docs:docs`.
* For new features, the feature should be mentioned in the [Release Notes](subprojects/docs/src/docs/release/notes.md).

Your code needs to run on [all versions of Java that Gradle supports](subprojects/docs/src/docs/userguide/compatibility.adoc) and across all supported operating systems (macOS, Windows, Linux). The [Gradle CI system](http://builds.gradle.org/) will verify this, but here are some pointers that will avoid surprises:

* Be careful when using features introduced in Java 1.7 or later. Some parts of Gradle still need to run on Java 6.
* Normalize file paths in tests. The `org.gradle.util.internal.TextUtil` class has some useful functions for this purpose.

### Creating commits and writing commit messages

The commit messages that accompany your code changes are an important piece of documentation. Please follow these guidelines when creating commits:

* [Write good commit messages.](https://cbea.ms/git-commit/#seven-rules)
* [Sign off your commits](https://git-scm.com/docs/git-commit#Documentation/git-commit.txt---signoff) to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/). We can only accept PRs that have all commits signed off.
* Keep commits discrete. Avoid including multiple unrelated changes in a single commit.
* Keep commits self-contained. Avoid spreading a single change across multiple commits. A single commit should make sense in isolation.

### Testing changes

After making changes, you can test your code in 2 ways:

1. Run tests.
- Run `./gradlew :<subproject>:quickTest` where `<subproject>` is the name of the subproject you've changed. 
- For example: `./gradlew :launcher:quickTest`.
2. Install Gradle locally and try out a change in behavior manually. 
- Install: `./gradlew install -Pgradle_installPath=/any/path`
- Use: `/any/path/bin/gradle taskName`.

It's also a good idea to run `./gradlew sanityCheck` before submitting your change because this will help catch code style issues.

### Submitting Your Change

After you submit your pull request, a Gradle developer will review it. It is normal for this to take several iterations, so don't get discouraged by change requests. They ensure the high quality that we all enjoy.

If you need to check on [CI](http://builds.gradle.org/) status as an external contributor, you can click "Log in as guest".

## Useful tips

### How Gradle Works

We have [a series of blog](https://blog.gradle.org/how-gradle-works-1) that explains how Gradle works.
This may help you better understand and contribute to Gradle.

### Debugging Gradle

You can debug Gradle by adding `-Dorg.gradle.debug=true` to the command-line. Gradle will wait for you to attach a debugger at `localhost:5005` by default.

If you made changes to build logic in the `build-logic` included build, you can run its tests by executing `./gradlew :build-logic:check`.

### Fixing DCO failures/Signing Off Commits After Submitting a Pull Request

You must agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/) by signing off your commits. We automatically verify that all commit messages contain a `Signed-off-by:` line with your email address. We can only accept PRs that have all commits signed off.

If you didn't sign off your commits before creating the pull request, you can fix that after the fact.

To sign off a single commit:

`git commit --amend --signoff`

To sign off one or multiple commits:

`git rebase --signoff origin/master`

Then force push your branch:

`git push --force origin test-branch`

### Java Toolchain

The Gradle build uses [Java Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) support to compile and execute tests across multiple versions of Java.

Available JDKs on your machine are automatically detected and wired for the various compile and test tasks.
Some tests require multiple JDKs to be installed on your computer, be aware of this if you make changes related to anything toolchains related.

If you want to explicitly run tests with a different Java version, you need to specify `-PtestJavaVersion=#` with the major version of the JDK you want the tests to run with (e.g. `-PtestJavaVersion=14`).

### Configuration cache enabled by default

The build of Gradle enables the configuration cache by default as a dogfooding experiment.

Most tasks that are used to build Gradle support the configuration cache, but some don't. For example, building the documentation currently requires you to disable the configuration cache.

To disable the configuration cache, run the build with `--no-configuration-cache`.

Tasks known to have problems are listed in the build logic. You can find this list at:

    build-logic/root-build/src/main/kotlin/gradlebuild.internal.cc-experiment.gradle.kts

If you discover a task that doesn't work with the configuration but it not in this list, please add it.

For more information on the configuration cache, see the [user manual](https://docs.gradle.org/current/userguide/configuration_cache.html).

### Remote build cache

Gradle, Inc runs a set of remote build cache nodes to speed up local builds when developing Gradle. By default, the build is [configured](https://github.com/gradle/gradle-org-conventions-plugin#what-it-does) to use the build cache node in the EU region.

The build cache has anonymous read access, so you don't need to authenticate in order to use it. You can use a different build cache node by specifying `-DcacheNode=us` for a build cache node in the US or `-DcacheNode=au` for a build cache node in Australia.

If you are not getting cache hits from the build cache, you may be using the wrong version of Java. A fixed version (Java 11) is required to get remote cache hits.

### Building a distribution from source

To create a Gradle distribution from the source tree you can run either of the following:

    ./gradlew :distributions-full:binDistributionZip

This will create a minimal distribution at `subprojects/distributions-full/build/distributions/gradle-<version>-bin.zip`, just what's needed to run Gradle (i.e. no sources nor docs).

You can then use it as a Gradle Wrapper local distribution in a Gradle based project by using a `file:/` URL pointing to the built distribution:

    ./gradlew wrapper --gradle-distribution-url=file:/path/to/gradle-<version>-bin.zip

To create a full distribution (includes sources and docs):

    ./gradlew :distributions-full:allDistributionZip

The full distribution will be created at `subprojects/distributions-full/build/distributions/gradle-<version>-all.zip`. You can then use it as a Gradle Wrapper local distribution:

    ./gradlew wrapper --gradle-distribution-url=file:/path/to/gradle-<version>-all.zip

## Our thanks

We deeply appreciate your effort toward improving Gradle. For any contribution, large or small, you will be immortalized in the release notes for the version you've contributed to.

If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)?
