This spec collects issues that prevent Gradle from working well, or at all, with JDK 9.

## Issues

This list is in priority order.

- `tools.jar` no longer exists as part of the JDK so `org.gradle.internal.jvm.JdkTools`(and others) need an alternative way to
get a SystemJavaCompiler which does rely on the JavaCompiler coming from an isolated, non-system `ClassLoader`. One approach would be:
    - Isolate the Gradle classes from the application `ClassLoader`
    - Load things, targeted for compilation, into an isolated `ClassLoader` as opposed to the JVM's application application `ClassLoader`.
    - `org.gradle.internal.jvm.JdkTools` could use `ToolProvider.getSystemJavaCompiler()` to get a java compiler

- JDK 9 is not java 1.5 compatible. `sourceCompatibility = 1.5` and `targetCompatibility = 1.5` will no longer work.
- Some tests use `tools.jar` as a "big jar", they need to be refactored to use something else.
- JDK 9 has completely changed the JVM and `org.gradle.internal.jvm.Jvm` is no longer an accurate model:
    - No longer a distinction between JRE and SDK, it's all rolled into one.
    - Files or jars under `lib/` should not be referenced: [http://openjdk.java.net/jeps/220](http://openjdk.java.net/jeps/220)
    _All other files and directories in the lib directory must be treated as private implementation details of the run-time system_

- Some tests which garbage collect(`System.gc()`) are failing. See: `ModelRuleExtractorTest`. There would need to be some exploration
to figure out how (or if) garbage collection is different on JDK9.

### Cannot fork build processes (e.g. test execution processes)

See:
- https://discuss.gradle.org/t/classcastexception-from-org-gradle-process-internal-child-bootstrapsecuritymanager/2443
- https://issues.gradle.org/browse/GRADLE-3287
- http://download.java.net/jdk9/docs/api/index.html

Proposed solution is to use a classpath manifest jar only on 9 and later.

### Default daemon args are invalid with Java 9

See: https://issues.gradle.org/browse/GRADLE-3286


### Initial JDK9 support in Gradle's own build
[gradle/java9.gradle](gradle/java9.gradle) adds both unit and integration test tasks executing on JDK 9.
 Once JDK 9 has been fully supported, jdk9 specific test tasks should be removed along with `[gradle/java9.gradle]`
