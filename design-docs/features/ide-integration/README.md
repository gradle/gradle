This spec defines a number of features to improve the developer experience using Gradle from the IDE. It covers changes in Gradle to improve those features that directly affect the IDE user experience. This includes the tooling API and tooling models, the Gradle daemon, and the Gradle IDE plugins (i.e. the plugins that run inside Gradle to provide the IDE models).

Tooling API stories that are not related directly to the IDE experience should go in the `tooling-api-improvements.md` spec.

One goal of this overall feature is to make it possible to import Gradle projects directly into Eclipse and IDEA, with user customizations taken into account.

## Features

- [Developer defines and uses projects from a composite Gradle build within IDE](model-composite-build)
- [Developer uses a subset of projects from a multi-project Gradle build within IDE](model-subset-build)
- [Developer executes tasks from a composite Gradle build within IDE](execute-tasks-in-composite)
- [Richer Eclipse model](richer-eclipse-model)
