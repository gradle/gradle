## New and noteworthy

Here are the new features introduced in this Gradle release.

### Support version ranges in parent elements

Gradle now suppoprts version range in parent elements of POM, which was introduced by [Maven 3.2.2](https://maven.apache.org/docs/3.2.2/release-notes.html):

> Parent elements can now use bounded ranges in the version specification. You can now consistently use ranges for all intra-project dependencies, of which parents are a special case but still considered a dependency of projects that inherit from them. The following is now permissible:

```
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.apache</groupId>
    <artifactId>apache</artifactId>
    <version>[3.0,4.0)</version>
  </parent>
  <groupId>org.apache.maven.its.mng2199</groupId>
  <artifactId>valid</artifactId>
  <version>1</version>
  <packaging>pom</packaging>
</project>
```


<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### Support for Maven optional dependencies

Gradle will now [take Maven optional dependencies into account](https://github.com/gradle/gradle/pull/3129) during dependency resolution. 
Before, optional dependencies were not included in the dependency graph.
As of Gradle 4.4, optional dependencies will participate in dependency resolution as soon as another dependency on the same module is found in the graph.
For example, if a transitive dependency on `foo:bar:1.1` is optional, but another path in the dependency graph brings `foo:bar:1.0` (not optional), then Gradle will resolve to `foo:bar:1.1`.
Previous releases would resolve to `foo:bar:1.0`. However, if no "hard" dependency is found on the optional module, then it will **not** be included, as previous Gradle versions did.

### Support for importing Maven Bill of Materials (BOMs)

Gradle now supports the import of BOMs by declaring them as dependencies. This allows declaring dependencies without versions and using a BOM to derive a set of compatible versions. 
For example, you can now setup a spring boot project like this:

```
dependencies {
    implementaion "org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE" //BOM import

    implementation "org.springframework.boot:spring-boot"
    implementation "org.springframework.boot:spring-boot-autoconfigure"

    testImplementation "junit:junit"
    testImplementation "org.springframework:spring-test"
    testImplementation "org.springframework.boot:spring-boot-test"
    testImplementation "org.springframework.boot:spring-boot-test-autoconfigure"
}
```

Published dependencies to BOMs are also honored.

### Eclipse plugin separates output folders

The `eclipse` plugin now defines separate output directories for each source folder. This ensures that main and test classes are compiled to different directories. 

The plugin also records which Eclipse classpath entries are needed for running classes from each source folder through the new `gradle_scope` and `gradle_used_by_scope` attributes. Future [Buildship](http://eclipse.org/buildship) versions will use this information to provide a more accurate classpath when launching applications and tests.

### Parametrized tooling model builders.

The Tooling API now allows model builders to accept parameters from the tooling client. This is useful when there are multiple possible mappings from the Gradle project to the tooling model and the decision depends on some user-provided value.
Android Studio for instance will use this API to request just the dependencies for the variant that the user currently selected in the UI. This will greatly reduce synchronization times.

For more information see the [documentation](javadoc/org/gradle/tooling/provider/model/ParametrizedToolingModelBuilder.html) of the new API.

### Security upgrade of third-party dependencies

This version includes several upgrade of third-party dependency packages to fix the following security issues:

- [CVE-2017-7525](http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2017-7525) (critical)
- SONATYPE-2017-0359 (critical)
- SONATYPE-2017-0355 (critical)
- SONATYPE-2017-0398 (critical)
- [CVE-2013-4002](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2013-4002) (critical)
- [CVE-2016-2510](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2016-2510) (severe)
- SONATYPE-2016-0397 (severe)
- [CVE-2009-2625](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2009-2625) (severe)
- SONATYPE-2017-0348 (severe)

This could avoid potential vulnerabilities.

### Visual Studio 2017 Support

It is now possible to compile native applications with the Visual C++ toolchain packaged with all versions of Visual Studio 2017.
With this release, Gradle will also begin discovering Visual Studio installations using the [vswhere utility](https://github.com/Microsoft/vswhere)
from Microsoft if it's available.  

### Embedded Ant version upgraded to Ant 1.9.9

Gradle now embeds [Ant 1.9.9](https://archive.apache.org/dist/ant/RELEASE-NOTES-1.9.9.html). Previous releases used Ant 1.9.6.

### Plugin repositories enhancements

Plugin repositories declared in a settings script can now have custom names:

```
// settings.gradle
pluginManagement {
    repositories {
        maven {
            name = "My Custom Plugin Repository"
            url = "https://..."
        }
    }
}
```

Explicit notation for common repositories can now be used in settings scripts:

```
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
}
```

Plugin resolution now takes all plugin repositories into account and can resolve transitive plugin dependencies across them. Before that change, it was required that all transitive dependencies of a given plugin were present in the same repository as the plugin. It's not anymore. 

The Gradle Plugin Portal repository can now be added to build scripts. This is particularly useful for `buildSrc` or binary plugin builds:

```
// build.gradle
repositories {
    gradlePluginPortal()
}
``` 

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Deprecation of no-rebuild command line options 

The command line options for avoiding a full rebuild of dependent projects in a multi-project builds (`-a`/`--no-rebuild`) were introduced in a very early version of Gradle. 
Since then Gradle optimized its up-to-date checking for project dependencies which renders the option obsolete. It has been deprecated and will be removed in Gradle 5.0.

<!--
### Example deprecation
-->

## Potential breaking changes

### Maven optional dependencies and BOM support may lead to different dependency resolution result

Supporting optional dependencies and BOM imports means that depending on the shape of your dependency graph, you may now have a different dependency resolution result after upgrading to Gradle 4.4.
Should you see any problem, [build scans](https://scans.gradle.com) can help you debug those. Alternatively, you can compare the output of the `dependencyInsight` task executed with a pre-4.4 version of Gradle and the one produced by Gradle 4.4.

### Change to the `Test` task structure

Common test framework functionality in the `Test` task moved to `AbstractTestTask`. Be aware that `AbstractTestTask` is the new base class for the `Test` task. The `AbstractTestTask` will be used by test frameworks outside of the JVM ecosystem. Plugins configuring an `AbstractTestTask` will find tasks for test frameworks (e.g., XCTest, Google Test, etc.).

### Changes in the `eclipse` plugin

The default output location in [EclipseClasspath](dsl/org.gradle.plugins.ide.eclipse.model.EclipseClasspath.html#org.gradle.plugins.ide.eclipse.model.EclipseClasspath:defaultOutputDir) changed from `${project.projectDir}/bin` to `${project.projectDir}/bin/default`.

### Removal of `@Incubating` methods

- `org.gradle.nativeplatform.tasks.InstallExecutable.setDestinationDir(Provider<? extends Directory>)` was removed.
    - Use `org.gradle.nativeplatform.tasks.InstallExecutable.getInstallDirectory()` instead.
- `org.gradle.nativeplatform.tasks.InstallExecutable.setExecutable(Provider<? extends RegularFile>)` was removed.
    - Use `org.gradle.nativeplatform.tasks.InstallExecutable.getSourceFile()` instead.

### Changes to Visual Studio toolchain discovery

In previous versions, Gradle would prefer a version of Visual Studio found on the path over versions discovered through any other 
means.  It will now consider a version found on the path only if a version is not found in the registry or through executing 
the [vswhere](https://github.com/Microsoft/vswhere) utility (i.e. it will consider the path only as a last resort).  In order to 
force a particular version of Visual Studio to be used, configure the [installation directory](dsl/org.gradle.nativeplatform.toolchain.VisualCpp.html#org.gradle.nativeplatform.toolchain.VisualCpp:installDir) on the Visual Studio toolchain.

### Ant version upgraded to Ant 1.9.9

Gradle has been upgraded to embed Ant 1.9.9 over Ant 1.9.6.

### Avoid checking other repositories when dependency resolution in one repository fails with HTTP status code in the 500 range

The HTTP status codes 5xx can be considered unrecoverable server states. Gradle will explicitly rethrow exceptions which occur in dependency resolution instead of quietly continue to the next repository similar to timeout issues introduced in Gradle 4.3.

### The type of `pluginManagement.repositories` changed

Before Gradle 4.4 it was a `PluginRepositoriesSpec`. This type has been removed and `pluginManagement.repositories` is now a regular `RepositoryHandler`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

- [Lucas Smaira](https://github.com/lsmaira) - Support parametrized Tooling model builders (gradle/gradle#2729)
- [Kyle Moore](https://github.com/DPUkyle) - Updated Gosu plugin to fix API breakage (gradle/gradle#3115)
- [Kyle Moore](https://github.com/DPUkyle) - Filter non-file URLs when getting classpath from `ClassLoader` (gradle/gradle#3224)
- [Jaci Brunning](https://github.com/JacisNonsense) - Support for overriding target platforms on Gcc toolchains (gradle/gradle#3124)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
