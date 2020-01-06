<!--- The issue this PR addresses -->
Fixes #?

### Context
<!--- Why do you believe many users will benefit from this change? -->
<!--- Link to relevant issues or forum discussions here -->

### Contributor Checklist
- [ ] [Review Contribution Guidelines](https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md)
- [ ] Make sure that all commits are [signed off](https://git-scm.com/docs/git-commit#git-commit---signoff) to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).
- [ ] Check ["Allow edit from maintainers" option](https://help.github.com/articles/allowing-changes-to-a-pull-request-branch-created-from-a-fork/) in pull request so that additional changes can be pushed by Gradle team
- [ ] Provide integration tests (under `<subproject>/src/integTest`) to verify changes from a user perspective
- [ ] Provide unit tests (under `<subproject>/src/test`) to verify logic
- [ ] Update User Guide, DSL Reference, and Javadoc for public-facing changes
- [ ] Ensure that tests pass locally: `./gradlew <changed-subproject>:check`

### Gradle Core Team Checklist
- [ ] Verify design and implementation 
- [ ] Verify test coverage and CI build status
- [ ] Verify documentation
- [ ] Recognize contributor in release notes
