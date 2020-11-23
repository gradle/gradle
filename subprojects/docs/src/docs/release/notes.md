The Gradle team is excited to announce Gradle @version@.

This release addresses some [performance problems with Kotlin DSL build scripts](#kotlin-dsl-performance), introduces new [dependency management APIs for consistent resolution](#dm-features), and adds some [improvements to Java toolchain selection](#java-toolchain-improvements). 
The [experimental configuration cache](#configuration-cache) has added support for composite builds and more core plugins shipped with Gradle.

This release also fixes some long-standing issues with composite builds. Gradle now [allows you to execute tasks from included builds directly from the command-line](#composite-builds). Several other smaller [usability issues](#other-usability) were added in this release.

We don't expect many builds to be affected, but this release [disables outdated TLS v1.0 and v1.1 protocols](#security-tls) to improve the security of builds resolving dependencies from external repositories. 

We would like to thank the following community contributors to this release of Gradle:

[Marcono1234](https://github.com/Marcono1234),
[Björn Sundahl](https://github.com/Ranzdo),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Danny Thomas](https://github.com/DanielThomas),
[Jeff](https://github.com/mathjeff),
[Mattia Tommasone](https://github.com/Raibaz),
[jdai8](https://github.com/jdai8),
[David Burström](https://github.com/davidburstrom),
[Björn Kautler](https://github.com/Vampire),
[Stefan Oehme](https://github.com/oehme),
[Thad House](https://github.com/ThadHouse),
[knittl](https://github.com/knittl),
[Gregorios Leach](https://github.com/simtel12).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<a name="kotlin-dsl-performance"></a>
## Performance improvements

### Kotlin DSL script compilation improvements

This release makes compilation of [Gradle Kotlin DSL](userguide/kotlin_dsl.html) scripts (`*.gradle.kts`) faster and easier on memory, and introduces compilation avoidance that can eliminate the need to recompile Kotlin build scripts altogether.

On a sample build with 100 subprojects, the cumulative script compilation time goes from [~50 seconds](https://scans.gradle.com/s/3bg67eccnya5i/performance/configuration?showScriptCompilationTimes) down to [~21 seconds](https://scans.gradle.com/s/y7dw5ekes24ag/performance/configuration?showScriptCompilationTimes) with cold caches and cold daemons.
Garbage collection time goes from [2.6 seconds](https://scans.gradle.com/s/3bg67eccnya5i/performance/build#garbage-collection) down to [1.3 seconds](https://scans.gradle.com/s/y7dw5ekes24ag/performance/build#garbage-collection). 
This improvement also reduces memory pressure.
On top of that, a non-ABI change can [eliminate build script recompilation altogether now](https://scans.gradle.com/s/exxa2y22shld6/performance/configuration#summary-script-compile), saving those 21 seconds.

<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAvIAAAHTCAYAAABFg/xCAAA3VklEQVR42u3dCZjV5Z3ge7Vj1u4kk5h0cqczPbcn3Z2+3XeSSWZ6OncyT3drgnvc2wSj19suUYNGE4mALEaQEBWEIFEkuKAggqCoONggsoNsIrsga7EWKDtIFfBefm9yzpwqagMFTsnn8zy/R+tsderUeU9961//8+ekBAAANDsneQgAAEDIAwAAQh4AABDyAAAg5AEAACEPAAAIeQAAEPIAAICQBwAAhDwAAAh5AABAyAMAAEIeAAAQ8gAAIOQBAAAhDwAACHkAABDyAACAkAcAAIQ8AAAIeQAAQMgDAABCHgAAhDwAACDkAQAAIX8UzZ8/P/Xq1St16NChxunLly9P3bt3T+3bt0/9+vVL27Zt82ABACDkyyXi77nnnrRgwYK0d+/e4un79+9PnTt3TrNnz0579uxJQ4cOTYMGDfKMAQBAyJeDHj16pKVLlx5yekVFReratWvx48rKytSxY0fPGAAAhPzxtnPnztSmTZv01FNP5d1qIurXrFmTz1u4cGHq3bt38bKxtb5169apqqrKswYAACF/PEW0R5xPnjw5h/rYsWPTfffdlw4cOJDmzp2b+vTpU7xsdXV1vuzu3bvrvb1169almTNnGmOMMcYYk9tQyB8lsftM7B9fEPvFt23bNm3durXeLfIR9AAAYIv8cRS71kS4F+K8EPI7duxIa9euTV26dCleduPGjalTp06eMQAACPly0Ldv3/TSSy/lI9PErjWxn3wh6uPNrtOnT8/nDRkyJA0ePNgzBgAAIV8OYjeaRx55JN15553pwQcfzFveC1auXJnDvnAc+dhSDwAAQh4AABDyAAAg5AEAACEPAAAIeQAAEPIAAICQBwAAhDwAACDkAQBAyAMAAEIeAAAQ8gAAIOQBAAAhDwAACHkAABDyAACAkIdm4/bbb0+nn356jamsrMznvfrqq+mqq65KZ599drrhhhvSvHnz6r2dGTNmpFtvvTWde+65afny5cXThw4dmi6//PJ8GzfffHNavHhx8byZM2ema665Jp930003pbfeess3BAAQ8tAUV199dbr++uvTCy+8UJw9e/akhQsXpjPOOCPddttt6bnnnksXXXRRuvjii9P+/fsPuY3XXnstX/baa69NAwcOTNu3b8+nT506Nf9i0KZN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" alt="Gradle Kotlin DSL script compilation performance improvements"/> 

Until now, any change to build logic in [buildSrc](userguide/organizing_gradle_projects.html#sec:build_sources) required all of the build scripts to be recompiled.
This release introduces compilation avoidance for [Gradle Kotlin DSL](userguide/kotlin_dsl.html) scripts.

Compilation avoidance will cause Gradle to only recompile build scripts when a change to shared build logic impacts the
ABI (application binary interface) of the classpath of the build script.
Changes to private implementation details of build logic, such as private methods or classes,
bodies of non-private methods or classes, as well as internal changes to [precompiled script plugins](userguide/custom_plugins.html#sec:precompiled_plugins),
will no longer trigger recompilation of the project's build scripts.

Compilation avoidance also applies to changes to any jar on the build script's classpath added by a plugin, such as from an included build or added directly via `buildscript {}` block.

While the impact on your build may vary, most builds can expect a noticeably shorter feedback loop when editing Kotlin DSL build logic.

**Note**: Kotlin's public [inline functions](https://kotlinlang.org/docs/reference/inline-functions.html#inline-functions) are not supported with compilation avoidance. 
If such functions appear in the public API of a jar on the buildscript's classpath, changes to classes in that jar will cause Gradle to fallback to its old behavior.
For example, if `buildSrc` contains a class with a public inline function, then any change to a class in `buildSrc` will cause all build scripts to be recompiled.

### More cache hits when normalizing runtime classpaths

For [up-to-date checks](userguide/more_about_tasks.html#sec:up_to_date_checks) and the [build cache](userguide/build_cache.html), Gradle needs to determine if two task input properties have the same value. 
In order to do so, Gradle first [normalizes](userguide/more_about_tasks.html#sec:configure_input_normalization) both inputs and then compares the result.

Runtime classpath analysis now smartly inspects all properties files, ignoring changes to comments, whitespace, and differences in property order.  Moreover, you can selectively ignore properties that don't impact the runtime classpath.

```
normalization {
    properties('**/build-info.properties') {
        ignoreProperty('timestamp')
    }
}
```

This improves the likelihood of up-to-date and build cache hits when any properties file on the classpath is regenerated or only differs by unimportant values.

See [the userguide](userguide/more_about_tasks.html#sec:property_file_normalization) for further information.

<a name="configuration-cache"></a>
### Configuration cache improvements

[//]: # (TODO context and overview of improvements)

#### Support for composite builds

Starting with this release, [composite builds](userguide/composite_builds.html) are fully supported with the configuration cache.

#### More supported core plugins

In this release a number of core Gradle plugins got improved to support the configuration cache:

* [`checkstyle`](userguide/checkstyle_plugin.html)
* [`pmd`](userguide/pmd_plugin.html)
* [`codenarc`](userguide/codenarc_plugin.html)
* [`jacoco`](userguide/jacoco_plugin.html)

See the [matrix of supported core plugins](userguide/configuration_cache.html#config_cache:plugins:core) in the user manual.

<a name="dm-features"></a>
## Consistent dependency resolution features

### Central declaration of repositories

Traditionally in a Gradle build, repositories used for dependency resolution are declared in every project.
However, in most cases, the same repositories should be used in every project of a single build.
This led to the common pattern of using an `allprojects { ... }` block to declare the repositories.
In Gradle 6.8, this pattern can be replaced with a conventional block in `settings.gradle(.kts)`:

```
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

There are several advantages in using this new construct instead of using `allprojects` or repeating the declaration in every build script.

Learn more by reading how to [declare repositories for the whole build](userguide/declaring_repositories.html#sub:centralized-repository-declaration).

### Central declaration of component metadata rules

[Component metadata rules](userguide/component_metadata_rules.html) are a powerful tool to fix bad metadata published on remote repositories.
However, similarly to repositories, rules traditionnally had to be applied on each project.
Starting from this release, it is possible to declare component metadata rules at a central place in `settings.gradle(.kts)`:

```
dependencyResolutionManagement {
    components {
        withModule('com.google.guava:guava', GuavaRule)
    }
}
```

You can learn more about declaring rules globally in the [user manual](userguide/component_metadata_rules.html#sec:rules_in_settings).

### Consistent dependency resolution

It's a common problem that the dependencies resolved for the runtime have different versions than the dependencies resolved for compile time.
This typically happens when a transitive dependency that is only present at runtime brings in a higher version of a first level dependency.

To mitigate this problem, Gradle now introduces an API which lets you declare consistency between dependency configurations.
For example, in the Java ecosystem, you can write:

```
java {
    consistentResolution {
        useCompileClasspathVersions()
    }
}
```

which tells Gradle that the common dependencies between the runtime classpath and the compile classpath should be aligned to the versions used at compile time.

There are many options to configure this feature which are described in the [user manual](userguide/resolution_strategy_tuning.html#resolution_consistency).

<a name="composite-builds"></a>
## Changes to composite builds

### Tasks can be executed from the command-line

Gradle now allows users to execute tasks from included builds directly from the command-line. For example, if your build includes `my-other-project` as an included build and it has a subproject `sub` with a task `foo`, then you can execute `foo` with the following command:

    gradle :my-other-project:sub:foo

Note, unlike a multi-project build, running `gradle build` will _not_ run the `build` task in all of the included builds.
You could introduce [task dependencies](https://docs.gradle.org/current/userguide/composite_builds.html#included_build_task_dependencies) to tasks in included builds if you wanted to recreate this behavior for included builds.

<a name="java-toolchain-improvements"></a>
## Improved Java toolchain selection 

[Java toolchain support](userguide/toolchains.html) provides an easy way to declare what Java version the project should be built with.
By default, Gradle will [auto-detect installed JDKs](userguide/toolchains.html#sec:auto_detection) that can be used as toolchain.

### Selecting toolchain by vendor and implementation

In case your build has specific requirements from the used JRE/JDK, you may want to define the vendor for the toolchain as well.
`JvmVendorSpec` has a list of well-known JVM vendors recognized by Gradle. The advantage is that Gradle can handle any inconsistencies across JDK versions
in how exactly the JVM encodes the vendor information.

```
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTOPENJDK

        // alternativly, use custom matching
        // vendor = JvmVendorSpec.matching("customString")
    }
}
```

If the vendor is not enough to select the appropriate toolchain, you may as well filter by the implementation of the virtual machine.
For example, to use an [Open J9](https://www.eclipse.org/openj9/) JVM, distributed via [AdoptOpenJDK](https://adoptopenjdk.net/), you can filter by the implementation as shown in the example below. 

```
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTOPENJDK
        implementation = JvmImplementation.J9
    }
}
```

Please refer to [the documentation](userguide/toolchains.html#using_toolchains_by_specific_vendors) for more detailed information.

### Viewing all available toolchains

In order to see which toolchains got detected and their corresponding metadata, Gradle 6.8 now provides some insight with the `javaToolchains` task.

Output of `gradle -q javaToolchains`:
```
 + Options
     | Auto-detection:     Enabled
     | Auto-download:      Enabled

 + AdoptOpenJDK 1.8.0_242
     | Location:           /path/to/8.0.242.hs-adpt/jre
     | Language Version:   8
     | Vendor:             AdoptOpenJDK
     | Is JDK:             true
     | Detected by:        SDKMAN!

 + OpenJDK 15-ea
     | Location:           /path/to/java/15.ea.21-open
     | Language Version:   15
     | Vendor:             AdoptOpenJDK
     | Is JDK:             true
     | Detected by:        SDKMAN!

 + Oracle JDK 1.7.0_80
     | Location:           /Library/Java/jdk1.7.0_80.jdk/jre
     | Language Version:   7
     | Vendor:             Oracle
     | Is JDK:             true
     | Detected by:        macOS java_home
```

This can help to debug which toolchains are available to the build and if the expected toolchain got detected or [requires manual setup](userguide/toolchains.html#sec:custom_loc).
See the [toolchain documentation](userguide/toolchains.html) for more in-depth information on toolchain detection and usage.

<a name="other-usability"></a>
## Other usability improvements

### Implicit imports

When using [dependency injection](userguide/custom_gradle_types.html#service_injection) when developing plugins, tasks or project extensions, it is now possible to use the `@Inject` annotation without explicitly importing it into your build scripts the
same way it works for other Gradle API classes.

### Test re-run JUnit XML reporting enhancements

The `Test` task, used for executing JVM tests, reports test results as HTML and as a set of XML files in the “JUnit XML” pseudo standard.
It is common for CI servers and other tooling to observe test results via the XML files.
A new `mergeReruns` option has been added that changes how tests that are executed more than once are reported in the XML files.

```
test {
    reports.junitXml.mergeReruns = true
}
```

When this new option is enabled, if a test fails but is then retried and succeeds, its failures will be recorded as `<flakyFailure>` instead of `<failure>`, within one `<testcase>`.
This is the same as the reporting produced by the [surefire plugin of Apache Maven™](https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html), when enabling reruns.
If your CI server understands this format, it will indicate that the test was flaky.

This option is disabled by default, causing each test execution to be listed as a separate `<testcase>` in the XML.
This means that when a test is executed multiple times, due to a retry-on-failure mechanism for example, it is listed multiple times.
This is also the behavior for all previous Gradle versions.

If you are using [build scans](https://scans.gradle.com) or [Gradle Enterprise](https://gradle.com/gradle-enterprise-solution-overview/failure-analytics/), flaky tests will be detected regardless of this setting.

Learn more about this new feature in the [Java testing documentation](userguide/java_testing.html#communicating_test_results_to_CI_servers_and_other_tools_via_xml_files).

### Importing projects with custom source sets into Eclipse

This version of Gradle fixes problems with projects that use custom source sets, like additional functional test source sets.

Custom source sets are now imported into Eclipse automatically and no longer require manual configuration in the build.

<a name="security-tls"></a>
## Outdated TLS versions are no longer enabled by default

This version of Gradle removes TLS protocols v1.0 and v1.1 from the default list of allowed protocols. Gradle will no longer fallback to TLS v1.0 or v1.1 by default when resolving dependencies. Only TLS v1.2 or TLS v1.3 are allowed by default.

These TLS versions can be re-enabled by manually specifying the system property `https.protocols` with
a comma separated list of protocols required by your build.

The vast majority of builds should not need to change in any way. [Maven Central](https://central.sonatype.org/articles/2018/May/04/discontinued-support-for-tlsv11-and-below/) and [JCenter/Bintray](https://jfrog.com/knowledge-base/why-am-i-failing-to-work-with-jfrog-cloud-services-with-tls-1-0-1-1/) dropped support for TLS v1.0 and TLS v1.1 two years ago. Java has had TLS v1.2+ available for several years. Disabling these protocols in Gradle protects builds from downgrade attacks.

Depending on the version of Java you use, Gradle will negotiate TLS v1.2 or TLS v1.3 when communicating with remote repositories.

**Note**: Early versions of JDK 11 & JDK 12 contained [race condition bug in the `TLSv1.3` handling logic](https://bugs.openjdk.java.net/browse/JDK-8213202)
which causes the exception `javax.net.ssl.SSLException: No PSK available. Unable to resume`. If you run into this issue,
we recommend updating to the latest minor JDK version.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->
## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
