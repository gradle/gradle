## New and noteworthy

Here are the new features introduced in Gradle 1.1-rc-1

### Test Logging

In previous versions, Gradle didn't show much information about what happened during test execution.
In Gradle 1.1-rc-1, the default output is more useful, and can be tuned to your preferences in many ways.
All features are supported both for JUnit and TestNG.

#### Show Exceptions

One of the most useful options is to show the exceptions thrown by failed tests. By default, Gradle will
log a succinct message for every test exception. To get more detailed output, configure the `exceptionFormat`:

<pre>
test {
    testLogging {
        exceptionFormat "full"
    }
}
</pre>

#### Stack Trace Filters

Stack traces of test exceptions are automatically truncated not to show anything below the entry point into
the test code. This filters out Gradle internals and internals of the test framework. A number of other
filters are available. For example, when dealing with Groovy code it makes sense to add the `groovy` filter:

<pre>
test {
    testLogging {
        stackTraceFilters "truncate", "groovy"
    }
}
</pre>

#### Show Other Test Events

Besides a test having failed, a number of other test events can be logged:

<pre>
test {
    testLogging {
        events "started", "passed", "skipped", "failed", "standardOut", "standardError"
        minGranularity 0
    }
}
</pre>

By setting `minGranularity`, these events aren't only shown for individual tests, but also for test classes and suites.

#### Individual Logging Per Log Level

Test logging can be configured separately per log level:

<pre>
test {
    testLogging {
        quiet {
            events "failed"
        }
    }
}
</pre>

On log levels `LIFECYCLE`, `INFO`, and `DEBUG`, some test events (most importantly failed tests) are already shown by default.
For detailed documentation about all test logging related options, see
[TestLogging](http://gradle.org/docs/nightly/javadoc/org/gradle/api/tasks/testing/logging/TestLogging.html)
and [TestLoggingContainer](http://gradle.org/docs/nightly/javadoc/org/gradle/api/tasks/testing/logging/TestLoggingContainer.html).

### Tooling API provides Gradle module information for external dependencies

The Tooling API can be used to obtain the model of the project which includes the information about the dependencies/libraries.
Now the Tooling API also provides Gradle module information, i.e. group, name, version of the dependency.
Please see the javadoc for [ExternalGradleModule](http://gradle.org/docs/nightly/javadoc/org/gradle/tooling/model/ExternalGradleModule.html).
You can obtain the Gradle module information iva [ExternalDependency.getExternalGradleModule()] (http://gradle.org/docs/nightly/javadoc/org/gradle/tooling/model/ExternalDependency.html#getExternalGradleModule()).

### Some feature

#### Some detail

Some details about the feature.

## Upgrading from Gradle 1.0

Please let us know if you encounter any issues during the upgrade to Gradle 1.1-rc-1, that are not listed below.

### Deprecations

#### Statement Labels

As in Java, statement labels are rarely used in Groovy. The following example shows a frequent pitfall where a
statement label is erroneously used in an attempt to configure an object:

<pre>
task foo {
    dependsOn: bar // does nothing; correct is 'dependsOn bar' or 'dependsOn = [bar]'
}
</pre>

To prevent such mistakes, the usage of statement labels in build scripts has been deprecated.

#### DSL

##### `Project.fileTree(Object)` - Removal of incorrect `@deprecation` tag

The `Project.fileTree(Object)` method was incorrectly annotated with the `@deprecated`
Javadoc tag in Gradle 1.0-milestone-8. This method has not been deprecated and the Javadoc tag has been removed.

##### `Project.fileTree(Closure)` - Addition of `@deprecation` tag

The `Project.fileTree(Closure)` method was deprecated in Gradle 1.0-milestone-8. The method was not
annotated with the `@deprecated` javadoc tag at that time. This has been added for this release.

#### API

##### `org.gradle.api.tasks.testing.TestLogging` - Moved into `logging` subpackage

The `org.gradle.api.tasks.testing.TestLogging` interface was moved into package
`org.gradle.api.tasks.testing.logging` (and subsequently enhanced with new methods).
For backwards compatibility reasons, the old interface was kept at its original location,
but is now deprecated.

### Potential breaking changes

##### `idea.project.jdkName`

We've decided to change the IDEA plugin's default JDK name. The new default is now smarter. Without this change,
many users had to configure the JDK name explicitly in the builds or manually tweak the JDK name in IDEA after running
the `gradle idea` task. The current default uses the Java version that Gradle runs with.

Although we believe the new default is much better for majority of users, there might be some builds out there
that preferred the old default. If you happen to prefer the old default (`1.6`) please configure
that explicitly in your build via [idea.project.jdkName](http://gradle.org/docs/current/dsl/org.gradle.plugins.ide.idea.model.IdeaProject.html#org.gradle.plugins.ide.idea.model.IdeaProject:jdkName)

##### maven settings.xml

We've updated the handling of the local maven cache to use the maven3 settings builder library to parse the maven `settings.xml`. When using the local maven cache via `mavenLocal()`, Gradle will fail the build if the settings builder cannot parse the `settings.xml` in `USER_HOME/.m2/settings.xml` or in `M2_HOME/conf/settings.xml`. If a custom location for the local repository is defined in the maven settings file, Gradle will use this location. If no `settings.xml` is available or it contains no local repository definition, Gradle uses the default location in `USER_HOME/.m2/repository`. 

## Fixed Issues

The list of issues fixed between 1.0 and 1.1-rc-1 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.1-rc-1%22%29+ORDER+BY+priority&tempMax=1000).
