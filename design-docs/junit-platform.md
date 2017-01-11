# Junit Platform (a.k.a. JUnit 5) Support

## Introduction

This design doc speaks to issue [#828](https://github.com/gradle/gradle/issues/828), the epic for JUnit 5 support.

### What is the JUnit Platform?

The [JUnit 5](http://junit.org/junit5/) project consists of 3 primary sub-projects (see [their descriptions in the user guide](http://junit.org/junit5/docs/current/user-guide/#overview-what-is-junit-5)):

* **JUnit Platform** a new launching API for executing test frameworks and a `TestEngine` abstraction for implementing a test framework that can be launched by the platform.
* **JUnit Jupiter** a `TestEngine` and new API for writing JUnit tests providing new features, many to support Java 8 lambdas.
* **JUnit Vintage** a `TestEngine` for executing JUnit 3 and 4 tests from the platform.

As the de-facto standard test framework in the JVM ecosystem, a new version of JUnit should have strong support within Gradle. In the context of Gradle support, the JUnit Platform is likely the only piece that needs to be (or should be) addressed. The platform can deal with launching Jupiter or Vintage or whatever other tests.

Marc Philipp (of the JUnit team) [initiated a discussion](https://discuss.gradle.org/t/core-support-for-junit-platform-a-k-a-junit-5/19487) on including JUnit 5 support as a core feature of Gradle. As a proof-of-concept, the JUnit team has created their own [Gradle plugin](https://github.com/junit-team/junit5/tree/master/junit-platform-gradle-plugin/src/main/groovy/org/junit/platform/gradle/plugin) to launch JUnit Platform tests ([documented here](http://junit.org/junit5/docs/current/user-guide/#running-tests-build)). This provides basic support for executing the `ConsoleLauncher` which is a CLI app for executing the JUnit Platform. Their plugin does not participate with the wider testing infrastructure in Gradle or the new model plugins.

### Current Issues with the Testing Infrastructure

* `Test` task has a number of drawbacks:
    * Presumes use of JUnit 3.8-4.x or TestNG as the test framework
    * Takes control of the execution model (fork every X classes, etc). JUnit5 has [an issue open](https://github.com/junit-team/junit5/issues/60) to support parallelism themselves. This may or may not belong as direct Gradle support.
    * Takes control of test detection (with a class-based model). For most use cases this is OK, but it limits the flexibility of the `TestEngine`s in the JUnit Platform space. (In particular, this seems to align poorly with Clojure tests.)
* `testing-jvm` (model plugin) separates a `JUnitTestSuitePlugin` from `JvmTestSuiteBasePlugin`, but the base plugin still presumes use of the `Test` task, which has the limitations above.
* Native plugins don't seem to participate in the same mechanisms as the JVM `Test` task (Not being a native developer, I don't even know if this is feasible/desired. Just seems like it's better to have consistency if possible.):
    * Events going through `TestListener`
    * Logging of results configured through `TestLoggingContainer`
    * Reporting in a common report format
    * Support through the Tooling API (and thus BuildShip)
* 3rd party plugins cannot implement support for new test frameworks and get the benefits of a core plugin (without extensive use of internals). See native plugins bullet above for limitations this causes.

## Phase 0 - Identify Needed Gradle Abstractions

Identify the abstractions and features needed to support arbitrary test frameworks with minimal coupling to their semantics.

### Initial Thoughts

* Core Infrastructure (JVM semantics may need to be extracted out if native or any other future languages/platforms should participate.)
    * Identification - There needs to be a framework-agnostic test ID class (`TestDescriptor` exists now) for the below features
    * Events - Current `TestListener` (really one of the internal protocols for this) seems to be the glue that feeds the other items below. This seems helpful, but needs to be usable for plugins in the public API.
    * Logging - Current `TestLoggingContainer` provides a way to customize the inline reporting of test results. This seems helpful to all test frameworks. There may be question of what level to provide this customization. Should it be a global extension or specific to each test task.
    * Reporting - Test frameworks usually provide their own reporting capabilities, so it wouldn't be strictly necessary to handle this directly in Gradle. However, aggregation of reports can be beneficial as well as consistent formatting.
    * Tooling API (by extension BuildShip) - It should be possible to execute, see the results of, rerun, run individual, etc for arbitrary test frameworks through the Tooling API. This should be reflected in consumers like BuildShip as well.
* Test Platform/Framework
    * Either through a base task or just exposure of the right hooks, a plugin should be able to support any arbitrary test framework and get all of the infrastructure benefits above.
    * The detection and execution models of those test frameworks should not be assumed by the core infrastructure.

## Phase 1 - Test Infrastructure Changes

Enhance the test infrastructure to support the core features that Gradle cares about independent of test frameworks. Provide abstractions to allow test frameworks/platforms to be implemented through plugins (using only public APIs).

*Details TBD*

## Phase 2 - JUnit Platform Support

Implement support for execution of tests through the JUnit Platform. This should leverage the test infrastructure improvements from phase 1. (May need to happen concurrently with phase 1.)

*Details TBD*

## Phase 3 - JUnit "Vintage" and TestNG Support

Support for JUnit "Vintage" (3 and 4) and TestNG leveraging the test infrastructure improvements, through one or more of the following:

* Adapting the `Test` task to leverage the new infrastructure
* Implementing new plugins/tasks to execute those tests using the new infrastructure
* Relying on the JUnit Platform to have a `TestEngine` that can execute them

*Details TBD*

## Phase 4 - Evaluate Native Support

Determine whether native plugins can/should leverage the same testing infrastructure as the JVM, to allow consistent

*Details TBD*

## Phase 5 - Refinement

*Details TBD*
