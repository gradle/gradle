Investigate and improve performance of the Tooling API.

Audience is users who call Gradle from the Tooling API, which includes, but is not limited to, most of IDE users (IntelliJ IDEA, Eclipse, Netbeans).

## Stories

### Performance tests establish build model creation baseline

Make sure that there's at least one performance test that measures the creation of a Tooling API model (should be `IdeaModel` or `EclipseModel`, or both, depending on the impact on performance test build duration) from the tooling API.

- Add the necessary infrastructure to be able to measure the performance of the tooling API itself for various versions of Gradle.
- We're only testing the Tooling API here, so it's not necessary to test against multiple target versions of Gradle. Instead, we will compare different versions of the Tooling API against a the same version of Gradle (so connecting the TAPI with the corresponding version of Gradle).
- Reuse, if possible, the `CrossVersionResultsStore`
- Sample build should be a Java project with a reasonable number of subprojects

