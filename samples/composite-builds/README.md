composite-builds
================

Demonstrates how to use Composite Builds.

The build in this directory composes two other included builds, namely [`core`](./core) and [`cli`](./cli).
The `cli` build depends on artifacts produced by the `cli` build.

Run with:

    ./gradlew run

See [settings.gradle.kts](./settings.gradle.kts) to see how to include builds and do dependency substitution.
