<!--- The issue this PR addresses -->
Fixes #?

### Context
<!--- Why do you believe many users will benefit from this change? -->
<!--- Link to relevant issues or forum discussions here -->

### Contributor Checklist
- [ ] [Review Contribution Guidelines](https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md)
- [ ] Make sure that all commits are [signed off](https://git-scm.com/docs/git-commit#Documentation/git-commit.txt---signoff) to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).
- [ ] Make sure all contributed code can be distributed under the terms of the [Apache License 2.0](https://github.com/gradle/gradle/blob/master/LICENSE), e.g. the code was written by yourself or the original code is licensed under [a license compatible to Apache License 2.0](https://apache.org/legal/resolved.html).
- [ ] Check ["Allow edit from maintainers" option](https://help.github.com/articles/allowing-changes-to-a-pull-request-branch-created-from-a-fork/) in pull request so that additional changes can be pushed by Gradle team
- [ ] Provide integration tests (under `<subproject>/src/integTest`) to verify changes from a user perspective
- [ ] Provide unit tests (under `<subproject>/src/test`) to verify logic
- [ ] Update User Guide, DSL Reference, and Javadoc for public-facing changes
- [ ] Ensure that tests pass sanity check: `./gradlew sanityCheck`
- [ ] Ensure that tests pass locally: `./gradlew <changed-subproject>:quickTest`

### Gradle Core Team Checklist
- [ ] Verify design and implementation 
- [ ] Verify test coverage and CI build status
- [ ] Verify documentation
