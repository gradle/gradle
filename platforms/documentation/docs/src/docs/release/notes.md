<meta property="og:image" content="https://gradle.org/images/releases/gradle-@version@.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="TO DO">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="TO DO">
<meta name="twitter:image" content="https://gradle.org/images/releases/gradle-@version@.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

This release features [1](), [2](), ... [n](), and more.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://blog.gradle.org/roadmap-announcement) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [Wrapper](userguide/gradle_wrapper.html) in your project:

```
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for plugin authors and build engineers to develop custom build logic.

#### Configurations are initialized lazily

Similar to [tasks](userguide/lazy_configuration.html), [configurations](userguide/declaring_configurations.html) are now realized only when necessary.

Starting with this release, applying the `base` plugin (or any plugin that applies it, such as JVM plugins) no longer realizes all configurations declared using `register` or the incubating role-based factory methods.

This change can reduce configuration time and memory usage in some builds.

To leverage these improvements, declare configurations using the `register` method instead of the `create` method:

```kotlin
configurations {
    // Instead of using `create`
    create("myEagerConfiguration")
    
    // Use `register` to avoid realizing the configuration
    register("myLazyConfiguration")
}
```

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the use case or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details

To embed videos, use the macros below. 
You can extract the URL from YouTube by clicking the "Share" button. 
For Wistia, contact Gradle's Video Team.
@youtube(Summary,6aRM8lAYyUA?si=qeXDSX8_8hpVmH01)@
@wistia(Summary,a5izazvgit)@

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv -->

<a name="java-24"></a>
### Support for building projects with Java 24

Gradle now supports [Java 24](userguide/compatibility.html#java) for compiling, testing, and starting Java programs.
You can select the Java language version using [toolchains](userguide/toolchains.html).

However, Gradle itself cannot run on Java 24 yet, as Groovy does not fully support JDK 24.
Future versions are expected to address this.

<a name="Problems-api-additional-data"></a>

### Expanded support for arbitrary data in the Problems API

In Gradle 8.13, we introduced `additional data` support in the public-facing [Problems API](https://github.com/gradle/gradle/pull/32664/javadoc/org/gradle/api/problems/package-summary.html), allowing users to attach extra context to a problem with some limitations.
In this release, those limitations have been removed, enabling the inclusion of `arbitrary data` in a problem.

This enhancement is particularly useful for IDE implementors who manage both the plugin code and its integration via the [Tooling API](https://github.com/gradle/gradle/pull/32664/userguide/tooling_api.html#embedding).

By leveraging this feature, you can provide richer context for problems that are not fully captured by the existing properties.

For example, a worker task may now include more detailed diagnostic information:

```java
public abstract class ProblemWorkerTask implements WorkAction<ProblemsWorkerTaskParameter> {

    // Use the Problems interface to report problems
    @Inject
    public abstract Problems getProblems();

    // Use the ObjectFactory to create instances of classes for composition
    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Override
    public void execute() {
        ProblemId problemId = ProblemId.create("type", "label", ProblemGroup.create("generic", "Generic"));
        getProblems().getReporter().report(problemId, problem -> problem
            .additionalData(SomeData.class, dataInstance -> {
                dataInstance.getSome().set("some"); // Provider API properties can be used
                dataInstance.setName("someData"); // Getters and setters can be used
                dataInstance.setNames(Collections.singletonList("someMoreData")); // Collections can be used
                SomeOtherData compositionDataInstance = getObjectFactory().newInstance(SomeOtherData.class);
                compositionDataInstance.setOtherName("otherName");
                dataInstance.setOtherData(compositionDataInstance); // Composition can be used
            })
        );
    }
}
```

The data interfaces for this example look as follows:

```java
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.provider.Property;

import java.util.List;

public interface SomeData extends AdditionalData {
    Property<String> getSome();

    String getName();

    void setName(String name);

    List<String> getNames();

    void setNames(List<String> names);

    SomeOtherData getOtherData();

    void setOtherData(SomeOtherData otherData);
}

public interface SomeOtherData {
    String getOtherName();

    void setOtherName(String name);
}
```

#### Receiving side on the Tooling API

The [`CustomAdditionalData.get()`](org/gradle/tooling/events/problems/CustomAdditionalData.html#get(java.lang.Class)) method allows you to retrieve additional data using a specified view type.

On the TAPI side, retrieving and using the additional data might look like this:

```java
void someMethod(List<Problem> problems) {
    SomeDataView view = problems.get(0).getAdditionalData().get(SomeDataView.class);
    System.out.println(view.getName());
    System.out.println(view.getNames().get(0));
    System.out.println(view.getOtherData().getOtherName());
}
```

The retrieved data can be structured using view interfaces like the following:

```java
interface SomeOtherDataView {
    String getOtherName();
}

interface SomeDataView {
    String getSome();

    String getName();

    List<String> getNames();

    SomeOtherDataView getOtherData();
}
```

### GraalVM Native Image selection for toolchains

Gradle's [toolchain support](userguide/toolchains.html) allows provisioning and selection of JDK versions required for building projects (compiling code, running tests, etc) and running Gradle itself.

With this release, the selection criteria have been expanded to include [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/) capability selection:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        nativeImageCapable = true
    }
}
```

See [the documentation](userguide/toolchains.html#sec:native_image) for details.

Note that this feature is available as well for [the daemon toolchain](userguide/gradle_daemon.html#sec:native_image).

### Configuration Cache improvements

#### Integrity Check mode

When facing cryptic configuration cache loading failures, you can now enable stricter integrity checks with `org.gradle.configuration-cache.integrity-check` property.
It can help to pinpoint the piece of code that failed to serialize properly with more precision. You can get from:
```
Index 4 out of bounds for length 3
```
to:
```
Configuration cache state could not be cached: field `user` of task `:greet` of type `GreetTask`: The value cannot be decoded properly with 'JavaObjectSerializationCodec'. It may have been written incorrectly or its data is corrupted.
```
Enabling the checks inflates the configuration cache entry and slows down storing and loading it, so it should be used as a troubleshooting tool and doesn't need to be permanently enabled.

<!-- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backward compatibility.
See the User Manual section on the "[Feature Lifecycle](userguide/feature_lifecycle.html)" for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

<!--
This section will be populated automatically
-->

## Known issues

Known issues are problems that were discovered post-release that are directly related to changes made in this release.

<!--
This section will be populated automatically
-->

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure if you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
