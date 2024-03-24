# Gradle Client

This is a desktop application acting as a Gradle Tooling API client.

The build requires Java 17 with `jlink` and `jpackage` JDK tools.
The build will fail to configure with the wrong Java version.
Building release distributables will fail if the required JDK tools are not available.

```shell
# Run from sources
./gradlew :gradle-client:run

# Run from sources in continuous mode
./gradlew -t :gradle-client:run

# Run debug build type from build installation
./gradlew :gradle-client:runDistributable

# Run release build type from build installation
./gradlew :gradle-client:runReleaseDistributable
```
