## Note about integration and cross-version tests for this subproject

Put plugin integration and cross-version tests into [`:kotlin-dsl-integ-tests`](https://github.com/gradle/gradle/tree/HEAD/platforms/dsl/kotlin-dsl-integ-tests) subproject instead.

Having tests here breaks Gradle's ability to instrument plugins defined in this subproject and their dependencies when embedded runner is used.
Instrumenting the plugins is important to properly test configuration caching and bytecode upgrades, without it the behavior of the embedded runner doesn't match the normal Gradle execution.
In short, for integration tests defined here the plugins and their dependencies, including KGP, are loaded with the application classloader which is not instrumented.

## Use the Kotlin DSL plugins built from this source

Test builds that apply the `kotlin-dsl` plugin get a published version of that plugin.
A published version is tied to the Kotlin version that Gradle embedded when the plugin was published.
After a bump of the embedded Kotlin version, the two Kotlin versions differ.
When testing such a bump many tests will fail because of the version mismatch.
Also, these tests do not test the right plugin: they should be using a `kotlin-dsl` plugin that is tied to the current embedded Kotlin version.

To use the plugins built from this source in the tests of a project, apply `gradlebuild.kotlin-dsl-plugin-bundle-integ-tests` to the project.
This plugin publishes the Kotlin DSL plugins to the local repository of the integration tests and cross version tests, at their own version, which is the next version to publish.
There, the repository is registered as an extra repository (see [Testing](../../../contributing/Testing.md#add-a-repository-for-dependencies-that-are-not-on-public-repositories)).
Gradle expects the published version of the plugins. The tests make the builds under test expect the version in the local repository instead, with the system property `org.gradle.internal.kotlin.dsl.plugins.version.override`.
`GradleExecuter` builds, smoke test builds and performance test builds get the property on their own.
Tooling API tests must give it to their builds, see `RepoScriptBlockUtil.locallyBuiltKotlinDslPluginsArguments()`. The Kotlin DSL model specs that extend `AbstractKotlinScriptModelCrossVersionTest` do this.
