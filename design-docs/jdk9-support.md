This spec collects issues that prevent Gradle from working well, or at all, with JDK 9.

## Issues

This list is in priority order.

### Cannot fork build processes (e.g. test execution processes)

See: https://discuss.gradle.org/t/classcastexception-from-org-gradle-process-internal-child-bootstrapsecuritymanager/2443

Proposed solution is to use a classpath manifest jar only on 9 and later.

### Default daemon args are invalid with Java 9

See: https://issues.gradle.org/browse/GRADLE-3286