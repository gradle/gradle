## New and noteworthy

Here are the new features introduced in Gradle 1.3.

## Brand new 'dependency insight' report.

The new report attempts to answer following questions:

* why this dependency is in the dependency graph?
* what are all the requested versions of this dependency?
* why this dependency has this version selected?

(in progress)

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to our backwards compatibility policy.

## Fixed Issues

The list of issues fixed between 1.2 and 1.3 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.3-rc-1%22%29+ORDER+BY+priority&tempMax=1000).

## Incubating features

We will typically introduce new features as _incubating_ at first, giving you a chance to test them out.
Typically the implementation quality of the new features is already good but the API might still change with the next release based on the feedback we receive.
For some very challenging engineering problems like the Gradle Daemon or parallel builds, it is impossible to get the implementation quality right from the beginning.
So we need you here also as beta testers.
We will iterate on the new feature based on your feedback, eventually releasing it as stable and production-ready.
Those of you who use new features before that point gain the competitive advantage of early access to new functionality in exchange for helping refine it over time.
To learn more read our [forum posting on our release approach](http://forums.gradle.org/gradle/topics/the_gradle_release_approach).

### Improved TestNG html report

(in progress)

### Resolution result API

* (in progress)
* The entry point to the ResolutionResult API has changed, you can get access to the instance of the ResolutionResult from the ResolvableDependencies.

## Upgrading from Gradle 1.2

Please let us know if you encounter any issues during the upgrade to Gradle 1.3, that are not listed below.

### Deprecations

#### Ant-task based Java compiler integration

Gradle currently supports two different Java compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<javac>` Ant task. The native Gradle integration has been the default since Gradle 1.0-milestone-9.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `CompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `optimize`
* `includeJavaRuntime`

#### Ant-task based Groovy compiler integration

Gradle currently supports two different Groovy compiler integrations: A native Gradle integration that uses the compiler APIs directly, and an Ant-task
based implementation that uses the `<groovyc>` Ant task. The native Gradle integration has been the default since Gradle 1.0.

The Ant-task based integration has now been deprecated and will be removed in Gradle 2.0. As a result, the following properties of `GroovyCompileOptions` are also
deprecated and will be removed in Gradle 2.0:

* `useAnt`
* `stacktrace`
* `includeJavaRuntime`

### Potential breaking changes

#### Incubating C++ `Compile` task type removed

This was replaced by `CppCompile` in Gradle 1.2. You should use the replacement class instead.

#### Incubating `GppCompileSpec` properties removed

The deprecated `task` property was removed from `GppCompileSpec`.

#### Removed GraphvizReportRenderer (private API)

This type was an early contribution. It is unlikely anyone uses it because it does not work and it is an undocumented private type.