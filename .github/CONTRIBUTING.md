# Contributing to Gradle
Thank you for considering making a contribution to Gradle! This guide explains how to setup your environment for Gradle development, how to identify tasks to work on, and where to get help if you encounter trouble.

## Development Setup
In order to make changes to Gradle, you'll need:

* A text editor or IDE. We use and recommend [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK) version 1.7 or higher
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join)

Gradle uses a pull request model for contributions. Fork [gradle/gradle](https://github.com/gradle/gradle) and clone your fork. Configure your Git username and email with
```
git config user.name 'First Last'
git config user.email user@example.com
```

You can generate the IntelliJ project by running
```
./gradlew idea
```
then "Open" it with IntelliJ.

You can generate the Eclipse projects by running
```
./gradlew eclipse
```

## Choosing Tasks
If you'd like to contribute to Gradle but aren't sure where, please look for issues [labelled help-wanted](https://github.com/gradle/gradle/labels/help-wanted). We have designated these issues as good candidates for easy contribution.

## Making Changes

### Larger Changes (features or enhancements)
Before starting to work on a feature or a fix, please open an issue to discuss the use case or bug with us.
Doing so helps to ensure that:
* You understand how your proposed changes fit with the strategic goals of the Gradle project.
* You can get early feedback on your proposed changes, and suggestions as to the best approach to implementation.
* We can all collaborate in creating a [design document](../design-docs). This is _required_ for all changes that affect behavior or public API.
    
### Code Change Guidelines
All code contributions should contain the following:

* Unit Tests (using [Spock](http://spockframework.org/spock/docs/1.1-rc-2/index.html)) for any logic introduced
* Integration Test coverage of the bug/feature at the level of build execution. Please annotate tests guarding against a specific GitHub issue `@Issue("gradle/gradle#123")`.
* Documentation in the User Guide and DSL Reference (under `subprojects/docs/src/docs`). You can generate docs by running `./gradlew :docs:docs`.
* Javadoc `@author` tags and committer names are not used in the codebase (contributions are recognised in the commit history and release notes)

Your code needs to run on all supported Java versions and operating systems. The [Gradle CI](http://builds.gradle.org/) will verify this, but here are some pointers that will avoid surprises:

* Avoid using features introduced in Java 1.7 or later. Several parts of Gradle still need to run on Java 6.
* Be careful to normalise file paths in tests. The `org.gradle.util.TextUtil` class has some useful utility functions for this purpose.

### Development Workflow
After making changes, you can test them in 2 ways:

To run tests, execute `./gradlew :<subproject>:check` where `<subproject>` is the name of the sub-project that has changed. For example: `./gradlew :launcher:check`.
To try out a change in behavior manually, install Gradle source locally and use it
```
./gradlew install -Pgradle_installPath=/any/path
/any/path/bin/gradle taskName
```

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach at debugger at `localhost:5005` by default.

### Creating Commits And Writing Commit Messages
The commit messages that accompany your code changes are an important piece of documentation, and help make your contribution easier to review.
Please consider reading [How to Write a Git Commit Message](http://chris.beams.io/posts/git-commit/). Minimally, follow these guidelines when writing commit messages.

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`Issue: #123`) in the commit message on a separate line
* Please check that your email address matches that on your [CLA](http://gradle.org/cla)

### Submitting Your Change
Before we can accept any code contributions, you must complete and electronically sign a [Gradle CLA](http://gradle.org/cla).

All code contributions should be submitted via a [pull request](https://help.github.com/articles/using-pull-requests) from a [forked GitHub repository](https://help.github.com/articles/fork-a-repo).

Once received, the pull request will be reviewed and eventually merged by a Gradle core developer. It is normal that this takes several iterations, so don't get discouraged by change requests. They ensure the high quality that we all enjoy.

### Getting Help
If you run into any trouble, please reach out to us on the [help/discuss forum](https://discuss.gradle.org/c/help-discuss) or on the issue you are working on.

## Our Thanks
We deeply appreciate your effort toward improving Gradle. For any contribution, large or small, you will be immortalized in the release notes for the version you've contributed to. 
  
If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)? 




