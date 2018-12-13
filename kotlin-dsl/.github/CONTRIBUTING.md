# Contributing to Gradle Kotlin DSL
Thank you for considering making a contribution to Gradle Kotlin DSL! This guide explains how to setup your environment for Gradle Kotlin DSL development and where to get help if you encounter trouble.

## Development Setup
In order to make changes to Gradle, you'll need:

* A text editor or IDE. We use and recommend the very latest [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK) version 1.8 or higher
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join)

Gradle Kotlin DSL uses a pull request model for contributions. Fork [gradle/kotlin-dsl](https://github.com/gradle/kotlin-dsl) and clone your fork. Active development happens in the `develop` branch, so make sure you check out this branch after cloning the repository. 

Configure your Git username and email with
```
git config user.name 'First Last'
git config user.email user@example.com
```

Before importing the project into IntelliJ IDEA make sure to run `./gradlew check` at least once so all required files are generated.

## Making Changes

### Development Workflow

After making changes, you can test them by running `./gradlew check`.

To try out a change in behavior manually, use the Gradle distribution from `./build/custom`, just make sure to stop all Gradle daemons before using it (`./gradlew --stop`).

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach at debugger at `localhost:5005` by default.

### Getting Help

If you run into any trouble, please reach out to us in the #kotlin-dsl channel of the [Gradle Community Slack](https://join.slack.com/t/gradle-community/shared_invite/enQtNDE3MzAwNjkxMzY0LTYwMTk0MWUwN2FiMzIzOWM3MzBjYjMxNWYzMDE1NGIwOTJkMTQ2NDEzOGM2OWIzNmU1ZTk5MjVhYjFhMTI3MmE).

### Resources

* The [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html) is a must read.
* The Gradle [user manual](https://docs.gradle.org/current/userguide/userguide.html) and [guides](https://gradle.org/guides/) contain Kotlin DSL build script samples that demonstrate how to use all Gradle features.
* Some [diagrams](../doc/c4) provide a good overview of how the Kotlin DSL is structured and interacts with Gradle, Gradle plugins, IDEs.

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, and help make your contribution easier to review.
Please consider reading [How to Write a Git Commit Message](http://chris.beams.io/posts/git-commit/). Minimally, follow these guidelines when writing commit messages.

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`See #123`) in the commit message on a separate line
* [Sign off](https://git-scm.com/docs/git-commit#git-commit---signoff) your Git commits to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

### Submitting Your Change

All code contributions should be submitted via a [pull request](https://help.github.com/articles/using-pull-requests) from a [forked GitHub repository](https://help.github.com/articles/fork-a-repo).

Once received, the pull request will be reviewed by a Gradle Kotlin DSL developer.

## Our Thanks
We deeply appreciate your effort toward improving Gradle. If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)? 
