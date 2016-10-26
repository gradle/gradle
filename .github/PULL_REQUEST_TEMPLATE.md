Any of the checked boxes below indicate that I took action:

- [ ] Reviewed the [Contribution Guidelines](https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md#contribution-workflow).
- [ ] Signed the [Gradle CLA](http://gradle.org/contributor-license-agreement/).
- [ ] Ensured that basic checks pass: `./gradlew quickCheck`

For all non-trivial changes that modify the behavior or public API:

- [ ] Before submitting a pull request, I started a discussion on the [Gradle developer list](https://groups.google.com/forum/#!forum/gradle-dev),
      the [forum](https://discuss.gradle.org/) or can reference a [JIRA issue](https://issues.gradle.org/secure/Dashboard.jspa).
- [ ] I considered writing a design document. A design document can be
brief but explains the use case or problem you are trying to solve,
touches on the planned implementation approach as well as the test cases
that verify the behavior. Optimally, design documents should be submitted
as a separate pull request. [Samples](https://github.com/gradle/gradle/tree/master/design-docs)
can be found in the Gradle GitHub repository. Please let us know if you need help with
creating the design document. We are happy to help!
- [ ] The pull request contains an appropriate level of unit and integration
test coverage to verify the behavior. Before submitting the pull request
I ran a build on your local machine via the command
`./gradlew quickCheck <impacted-subproject>:check`.
- [ ] The pull request updates the Gradle documentation like user guide,
DSL reference and Javadocs where applicable.
