The Gradle team has an important update to [3.2](https://docs.gradle.org/3.2/release-notes). This release fixes critical defects to the Gradle wrapper involving argument escaping that may prevent the wrapper from executing.

It does not include any new features. We recommend anyone upgrading from an older version to upgrade straight to Gradle 3.2.1 instead of Gradle 3.2.

## Fixed regressions

 - [gradle/gradle#865](https://github.com/gradle/gradle/issues/865) Gradle wrapper fails to escape arguments with nested quotes
 - [gradle/gradle#877](https://github.com/gradle/gradle/issues/877) Newlines in environment variables used by the wrapper breaks application plugin shell script
 
## Additional upgrade instructions

If you are upgrading from Gradle 3.2 and using the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), re-run `./gradlew wrapper` and commit the result to avoid a misconfigured wrapper script.
