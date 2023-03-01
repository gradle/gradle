# Cross Version Test

## Pitfalls

1. Executing Gradle Versions that don't support your current JVM

   This will result in e.g. 
   ```
   execution failed for task :tooling-api:gradle3.5.1CrossVersionTest'.
    > No tests found for given includes: [org.gradle.integtests.tooling.r81.LogLevelConfigCrossVersionSpec](--tests filter)`
   ```
   You can fix it by adding `-PtestJavaVersion=8` to your test run configuration in IntelliJ

1. Executing with Kotlin scripts on Gradle Version prior 5.0.
    
    This will result in an invocation as if no script is present and you'll be puzzled why nothing you wrote in the script is happening. Obviously the older Gradle Version just ignores the Kotlin script.
    The solution is to use a `groovy` script since it works on all Gradle Versions. 
