For Gradle 1.1 our focus has been on usability improvements, bug fixes, and ground work to support the post 1.0 evolution of Gradle. 

We focussed on improving Maven integration, dependency management and OSGi support, while also knocking off a few other known issues. For more information, check the [full list of resolved tickets](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.1-rc-1%22%2C+%221.1-rc-2%22%29+ORDER+BY+priority&tempMax=1000). We did also manage to add some nice new features, outlined below.

You might be interested in our [recent posting](http://forums.gradle.org/gradle/topics/the_gradle_release_approach) of what you can expect from us regarding release frequency, backwards compatibility and our deprecation policy. We have also written a [quick outlook on the upcoming 1.2 release](http://forums.gradle.org/gradle/topics/gradle_1_2_release_outlook?rfm=1).

## New and noteworthy

### Test Logging

Gradle 1.1 provides much more detailed information during test execution, right on the console. We've worked hard to make the the new output useful and informative out of the box, but we've also given you the ability to finely tune it to your liking.

The old output:

<pre><tt>:spring-integration-twitter:test
Test o.s.i.t.i.MessageSourceTests FAILED
4 tests completed, 1 failure
</tt>
</pre>

The improved output:

<pre><tt>:spring-integration-twitter:test
o.s.i.t.i.MessageSourceTests > testSearchReceivingMessageSourceInit  FAILED
    j.f.AssertionFailedError at MessageSourceTests.java:96

4 tests completed, 1 failed, 1 skipped
</tt>
</pre>

#### Show Exceptions

One of the most useful options is to show the exceptions thrown by failed tests. By default, Gradle will log a succinct message for every test exception. To get more detailed output, configure the `exceptionFormat`:

    test {
        testLogging {
            exceptionFormat "full"
        }
    }

Which would produce output like:

<pre><tt>o.s.i.t.i.MessageSourceTests > testSearchReceivingMessageSourceInit FAILED
    j.f.AssertionFailedError: null
        at j.f.Assert.fail(Assert.java:47)
        at j.f.Assert.assertTrue(Assert.java:20)
        at j.f.Assert.assertTrue(Assert.java:27)
        at o.s.i.t.i.MessageSourceTests.testSearchReceivingMessageSourceInit(MessageSourceTests.java:96)

4 tests completed, 1 failed, 1 skipped
</tt>
</pre>

#### Stack Trace Filters

Stack traces of test exceptions are automatically truncated not to show anything below the entry point into the test code. This filters out Gradle internals and internals of the test framework. A number of other filters are available. For example, when dealing with Groovy code it makes sense to add the `groovy` filter:

    test {
        testLogging {
            stackTraceFilters "truncate", "groovy"
        }
    }

While would produce output like:

<pre><tt>o.s.i.t.i.MessageSourceTests > testSearchReceivingMessageSourceInit FAILED
    o.s.i.MessageDeliveryException: Failed to send tweet 'Liking the new Gradle test logging output!'
        at o.s.i.t.i.SearchReceivingMessageSource.&lt;init&gt;(SearchReceivingMessageSource.java:42)
        at o.s.i.t.i.MessageSourceTests.testSearchReceivingMessageSourceInit(MessageSourceTests.java:81)
        Caused by:
        o.s.i.MessageSourceException: Oops! Looks like Twitter is down. Try again shortly.
            at o.s.i.c.IntegrationObjectSupport.onInit(IntegrationObjectSupport.java:113)
            at o.s.i.t.i.AbstractTwitterMessageSource.onInit(AbstractTwitterMessageSource.java:92)
            at o.s.i.t.i.SearchReceivingMessageSource.&lt;init&gt;(SearchReceivingMessageSource.java:40)
            ... 1 more
</tt>
</pre>

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

For further details, see the [forum announcement](http://forums.gradle.org/gradle/topics/whats_new_in_gradle_1_1_test_logging) for this feature.

### Easier opening of test and code quality reports

When a test or code quality task has found a problem, it typically prints a message which includes the file path of the generated report. This message has been
slightly changed to print the path as a file URL. Smart consoles recognize such URLs and make it easy to open them. For example, in Mac's Terminal.app reports are now
just a CMD + double click away.

### Tooling API provides Gradle module information for external dependencies

The Tooling API can be used to obtain the model of the project which includes the information about the dependencies of the project. In Gradle 1.1, the Tooling API also provides Gradle
module information, i.e. group, name, version of each dependency.

Please see the javadoc for [GradleModuleVersion](javadoc/org/gradle/tooling/model/GradleModuleVersion.html). You can obtain the Gradle module information via [ExternalDependency.getGradleModuleVersion()](javadoc/org/gradle/tooling/model/ExternalDependency.html#getGradleModuleVersion\(\)).

This feature helps the IDE integration like Gradle STS plugin to support
more flexible developer workspace and hence faster dev cycles.
Using Eclipse terms: it is a first step into providing the ability to toggle between
Eclipse 'project' dependency and a regular 'binary' dependency.      

### Global Maven settings.xml

When migrating from Maven you can reuse the artifacts from your local Maven repository in your Gradle build. Gradle now honours the Maven settings located in <code><em>$M2_HOME</em>/conf/settings.xml</code> to locate the local Maven repository. If a local repository is defined in `~/.m2/settings.xml`, this location takes precedence over a repository definition in <code><em>$M2_HOME</em>/conf/settings.xml</code>. This is used when you have declared the `mavenLocal()` repository definition in your build script and in general when checking for locally available resources before downloading them again.

### Publishing SHA1 checksums to Ivy repositories

Gradle will now automatically generate and publish SHA1 checksum files when publishing to an Ivy repository. For each file `foo.ext` published, Gradle will also publish a checksum file with the name `foo.ext.sha1`. 

The presence of SHA1 checksums in the dependency repository allows Gradle to be more efficient when resolving dependencies. Recently Gradle gained the ability to use the checksum of a remote file to determine whether or not it truly needs to be downloaded. If Gradle can find a file with an identical checksum to the target remote file, it will use that instead of downloading the remote file. Also, for “changing” dependencies (e.g. snapshots) Gradle can compare the remote checksum with the checksum of the local file to avoid downloading unchanged dependencies.

Checksums have always been published to Maven repositories. This new feature of publishing checksums to Ivy repositories unlocks the recent Gradle dependency downloading optimizations to Ivy users. There is no change required to your build script to enable this functionality.

### Dependency resolution supports HTTP Digest Authentication

Due to the way Gradle used pre-emptive HTTP Authentication, Gradle 1.0 was not able to handle a repository secured with HTTP Digest Authentication. This issue has been resolved by using the following new strategy:

* Any GET/HEAD request issued by Gradle will no longer contain pre-emptive HTTP Authentication headers.
* An initial PUT/POST request will contain Basic Authentication headers for pre-emptive HTTP Authentication.
    * If the server requires HTTP Basic Authentication, then this request will succeed automatically
    * If the server requires HTTP Digest Authentication, then this request will fail with a 401, and we will re-send the request with the correct headers.
* After the initial PUT/POST request, subsequent requests to the repository will have correct Auth headers, and will not require re-send.

## Upgrading from Gradle 1.0

Please let us know if you encounter any issues during the upgrade to Gradle 1.1, that are not listed below.

### Deprecations

#### Statement Labels

As in Java, statement labels are rarely used in Groovy. The following example shows a frequent pitfall where a statement label is erroneously used in an attempt to configure an object:

    task foo {
        dependsOn: bar
    }

Whereas what the author actually intended was:

    task foo {
        dependsOn bar
    }

Note the colon after `dependsOn` in the first code block. This extra colon causes the line to be interpreted as a statement label (a Java/Groovy language feature), which effectively makes it a non operation. Statement labels are not useful in Gradle build scripts.

To prevent such mistakes that are hard to track down and debug, the usage of statement labels in build scripts has been deprecated and Gradle will issue a deprecation warning when they are used.
In Gradle 2.0, statement labels in build scripts will no longer be supported and will cause an error.

#### M2_HOME system property

Previously, Gradle looked for a JVM system property named `M2_HOME` for the location of a custom Maven home directory. This has been deprecated in favor of an environment variable, named `M2_HOME`, which is also used by other tools that integrate with Maven.
Support for the `M2_HOME` system property will be removed in Gradle 2.0.

#### Publication of missing artifacts

Previously, if an artifact to be published referred to a file that does not exist during publication, then Gradle would silently ignore the artifact to be published. This is only likely to occur when declaring [file artifacts](userguide/artifact_management.html#N143A6).

This behavior is now deprecated. Attempting to publish a non-existant artifact file will result in a deprecation warning, and will produce an error in Gradle 2.0.

#### DSL

##### `Project.fileTree(Object)` - Removal of incorrect `@deprecation` tag

The `Project.fileTree(Object)` method was incorrectly annotated with the `@deprecated` Javadoc tag in Gradle 1.0-milestone-8. This method has not been deprecated and the Javadoc tag has been removed.

##### `Project.fileTree(Closure)` - Addition of `@deprecation` tag

The `Project.fileTree(Closure)` method was deprecated in Gradle 1.0-milestone-8. The method was not annotated with the `@deprecated` javadoc tag at that time. This has been added for this release.
This method will be removed in Gradle 2.0.

#### API

##### `org.gradle.api.tasks.testing.TestLogging` - Moved into `logging` subpackage

The `org.gradle.api.tasks.testing.TestLogging` interface was moved into package `org.gradle.api.tasks.testing.logging` (and subsequently enhanced with new methods). For backwards compatibility reasons, the old interface was kept at its original location,
but is now deprecated. The old interface will be removed in Gradle 2.0.

### Potential breaking changes

##### `idea.project.jdkName`

We've decided to change the IDEA plugin's default JDK name. The new default is now smarter. Without this change, many users had to configure the JDK name explicitly in the builds or manually tweak the JDK name in IDEA after running the `gradle idea` task. The current default uses the Java version that Gradle runs with.

Although we believe the new default is much better for majority of users, there might be some builds out there that preferred the old default. If you happen to prefer the old default (`1.6`) please configure that explicitly in your build via [idea.project.jdkName](dsl/org.gradle.plugins.ide.idea.model.IdeaProject.html#org.gradle.plugins.ide.idea.model.IdeaProject:jdkName)

##### `AbstractTask.getDynamicObjectHelper()`

Deprecated internal method `AbstractTask.getDynamicObjectHelper()` has been removed.

## Fixed Issues

The list of issues fixed between 1.0 and 1.1 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.1-rc-1%22%2C+%221.1-rc-2%22%29+ORDER+BY+priority&tempMax=1000).
