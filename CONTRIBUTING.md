# Gradle Project & Developer Guidelines

Gradle is completely open source (ASLv2 license) and is a community driven development effort, led by [Gradleware](http://gradleware.com).

## Contribute!

Along with the work of a small group of core developers, Gradle depends on great community contributions in order to keep improving.
If you have a bug you'd love to see fixed or a feature that you think is missing, it might be a good candidate for a contribution.

If you decide you have the time and enthusiasm to help out, then great! But before you start, check out the workflow and guidelines below.
Following these simple steps can help ensure that your code contribution ends up a valuable part of a future Gradle release.

## Contribution Workflow

This is the general process for contributing code to the Gradle project.

1. Ensure that you are prepared to sign and submit the [Gradle CLA](CLA.md). You'll need to sign one of these before any code contributions will be accepted into the Gradle codebase.
(We're still working on our electronic signing process).
2. Before starting to work on a feature or a fix, it's generally a good idea to open a discussion about your proposed changes on the Gradle Developer List (dev@gradle.codehaus.org). 
Doing so helps to ensure that:
    1. You understand how your proposed changes fit with the strategic goals of the Gradle project.
    2. You can get feedback on your proposed changes, and suggestions as to the best approach to implementation.
    3. The Gradle core devs can create a [Jira issue](http://issues.gradle.org) for the work if deemed necessary.
    4. You and the other devs can collaborate in creating a [design document](design-docs) if deemed necessary.
3. All code contributions should be submitted via a [pull request](https://help.github.com/articles/using-pull-requests) from a [forked GitHub repository](https://help.github.com/articles/fork-a-repo).
4. Once received, the pull request will be reviewed by a Gradle core developer. Your pull request will likely get more attention if you:
    1. Have first discussed the change on the Gradle Developer list.
    2. Have followed all of the Contribution Guidelines, below.
5. After review, and usually after a number of iterations of development, you pull request may be merged into the Gradle distribution.

## Contribution Guidelines

All code contributions should contain the following:

1. Unit Tests (we love Spock) for any logic introduced.
2. Integration Test coverage of the bug/feature at the level of build execution.
3. Documentation coverage in the UserGuide, DSL Reference and JavaDocs where appropriate.

If you're not sure where to start, ask on the developer list. There's likely a number of existing examples to help get you going.

Try to ensure that your code & tests will run successfully on Java 5, and on both *nix and Windows platforms. 
The [Gradle CI](http://builds.gradle.org/) will verify this, but it helps if things work first time.

1. Avoid using features introduced in Java 1.6 or later
2. Be careful to normalise file paths in tests. The `org.gradle.util.TextUtil` class has some useful utility functions for this purpose.

## Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, and help make your contribution easier to review.
Follow these guidelines when creating public commits and writing commit messages.

1. Keep commits discrete: avoid including multiple unrelated changes in a single commit.
2. Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation.
3. The first line of your commit message should be a summary of the changes and intent of the change. Details should follow on subsequent lines, each line prefixed by '-'.
4. If your commit pertains to a Jira issue, include the issue number (eg GRADLE-3424) in the commit message.

Example:

    GRADLE-2001 Ensure that classpath does not contain duplicate entries
    - details
      - sub-details
    - more details
