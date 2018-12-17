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

### Reporting Security Issues

Please do not report security issues to the public issue tracker.
Please send security issues to [security@gradle.com](mailto:security@gradle.com).

## Accept Developer Certificate of Origin

In order for your contributions to be accepted, you must [sign off](https://git-scm.com/docs/git-commit#git-commit---signoff) your Git commits to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

## Follow the Code of Conduct

In order to foster a more inclusive community, Gradle has adopted the [Contributor Covenant](https://www.contributor-covenant.org/version/1/4/code-of-conduct/).

Contributors must follow the Code of Conduct outlined at [https://gradle.org/conduct/](https://gradle.org/conduct/).

## Making Changes

### Development Setup

In order to make changes to Gradle, you'll need:

* A text editor or IDE. We use and recommend [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://jdk.java.net/) (JDK) version 9 or 10.
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join).

Gradle uses pull requests for contributions. Fork [gradle/gradle](https://github.com/gradle/gradle) and clone your fork. Configure your Git username and email with

    git config user.name 'First Last'
    git config user.email user@example.com

### IntelliJ

You can generate the IntelliJ projects by importing it using the import wizard. You must use IntelliJ 2018.3 or newer.


Alternatively, you may also generated IDEA metadata files with:

    ./gradlew idea

then open the generated `gradle.ipr` file.  This is unsupported and will be removed at some point.

### Eclipse

You can generate the Eclipse projects by running

    ./gradlew eclipse

Then you can import the generated projects into Eclipse
  
1. Install Eclipse 4.5 (Mars) at least
2. Install the Groovy Eclipse plugin from http://dist.springsource.org/snapshot/GRECLIPSE/e4.5/
3. Make sure you have a Java 8 compatible JDK configured in your workspace
4. In `Window->Preferences->Groovy->Compiler`, check `Enable Script folder support` and add `**/*.gradle`
5. Import all projects using the "Import Existing Projects into Workspace" wizard


 
### Code Change Guidelines

All code contributions should contain the following:

* Unit Tests (using [Spock](http://spockframework.org/spock/docs/1.1-rc-2/index.html)) for any logic introduced
* Integration Test coverage of the bug/feature at the level of build execution. Please annotate tests guarding against a specific GitHub issue `@Issue("gradle/gradle#123")`.
* Documentation in the User Manual and DSL Reference (under `subprojects/docs/src/docs`). You can generate docs by running `./gradlew :docs:docs`.

Your code needs to run on all supported Java versions and operating systems. The [Gradle CI](http://builds.gradle.org/) will verify this, but here are some pointers that will avoid surprises:

* Avoid using features introduced in Java 1.7 or later. Several parts of Gradle still need to run on Java 6.
* Normalise file paths in tests. The `org.gradle.util.TextUtil` class has some useful functions for this purpose.

### Development Workflow

After making changes, you can test them in 2 ways:

To run tests, execute `./gradlew :<subproject>:check` where `<subproject>` is the name of the sub-project that has changed. For example: `./gradlew :launcher:check`.

To try out a change in behavior manually, install Gradle locally and use it.
Install: `./gradlew install -Pgradle_installPath=/any/path`. Use: `/any/path/bin/gradle taskName`.

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach a debugger at `localhost:5005` by default.

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, please follow these guidelines when writing commit messages:

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`Issue: #123`) in the commit message on a separate line
* [Sign off](https://git-scm.com/docs/git-commit#git-commit---signoff) your commits to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

### Submitting Your Change

After you submit your pull request, a Gradle core developer will review it. It is normal that this takes several iterations, so don't get discouraged by change requests. They ensure the high quality that we all enjoy.

If you need to check on CI status as an external contributor, login as a guest.

### Signing Off Commits After Submitting a Pull Request

Pull requests are automatically verified that all commit messages contain the Signed-off-by line with an email address that matches the commit author. In case you didn't sign off your commits before creating a pull request, you can still fix that to confirm that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

To sign off a single commit:

`git commit --amend --signoff`

To sign off one or multiple commits:

`git filter-branch --msg-filter "cat - && echo && echo 'Signed-off-by: Your Name <Your.Name@example.com>'" HEAD`

Then force push your branch:

`git push --force origin test-branch`

## Getting Help

If you run into any trouble, please reach out to us on the issue you are working on.

## Our Thanks

We deeply appreciate your effort toward improving Gradle. For any contribution, large or small, you will be immortalized in the release notes for the version you've contributed to.

If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)?
