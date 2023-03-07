# Cross Version Test

## Pitfalls

###  Executing Gradle Versions that don't support your current JVM

This will result in e.g. 

    execution failed for task :tooling-api:gradle3.5.1CrossVersionTest'.
    > No tests found for given includes: [org.gradle.integtests.tooling.r81.LogLevelConfigCrossVersionSpec](--tests filter)`

You can fix it by adding `-PtestJavaVersion=8` to the build.

### Executing with Kotlin scripts on Gradle Version prior 5.0.
    
This will result in an invocation as if no script is present and you'll be puzzled why nothing you wrote in the script is happening.
The reason is that Kotlin DSL was introduced in Gradle 5.0. Older Gradle versions simply ignore Kotlin scripts.
The solution is to use a `groovy` script since it works on all Gradle Versions. 
