Windows specific daemon problems

- Can change test code when using JUnit categories [GRADLE-3315](https://issues.gradle.org/browse/GRADLE-3315)
- Can change buildSrc logic [GRADLE-2415](https://issues.gradle.org/browse/GRADLE-2415)
- Can clean after compiling [GRADLE-2275](https://issues.gradle.org/browse/GRADLE-2275)
- Daemon should start when JAVA_HOME points to JRE [GRADLe-2803](https://issues.gradle.org/browse/GRADLE-2803)
- Properly disconnect daemon process from parent process input [GRADLE-3265](https://issues.gradle.org/browse/GRADLE-3265)

### Story - Build script classpath can contain a changing jar

Fix ClassLoader caching to detect when a build script classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.
