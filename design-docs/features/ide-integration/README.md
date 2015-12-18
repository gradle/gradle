This spec defines a number of features to improve the developer experience using Gradle from the IDE. It covers changes in Gradle to improve those features that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins (i.e. the plugins that run inside Gradle to provide the IDE models).

Tooling API stories that are not related directly to the IDE experience should go in the `tooling-api-improvements.md` spec.

One goal of this overall feature is to make it possible to import Gradle projects directly into Eclipse and IDEA, with user customizations taken into account.

## Features

- [Expose source and target platforms of JVM language projects](source-and-target-jvm)
- [Developer uses projects from multiple Gradle builds from IDE](ide-multiple-build)
- [Developer uses subset of a multi-project Gradle build from IDE](ide-subset-build)
- [Provide smooth upgrade from STS Gradle to Buildship] (upgrade-to-buildship)
