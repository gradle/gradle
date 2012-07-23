## New and noteworthy

Here are the new features introduced in Gradle 1.1.

### Test Logging

Gradle 1.1 provides much more detailed information during test execution, right on the console. We've worked hard to make the the new output useful and informative out of the box, but we've also given you the ability to finely tune it to your liking.

**TODO:** need a comparison here between the old and new, which also acts as a preview of the new.
**TODO:** Add example output for all of the settings below.

#### Show Exceptions

One of the most useful options is to show the exceptions thrown by failed tests. By default, Gradle will log a succinct message for every test exception. To get more detailed output, configure the `exceptionFormat`:

    test {
        testLogging {
            exceptionFormat "full"
        }
    }

#### Stack Trace Filters

Stack traces of test exceptions are automatically truncated not to show anything below the entry point into the test code. This filters out Gradle internals and internals of the test framework. A number of other filters are available. For example, when dealing with Groovy code it makes sense to add the `groovy` filter:

    test {
        testLogging {
            stackTraceFilters "truncate", "groovy"
        }
    }

#### Show Other Test Events

Besides a test having failed, a number of other test events can be logged:

    test {
        testLogging {
            events "started", "passed", "skipped", "failed", "standardOut", "standardError"
            minGranularity 0
        }
    }

By setting `minGranularity`, these events aren't only shown for individual tests, but also for test classes and suites.

#### Individual Logging Per Log Level

Test logging can be configured separately per log level:

    test {
        testLogging {
            quiet {
                events "failed"
            }
        }
    }

On log levels `LIFECYCLE`, `INFO`, and `DEBUG`, some test events (most importantly failed tests) are already shown by default. For detailed documentation about all test logging related options, see [TestLogging](javadoc/org/gradle/api/tasks/testing/logging/TestLogging.html) and [TestLoggingContainer](javadoc/org/gradle/api/tasks/testing/logging/TestLoggingContainer.html).

### Tooling API provides Gradle module information for external dependencies

The Tooling API can be used to obtain the model of the project which includes the information about the dependencies/libraries. Now the Tooling API also provides Gradle module information, i.e. group, name, version of the dependency. Please see the javadoc for [ExternalGradleModule](javadoc/org/gradle/tooling/model/ExternalGradleModule.html). You can obtain the Gradle module information via [ExternalDependency.getExternalGradleModule()](javadoc/org/gradle/tooling/model/ExternalDependency.html#getExternalGradleModule\(\)).

### Global maven settings.xml

When Gradle checks for local available artifacts and when using `mavenLocal` in your build scripts, Gradle now honours the maven settings located in `M2_HOME/conf/settings.xml` to locate the local maven repository. If a local repository is defined in `USER_HOME/.m2/settings.xml`, this location takes precedence over a repository definition in `M2_HOME/conf/settings.xml`.

### Publishing SHA1 checksums to Ivy repositories

Gradle will now automatically generate and publish SHA1 checksum files when publishing to an Ivy repository. There is no change required to your build script to enable this functionality.

For each file `foo.ext` published, Gradle will also publish a checksum file with the name `foo.ext.sha1`.

### Dependency resolution supports HTTP Digest Authentication

Due to the way we did pre-emptive HTTP Authentication, Gradle 1.0 was not able to handle a repository secured with HTTP Digest Authentication. This problem is now fixed.

As a result of this fix:

* Any GET/HEAD request issued by Gradle will no longer contain pre-emptive HTTP Authentication headers.
* An initial PUT/POST request will contain  Basic Authentication headers for pre-emptive HTTP Authentication.
    * If the server requires HTTP Basic Authentication, then this request will succeed automatically
    * If the server requires HTTP Digest Authentication, then this request will fail with a 401, and we will re-send the request with the correct headers.
* After the initial PUT/POST request, subsequent requests to the repository will have correct Auth headers, and will not require re-send.

## Upgrading from Gradle 1.0

Please let us know if you encounter any issues during the upgrade to Gradle 1.1, that are not listed below.

### Deprecations

#### Statement Labels

As in Java, statement labels are rarely used in Groovy. The following example shows a frequent pitfall where a statement label is erroneously used in an attempt to configure an object:

    task foo {
        dependsOn: bar // does nothing; correct is 'dependsOn bar' or 'dependsOn = [bar]'
    }

To prevent such mistakes, the usage of statement labels in build scripts has been deprecated.

#### M2_HOME system property

Passing the variable M2\_HOME as system property to locate the global maven settings file is deprecated. You should use a M2\_HOME environment variable instead.

#### Publication of missing artifacts

If a published module references an artifact where the artifact file does not exist, then publication will emit a warning and continue. Relying on this behaviour is deprecated; in a future Gradle version your build will fail if you attempt to publish an artifact that does not exist.

#### DSL

##### `Project.fileTree(Object)` - Removal of incorrect `@deprecation` tag

The `Project.fileTree(Object)` method was incorrectly annotated with the `@deprecated` Javadoc tag in Gradle 1.0-milestone-8. This method has not been deprecated and the Javadoc tag has been removed.

##### `Project.fileTree(Closure)` - Addition of `@deprecation` tag

The `Project.fileTree(Closure)` method was deprecated in Gradle 1.0-milestone-8. The method was not annotated with the `@deprecated` javadoc tag at that time. This has been added for this release.

#### API

##### `org.gradle.api.tasks.testing.TestLogging` - Moved into `logging` subpackage

The `org.gradle.api.tasks.testing.TestLogging` interface was moved into package `org.gradle.api.tasks.testing.logging` (and subsequently enhanced with new methods). For backwards compatibility reasons, the old interface was kept at its original location,
but is now deprecated.

### Potential breaking changes

##### `idea.project.jdkName`

We've decided to change the IDEA plugin's default JDK name. The new default is now smarter. Without this change, many users had to configure the JDK name explicitly in the builds or manually tweak the JDK name in IDEA after running the `gradle idea` task. The current default uses the Java version that Gradle runs with.

Although we believe the new default is much better for majority of users, there might be some builds out there that preferred the old default. If you happen to prefer the old default (`1.6`) please configure that explicitly in your build via [idea.project.jdkName](dsl/org.gradle.plugins.ide.idea.model.IdeaProject.html#org.gradle.plugins.ide.idea.model.IdeaProject:jdkName)

##### `AbstractTask.getDynamicObjectHelper()`

Deprecated internal method `AbstractTask.getDynamicObjectHelper()` has been removed.

## Fixed Issues

The list of issues fixed between 1.0 and 1.1 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.1-rc-1%22%29+ORDER+BY+priority&tempMax=1000).
