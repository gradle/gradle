The Gradle team has an important update to [3.2](https://docs.gradle.org/3.2/release-notes). This release fixes critical defects to the Gradle wrapper involving argument escaping that may prevent the wrapper from executing.

It does not include any new features. We recommend anyone upgrading from an older version to upgrade straight to Gradle 3.2.1 instead of Gradle 3.2.
 
## Additional upgrade instructions

If you are upgrading from Gradle 3.2 and using the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), re-run `./gradlew wrapper` and commit the result to avoid a misconfigured wrapper script.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
