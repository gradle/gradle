This spec collects issues that prevent Gradle from working well, or at all, with JDK 9.

## Issues

This list is in priority order.

- tools.jar no longer exists as part of the JDK so `org.gradle.internal.jvm.JdkTools`(and others) need an alternative way to
get a SystemJavaCompiler which does not includes Gradle's own classes, i.e. without using `ClassLoader.getSystemClassLoader()`
- JDK 9 is not java 1.5 compatible. `sourceCompatibility = 1.5` and `targetCompatibility = 1.5` will no longer work.
- Some tests use `tools.jar` as a "big jar", they need to be refactored to use something else.
- JDK 9 has completely changed the JVM and `org.gradle.internal.jvm.Jvm` is no longer an accurate model:
    - No longer a distinction between JRE and SDK, it's all rolled into one.
    - Files or jars under `lib/` should not be referenced: [http://openjdk.java.net/jeps/220](http://openjdk.java.net/jeps/220)
    _All other files and directories in the lib directory must be treated as private implementation details of the run-time system_

### Cannot fork build processes (e.g. test execution processes)

See:
- https://discuss.gradle.org/t/classcastexception-from-org-gradle-process-internal-child-bootstrapsecuritymanager/2443
- https://issues.gradle.org/browse/GRADLE-3287
- http://download.java.net/jdk9/docs/api/index.html

Proposed solution is to use a classpath manifest jar only on 9 and later.

### Default daemon args are invalid with Java 9

See: https://issues.gradle.org/browse/GRADLE-3286
