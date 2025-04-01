<meta property="og:image" content="https://gradle.org/images/releases/gradle-@version@.png" />
<meta property="og:type"  content="article" />
<meta property="og:title" content="Gradle @version@ Release Notes" />
<meta property="og:site_name" content="Gradle Release Notes">
<meta property="og:description" content="Gradle now supports Java 24, improves build performance with lazy configuration and configuration cache diagnostics, and expands the Problems API with support for structured data and GraalVM Native Image toolchain selection.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:site" content="@gradle">
<meta name="twitter:creator" content="@gradle">
<meta name="twitter:title" content="Gradle @version@ Release Notes">
<meta name="twitter:description" content="Gradle now supports Java 24, improves build performance with lazy configuration and configuration cache diagnostics, and expands the Problems API with support for structured data and GraalVM Native Image toolchain selection.">
<meta name="twitter:image" content="https://gradle.org/images/releases/gradle-@version@.png">

We are excited to announce Gradle @version@ (released [@releaseDate@](https://gradle.org/releases/)).

Gradle now supports [Java 24](#java-24).

This release adds support for selecting GraalVM Native Image toolchains, and introduces [lazy configuration initialization](#build-authoring) to improve configuration performance and memory usage.

It also expands the [Problems API](#build-authoring) to support arbitrary structured data, making it easier for IDEs to consume rich diagnostics through the Tooling API.

Additionally, this release includes enhancements to the [configuration cache](#configuration-cache), including a new integrity check mode for improved debugging.

<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)

 THIS LIST SHOULD BE ALPHABETIZED BY [PERSON NAME] - the docs:updateContributorsInReleaseNotes task will enforce this ordering, which is case-insensitive.
-->

We would like to thank the following community members for their contributions to this release of Gradle:

Be sure to check out the [public roadmap](https://roadmap.gradle.org) for insight into what's planned for future releases.

## Upgrade instructions

Switch your build to use Gradle @version@ by updating the [Wrapper](userguide/gradle_wrapper.html) in your project:

```text
./gradlew wrapper --gradle-version=@version@ && ./gradlew wrapper
```

See the [Gradle 8.x upgrade guide](userguide/upgrading_version_8.html#changes_@baseVersion@) to learn about deprecations, breaking changes, and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin, and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).   

## New features and usability improvements

<a name="java-24"></a>
### Support for building projects with Java 24

Gradle now supports [using Java 24](userguide/compatibility.html#java) for compiling, testing, and starting Java programs.
Selecting a language version is done using [toolchains](userguide/toolchains.html).

You cannot run Gradle @version@ itself with Java 24 because Groovy does not fully support JDK 24.
However, future versions are expected to provide this support.

### GraalVM Native Image selection for toolchains

Gradle's [toolchain support](userguide/toolchains.html) allows provisioning and selection of specific JDK versions for building projects—compiling code, running tests, and even running Gradle itself.

With this release, toolchain selection has been expanded to support [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/) capability:

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        nativeImageCapable = true
    }
}
```

This allows Gradle to select only JDKs that support Native Image when resolving a toolchain.
See the [toolchain documentation](userguide/toolchains.html#sec:native_image) for more details.

Note: Native Image capability selection is also supported for the [daemon toolchain](userguide/gradle_daemon.html#sec:native_image).

<a name="build-authoring"></a>
### Build authoring improvements

Gradle provides [rich APIs](userguide/getting_started_dev.html) for plugin authors and build engineers to develop custom build logic.

#### Configurations are initialized lazily

Just like [tasks](userguide/lazy_configuration.html), [configurations](userguide/declaring_configurations.html) are now realized only when necessary.

Starting with this release, applying the `base` plugin—either directly or via another plugin such as the Java or Kotlin plugin—no longer realizes all configurations declared with `register` or the incubating role-based factory methods.

This change can lead to reduced configuration time and lower memory usage in some builds.

To take advantage of this improvement, prefer using the `register` method over `create` when declaring configurations:

```kotlin
configurations {
    // Eager: this configuration is realized immediately
    create("myEagerConfiguration")

    // Lazy: this configuration is only realized when needed
    register("myLazyConfiguration")
}
```

#### Expanded support for Arbitrary Data in the Problems API

In Gradle 8.13, we introduced support for `additional data` in the public [Problems API](https://github.com/gradle/gradle/pull/32664/javadoc/org/gradle/api/problems/package-summary.html), allowing users to attach extra context to reported problems—albeit with some limitations.

In this release, those limitations have been removed. You can now include `arbitrary data` in problem reports, offering significantly greater flexibility.

This enhancement is especially valuable for IDE implementors managing both the plugin and its integration via the [Tooling API](https://github.com/gradle/gradle/pull/32664/userguide/tooling_api.html#embedding), where conveying rich, structured diagnostics is critical.

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
                dataInstance.getSome().set("some");                                 // Provider API properties can be used
                dataInstance.setName("someData");                                   // Getters and setters can be used
                dataInstance.setNames(Collections.singletonList("someMoreData"));   // Collections can be used
                
                SomeOtherData compositionDataInstance = getObjectFactory().newInstance(SomeOtherData.class);
                compositionDataInstance.setOtherName("otherName");
                
                dataInstance.setOtherData(compositionDataInstance);                 // Composition can be used
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

With this enhancement, the Problems API becomes a more powerful diagnostic tool—capable of carrying rich, structured, and typed context through the build, IDE, and Tooling API layers.

#### Receiving Additional Data via the Tooling API

The [`CustomAdditionalData.get()`](org/gradle/tooling/events/problems/CustomAdditionalData.html#get(java.lang.Class)) method allows consumers on the Tooling API (TAPI) side to retrieve additional data using a typed view interface.

On the receiving side, you can access the data like this:

```java
void someMethod(List<Problem> problems) {
    SomeDataView view = problems.get(0).getAdditionalData().get(SomeDataView.class);
    
    System.out.println(view.getName());
    System.out.println(view.getNames().get(0));
    System.out.println(view.getOtherData().getOtherName());
}
```

The data is exposed through view interfaces that mirror the structure of the data reported by the build logic:

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

These view types provide a safe, structured way to consume custom data in IDEs or other TAPI-based tooling.

<a name="configuration-cache"></a>
### Configuration cache improvements

The [configuration cache](userguide/configuration_cache.html) improves build time by caching the result of the configuration phase and reusing it for subsequent builds.
This feature can significantly improve build performance.

#### Integrity Check mode

To help diagnose obscure configuration cache loading errors, you can now enable stricter integrity checks using the `org.gradle.configuration-cache.integrity-check` property.

This mode provides more detailed error messages to pinpoint the exact part of your build that failed to serialize correctly.

For example, instead of seeing a cryptic error like:

```text
Index 4 out of bounds for length 3
```

You might now see:

```text
Configuration cache state could not be cached: field `user` of task `:greet` of type `GreetTask`: The value cannot be decoded properly with 'JavaObjectSerializationCodec'. It may have been written incorrectly or its data is corrupted.
```

Note: Enabling integrity checks increases the size of the configuration cache and slows down cache reads/writes.
Use it only for troubleshooting—not in regular builds.

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
