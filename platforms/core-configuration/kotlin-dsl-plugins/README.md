## Note about integration and cross-version tests for this subproject

Put plugin integration and cross-version tests into [`:kotlin-dsl-integ-tests`](https://github.com/gradle/gradle/tree/HEAD/platforms/core-configuration/kotlin-dsl-integ-tests) subproject instead.

Having tests here breaks Gradle's ability to instrument plugins defined in this subproject and their dependencies when embedded runner is used.
Instrumenting the plugins is important to properly test configuration caching and bytecode upgrades, without it the behavior of the embedded runner doesn't match the normal Gradle execution.
In short, for integration tests defined here the plugins and their dependencies, including KGP, are loaded with the application classloader which is not instrumented.
