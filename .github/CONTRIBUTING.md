# Contributing to Gradle
Thank you for considering a contribution to Gradle! This guide explains how to:

* maximize the chance of your changes being accepted
* work on the Gradle code base
* get help if you encounter trouble

## Get in touch

Before starting to work on a feature or a fix, please open an issue to discuss the use case or bug with us. This can save both you and us a lot of time.
For any non-trivial change, we'll ask you to create a short design document explaining:

* Why is this change done? What's the use case?
* What will the API look like? (For new features)
* What test cases should it have? What could go wrong?
* How will it roughly be implemented? (We'll happily provide code pointers to save you time)

This can be done directly inside the GitHub issue or (for large changes) you can share a Google Doc with us.

## Sign the CLA

Before we can accept any code contributions, you must electronically sign a [Gradle CLA](http://gradle.org/cla).

## Follow the Code of Conduct

In order to foster a more inclusive community, Gradle has adopted the [Contributor Covenant](https://www.contributor-covenant.org/version/1/4/code-of-conduct/).

Contributors must follow the Code of Conduct outlined at [https://gradle.org/conduct/](https://gradle.org/conduct/).

## Making Changes

### Development Setup

In order to make changes to Gradle, you'll need:

* A text editor or IDE. We use and recommend [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK) version 1.7 or higher
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join)

Gradle uses pull requests for contributions. Fork [gradle/gradle](https://github.com/gradle/gradle) and clone your fork. Configure your Git username and email with

    git config user.name 'First Last'
    git config user.email user@example.com

You can generate the IntelliJ project by running

    ./gradlew idea

You can generate the Eclipse projects by running

    ./gradlew eclipse

### Code Change Guidelines

All code contributions should contain the following:

* Unit Tests (using [Spock](http://spockframework.org/spock/docs/1.1-rc-2/index.html)) for any logic introduced
* Integration Test coverage of the bug/feature at the level of build execution. Please annotate tests guarding against a specific GitHub issue `@Issue("gradle/gradle#123")`.
* Documentation in the User Guide and DSL Reference (under `subprojects/docs/src/docs`). You can generate docs by running `./gradlew :docs:docs`.

Your code needs to run on all supported Java versions and operating systems. The [Gradle CI](http://builds.gradle.org/) will verify this, but here are some pointers that will avoid surprises:

* Avoid using features introduced in Java 1.7 or later. Several parts of Gradle still need to run on Java 6.
* Normalise file paths in tests. The `org.gradle.util.TextUtil` class has some useful functions for this purpose.

### Development Workflow

After making changes, you can test them in 2 ways:

To run tests, execute `./gradlew :<subproject>:check` where `<subproject>` is the name of the sub-project that has changed. For example: `./gradlew :launcher:check`.

To try out a change in behavior manually, install Gradle locally and use it
    ./gradlew install -Pgradle_installPath=/any/path
    /any/path/bin/gradle taskName

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach a debugger at `localhost:5005` by default.

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, please follow these guidelines when writing commit messages:

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`Issue: #123`) in the commit message on a separate line
* Please check that your email address matches that on your [CLA](http://gradle.org/cla)

### Submitting Your Change

After you submit your pull request, a Gradle core developer will review it. It is normal that this takes several iterations, so don't get discouraged by change requests. They ensure the high quality that we all enjoy.

## Getting Help

If you run into any trouble, please reach out to us on the issue you are working on.

## Our Thanks

We deeply appreciate your effort toward improving Gradle. For any contribution, large or small, you will be immortalized in the release notes for the version you've contributed to.

If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)?
