# Contributing to Gradle Kotlin DSL
Thank you for considering making a contribution to Gradle Kotlin DSL! This guide explains how to setup your environment for Gradle Kotlin DSL development and where to get help if you encounter trouble.

## Development Setup
In order to make changes to Gradle, you'll need:

* A text editor or IDE. We use and recommend the very latest [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK) version 1.8 or higher
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join)

Gradle Kotlin DSL uses a pull request model for contributions. Fork [gradle/kotlin-dsl](https://github.com/gradle/kotlin-dsl) and clone your fork. Configure your Git username and email with
```
git config user.name 'First Last'
git config user.email user@example.com
```

Before importing the project into IntelliJ IDEA make sure to run `./gradlew check` at least once so all required files are generated.

## Making Changes

### Development Workflow

After making changes, you can test them by running `./gradlew check`.

To try out a change in behavior manually, use the Gradle distribution from `./build/custom`, just make sure to stop all Gradle daemons before using it (`./gradlew --stop`).

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach at debugger at `localhost:5005` by default. We recommend disabling the Gradle Daemon when debugging (`--no-daemon`).

### Getting Help

If you run into any trouble, please reach out to us in the #gradle channel of the public [Kotlin Slack](http://kotlinslackin.herokuapp.com/) instance.

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, and help make your contribution easier to review.
Please consider reading [How to Write a Git Commit Message](http://chris.beams.io/posts/git-commit/). Minimally, follow these guidelines when writing commit messages.

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`See #123`) in the commit message on a separate line
* Please check that your email address matches that on your [CLA](http://gradle.org/cla)

### Submitting Your Change
Before we can accept any code contributions, you must complete and electronically sign a [Gradle CLA](http://gradle.org/cla).

All code contributions should be submitted via a [pull request](https://help.github.com/articles/using-pull-requests) from a [forked GitHub repository](https://help.github.com/articles/fork-a-repo).

Once received, the pull request will be reviewed by a Gradle Kotlin DSL developer.

## Our Thanks
We deeply appreciate your effort toward improving Gradle. If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)? 
