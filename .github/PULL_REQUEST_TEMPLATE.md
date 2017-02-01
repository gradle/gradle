## Context
<!--- Why do you believe many users will benefit from this change? -->
<!--- Link to relevant issues or forum discussions here -->

#### Contributor Checklist
- [ ] [Review Contribution Guidelines](https://github.com/gradle/gradle/blob/master/.github/CONTRIBUTING.md)
- [ ] [Sign Gradle CLA](http://gradle.org/contributor-license-agreement/)
- [ ] [Link to Design Spec](https://github.com/gradle/gradle/tree/master/design-docs) for changes that affect more than 1 public API or change > 20 files
- [ ] Integration tests to verify changes from a user perspective. Unit tests to verify logic
- [ ] User Guide, DSL Reference, and Javadoc updates for documentation
- [ ] Ensure that tests pass locally: `./gradlew quickCheck <impacted-subproject>:check`

#### Member Checklist
- [ ] Verify design and implementation 
- [ ] Verify test coverage and CI build status
- [ ] Verify documentation including proper use of `@since` and `@Incubating` annotations for all public APIs and internal interfaces
- [ ] Recognize contributor in release notes
