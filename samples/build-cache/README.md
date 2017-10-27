build-cache
===========

A project that configures Gradle build cache.

See [settings.gradle.kts](./settings.gradle.kts).

Populate local cache with:

    ./gradlew build

Build and fetch cached tasks outputs from local cache with:

    ./gradlew clean build
