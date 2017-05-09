### Context
<!--- Why do you believe many users will benefit from this change? -->
<!--- Link to relevant issues or forum discussions here -->

### Contributor Checklist
- [ ] [Review Contribution Guidelines](https://github.com/gradle/gradle/blob/master/.github/CONTRIBUTING.md)
- [ ] [Sign Gradle CLA](http://gradle.org/contributor-license-agreement/)
- [ ] [Link to Design Spec](https://github.com/gradle/gradle/tree/master/design-docs) for changes that affect more than 1 public API (that is, not in an `internal` package) or updates to > 20 files
- [ ] Provide integration tests (under `<subproject>/src/integTest`) to verify changes from a user perspective
- [ ] Provide unit tests (under `<subproject>/src/test`) to verify logic
- [ ] Update User Guide, DSL Reference, and Javadoc for public-facing changes
- [ ] Ensure that tests pass locally: `./gradlew quickCheck <impacted-subproject>:check`

### Gradle Core Team Checklist
- [ ] Verify design and implementation 
- [ ] Verify test coverage and CI build status
- [ ] Verify documentation including proper use of `@since` and `@Incubating` annotations for all public APIs
- [ ] Recognize contributor in release notes
