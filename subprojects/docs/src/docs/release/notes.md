Continuing on with the performance improvements delivered in recent Gradle versions, 1.5 brings wide reaching optimizations to dependency resolution as well as important 
improvements to two recent features; configure-on-demand and parallel execution. 
Gradle continues to embrace the challenges of large scale build automation. Along with the performance improvements comes the usual mix of new features, bug fixes, usability improvements and refinements. 

The dependency resolve rule feature introduced in 1.4 gains new capabilities in 1.5. Trouble dependencies can now be completely substituted at resolution time which enables solving 
some very tricky issues with complex dependency graphs. On the publication side, the new (incubating) Maven and Ivy publishing plugins gain new capabilities making them able 
to solve more publishing use cases with elegance.

A very exciting trend is the increase in code and documentation contributions from the community, both large and small. Some very useful new features such as the "build dashboard" 
and "distributions" plugins added in this release, to name a few, came by way of contribution. If you'd like to get involved and contribute to Gradle, a great place to 
start is [gradle.org/contribute](http://www.gradle.org/contribute).

The Gradle team is also excited to announce the first ever [“Gradle Summit”](http://gradlesummit.com/) (Sponsored by [Gradleware](http://gradleware.com/)),
held June 13th - 14th in Santa Clara, California. The summit will be two fully packed days of technical sessions given by Gradle core developers ranging from introductory 
to deep dive as well as informational sessions by several large organizations on how they get the most out of Gradle. In between sessions there'll be plenty of opportunity 
for talking to other Gradle users and the Gradle development team. This is an event not to miss. 
Registration is [now open](http://gradlesummit.com/conference/santa_clara/2013/06/gradle/event_register).
    
Read on for more details on why you should upgrade to Gradle 1.5. As always, please share your feedback and experiences with Gradle 1.5 via the Gradle Forums.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Performance improvements 

This release brings performance improvements for JUnit and TestNG test execution as well as dependency resolution improvements for all builds that use dependency resolution.

#### Test execution performance

Test execution has been further optimized in this release, continuing on the work in Gradle 1.4. The test report is now generated more efficiently, so that it take less
time and heap space to generate the HTML report. In addition, for those projects that use the TestNG framework, the test JVM now starts more quickly, meaning that test
execution starts earlier than it did in previous Gradle releases.

#### Dependency resolution performance

Gradle's [dependency cache](userguide/dependency_management.html#sec:dependency_cache) is multi process safe, which requires the use of locking mechanisms.
Improvements to the way the locks are utilised in this release have increased the dependency resolution speed by up to 30%
for builds that use local repositories or maven local.
Builds that don't use local repositories should also exhibit slightly faster dependency resolution.
Every build that resolves dependencies benefits from this improvement.

### Substituting dependencies via dependency resolve rules (i)

Gradle 1.4 [introduced the ability](http://www.gradle.org/docs/1.4/release-notes#dependency-resolve-rules) to dynamically change the version of a dependency to be resolved via dependency resolve rules.
It is now possible to change the group, name and/or version of a requested dependency, allowing a dependency to be substituted with a completely
different dependency during resolution.

    configurations.all {
        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            if (details.requested.name == 'groovy-all') {
                details.useTarget group: details.requested.group, name: 'groovy', version: details.requested.version
            }
        }
    }

Dependency resolve rules can now be used to solve some interesting dependency resolution problems:

- Substituting an alternative implementation for some module. For example, replace all usages of `log4j` with a compatible version of `log4j-over-slf4j`.
- Dealing with conflicting implementations of some module. For example, replace all usages of the various `slf4j` bindings with `slf4j-simple`.
- Dealing with conflicting packaging of some module. For example, replace all usages of `groovy-all` with `groovy`.
- Dealing with modules that have changed their (group, module) identifier. For example, replace `ant:ant:*` with `org.apache.ant:ant:1.7.0` and let conflict resolution take care of the rest.
- Substituting different implementations at different stages. For example, substitute all servlet API dependencies with `'javax.servlet:servlet-api:2.4'` at compile time and the jetty implementation at test runtime.

For more information, including more code samples, please refer to [the user guide](userguide/dependency_management.html#sec:dependency_resolve_rules).

### New Sonar Runner plugin (i)

Gradle 1.5 ships with a new `sonar-runner` plugin that is set to replace the existing Sonar plugin. As its name indicates,
the new plugin is based on the [Sonar Runner](http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner),
the new and official way to integrate with Sonar. Unlike the old Sonar plugin, the new Sonar Runner plugin
is compatible with the latest Sonar versions (3.4 and above). To learn more, check out the [Sonar Runner Plugin](userguide/sonar_runner_plugin.html)
chapter in the Gradle user guide, and the `sonarRunner` samples in the full Gradle distribution.

### IDEA plugin is now Scala aware

When the IDEA plugin encounters a Scala project, it will now add additional configuration to make the
project compile in IntelliJ IDEA out of the box. In particular, the plugin adds a Scala facet and
a Scala compiler library that matches the Scala version used on the project's class path.

### Configure-on-demand improvements (i)

Gradle 1.4 introduced a [new operational mode called “configure-on-demand”](http://www.gradle.org/docs/1.4/release-notes#improved-scalability-via-configuration-on-demand) designed
to improve Gradle performance on large projects.  This release brings the following improvements to this new feature:

* [Tooling API](userguide/embedding.html) compatibility.
* [buildSrc](userguide/organizing_build_logic.html#sec:build_sources) is now fully supported.
* Task dependencies declared via task path are now supported.

Enabling this new mode is now more convenient. It can be enabled at invocation time via the new `--configure-on-demand` flag, or via the `org.gradle.configureondemand` project property.
The project property can be set permanently for a project, or permanently for a user just like other [build settings](userguide/build_environment.html#sec:gradle_configuration_properties).

For example, by adding a `gradle.properties` file to root of the project with the following content Gradle will always use configure-on-demand mode for the project.

    #gradle.properties file
    org.gradle.configureondemand=true

For more information on configure-on-demand please consult [the user guide](userguide/multi_project_builds.html#sec:configuration_on_demand).

### Parallel execution improvements (i)

Gradle 1.2 introduced a [parallel execution](userguide/multi_project_builds.html#sec:parallel_execution) mode for multi-project builds.
This release brings significantly improved utilisation of the parallel workers.

Previously, workers where statically assigned to projects and often waited for the upstream dependencies to be built.
This caused workers to stay idle when there was work they could be doing. The distribution of work is now more dynamic which has resulted in highly parallelizable builds building up to 30% faster.

It is also now possible to enable parallel building via a [build setting](userguide/build_environment.html#sec:gradle_configuration_properties).
For example, by adding a `gradle.properties` file to root of the project with the following content Gradle will always build the project in parallel.

    //gradle.properties file
    org.gradle.parallel=true

### New distribution plugin (i)

Thanks to a contribution from [Sébastien Cogneau](https://github.com/scogneau), a new `distribution` plugin has been added. This plugin adds general-purpose for support bundling and installing distributions.

This plugin adds a `main` distribution, and you can add additional distributions. For each distribution, tasks are added to create a ZIP or TAR file for the distribution and
to install the distribution.

You can define multiple distributions:

    distributions {
        enterprise
        community
    }

To build the additional distributions you can run the generated `Zip` tasks `enterpriseDistZip` and `communityDistZip`. For more information, please consult the 
[user guide](userguide/distribution_plugin.html).

### Improved Java library distribution plugin (i)

The Java library distribution plugin now extends the newly introduced distribution plugin. Thanks to this, you can now create tar files and install Java library distributions.

For more information, please consult the [user guide](userguide/javaLibraryDistribution_plugin.html).

### Improved usability of project dependencies

Project dependencies at configuration time are now fully supported.
Prior to this change, any resolution of a project dependency at configuration time may have led to confusing behavior as the target project may not have been configured yet.
Now the resolution of the project dependency implies configuration of the target project.
This means that the order in which projects are configured may now be different (i.e. it will be correct).
This change should not cause any trouble in existing builds and it fixes up the confusing behavior with project dependencies.

### Improvements to the new '`maven-publish`' and '`ivy-publish`' plugins (i)

The '`maven-publish`' and '`ivy-publish`' plugins gain new features and capabilities in this Gradle release.

#### Easy publication of software components

Gradle 1.5 introduces the concept of a “Software Component”, which defines something that can be produced by a Gradle project such as a Java library or a web application.
Both the '`ivy-publish`' and '`maven-publish`' plugins are component-aware, simplifying the process of publishing a module. The component defines the set of artifacts and dependencies for publishing.

Presently, the set of components available for publishing is limited to '`java`' and '`web`', added by the '`java`' and '`war`' plugins respectively. In the future it will be possible to
create new components and new component types.

Publishing the '`web`' component will result in the war file being published with no runtime dependencies (dependencies are bundled in the war):

    apply plugin: 'war'
    apply plugin: 'maven-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    publishing {
        repositories {
            maven { url 'http://mycompany.org/mavenRepo' }
            ivy { url 'http://mycompany.org/ivyRepo' }
        }
        publications {
            mavenWeb(MavenPublication) {
                from components.web
            }
            ivyWeb(IvyPublication) {
                from components.java // Include the standard java artifacts
            }
        }
    }

#### Publishing custom artifacts

This release introduces the ability to customize the set of artifacts to publish to a Maven repository or an Ivy repository.
This gives complete control over which artifacts are published, and the classifier/extension used to publish them.

Due to differences in the capabilities of Ivy vs Maven repositories, the DSL is slightly different for each repository format.

    apply plugin: 'java'
    apply plugin: 'maven-publish'
    apply plugin: 'ivy-publish'

    group = 'group'
    version = '1.0'

    // … declare dependencies and other config on how to build

    task sourceJar(type: Jar) {
        from sourceSets.main.allJava
        classifier "source"
    }

    publishing {
        repositories {
            maven { url 'http://mycompany.org/mavenRepo' }
            ivy { url 'http://mycompany.org/ivyRepo' }
        }
        publications {
            mavenCustom(MavenPublication) {
                from components.java // Include the standard java artifacts
                artifact sourceJar {
                    classifier "source"
                }
                artifact("project-docs.htm") {
                    classifier "docs"
                    extension "html"
                    builtBy myDocsTask
                }
            }
            ivyCustom(IvyPublication) {
                from components.java // Include the standard java artifacts
                artifact(sourceJar) {
                    type "source"
                    conf "runtime"
                    classifier "source"
                }
                artifact("project-docs.htm") {
                    classifier "docs"
                    extension "html"
                    builtBy myDocsTask
                }
            }
        }
    }

Be sure to check out the DSL reference for [MavenPublication](dsl/org.gradle.api.publish.maven.MavenPublication.html) and [IvyPublication](dsl/org.gradle.api.publish.ivy.IvyPublication.html)
for complete details on how the set of artifacts can be customized.

For more information about using the new '`maven-publish`' and '`ivy-publish`' plugins in general, please consult the user guide ([maven](userguide/publishing_maven.html)) ([ivy](userguide/publishing_ivy.html)).

#### Generate POM file without publishing

POM file generation has been moved into a separate task, so that it is now possible to generate the POM file without actually publishing your project. All details of
the publishing model are still considered in POM generation, including `components`, custom `artifacts`, and any modifications made via `pom.withXml`.

The task for generating the POM file is of type [`GenerateMavenPom`](dsl/org.gradle.api.publish.maven.tasks.GenerateMavenPom.html), and is given a name based on the name
of the publication: `generatePomFileFor<publication-name>Publication`. So in the above example where the publication is named '`mavenCustom`',
the task will be named `generatePomFileForMavenCustomPublication`.

#### Full support for Unicode in publication identifiers

Where supported by the underlying metadata format, Gradle will now handle any valid Unicode character in module group, name and version as well as artifact name, extension and classifier.

The only values that are explicitly prohibited are '\\', '/' and any ISO control character. Supplied values are validated early in publication. 

A couple of caveats to the Unicode support:

- Maven restricts '`groupId`' and '`artifactId`' to a limited character set (`[A-Za-z0-9_\\-.]+`) and Gradle enforces this restriction.
- Certain repositories will not be able to handle all supported characters. For example, the '`:`' character cannot be used
  as an identifier when publishing to a filesystem-backed repository on Windows.

### Support for Ivy dynamic resolve mode (i)

It is now possible to enable the equivalent of Ivy's _dynamic resolve_ mode when resolving dependencies. This is only supported for Ivy repositories.

See the [user guide](userguide/dependency_management.html#ivy_dynamic_resolve_mode) for examples and further details.

### New build dashboard Plugin (i)

Thanks to a contribution from [Marcin Erdmann](https://github.com/erdi), a new `build-dashboard` plugin has been added. This plugin adds a task to projects to generate a build dashboard HTML report which contains
references to all reports that were generated during the build. In the following example, the `build-dashboard` plugin is added to a project which has also the `groovy` and
the `codenarc` plugin applied:

    apply plugin: 'groovy'
    apply plugin: 'build-dashboard'
    apply plugin: 'codenarc'

By running the `buildDashboard` task after other tasks that generate reports (e.g. by running `gradle check buildDashboard`), the generated build dashboard contains links to the 
`codenarc` reports. This version of the build dashboard does not include links to test reports. This plugin is in the early stages of development and will be significantly improved in future Gradle releases.

More information on the `build-dashboard` plugin can be found in the [user guide](userguide/buildDashboard_plugin.html).
  
## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 2.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

### `ArtifactRepositoryContainer.getResolvers()`

This method exposes internal implementation details that will be subject to change in the future. Its use should be avoided.

## Potential breaking changes

### Changes to incubating Maven publishing support

Breaking changes have been made to the incubating '`maven-publish`' plugin, which provides an alternative means to publish to Maven repositories.

- A MavenPublication must be explicitly added to the `publications` container; no publication is added implicitly by the `maven-publish` plugin.
    - If no `MavenPublication` is configured then nothing will be published.
- A `MavenPublication` does not include any artifacts or dependencies by default; these must be added directly or via a `SoftwareComponent`.
    - If no artifacts are configured, a Maven POM file will be published with no artifacts or dependencies declared.
- The `groupId`, `artifactId` and `version` in the published pom cannot be changed via `MavenPom.withXml()`:
   it was previously possible change these values, but any interproject dependency would not pick up these changes.
    - In the future Gradle will provide a robust mechanism for modifying publication identity prior to publication.
- Identifiers used in Maven publications (`groupId`, `artifactId`, `version`, `ext`, `classifier`) have new character restrictions:
  these identifiers may not contain '`/`', '`\`' or any ISO Control Characters. Using these values generally made it impossible to resolve these modules, so this is now prevented
  at the time of publication.
   - `groupId` and `artifactId` are further restricted to "`[A-Za-z0-9_\-.]+`": this is a Maven restriction, so it is enforced at the time of publication.
- The `GenerateMavenPom` task for a publication is not created until the publishing extension is first accessed. Any attempt to configure a `GenerateMavenPom` task
  should be enclosed within a `publishing` block.
- Once the publishing extension is accessed as a property, it is no longer possible to further configure the extension using a `publishing` block.

Be sure to check out the [Maven Publishing User Guide Chapter](userguide/publishing_maven.html) and the [MavenPublication DSL reference](dsl/org.gradle.api.publish.maven.MavenPublication.html)
for complete description and examples of the new Maven Publishing support.

### Changes to incubating Ivy publishing support

Breaking changes have been made to the incubating '`ivy-publish`' plugin, which provides an alternative means to publish to Ivy repositories.

- An `IvyPublication` must be explicitly added to the `publications` container; no publication is added implicitly by the `ivy-publish` plugin.
    - If no `IvyPublication` is configured then nothing will be published.
- An `IvyPublication` does not include any artifacts or dependencies by default; these must be added directly or via a `SoftwareComponent`.
    - If no artifacts are configured, an `ivy.xml` file will be published with no artifacts or dependencies declared.
- The `organisation`, `name` and `revision` cannot be changed via `IvyDescriptor.withXml()`:
   it was previously possible to do this, although it did not change the actual identity of the published module.
    - In the future Gradle will provide a robust mechanism for modifying publication identity prior to publication.
- Identifiers in ivy modules (`organisation`, `module`, `revision`) and artifacts (`name`, `ext`, `type`, `classifier`) have new character restrictions:
  these identifiers may not contain '`/`', '`\`' or any ISO Control Characters. Using these values generally made it impossible to resolve these modules, so this is now prevented
  at the time of publication.
- Removed `GenerateIvyDescriptor.xmlAction` property. The `ivy.descriptor.withXml()` method provides a way to customise the generated module descriptor.
- The `GenerateIvyDescriptor` task for a publication is not created until the publishing extension is first accessed. Any attempt to configure a `GenerateIvyDescriptor`
  should be enclosed within a `publishing` block.
- Once the publishing extension is accessed as a property, it is no longer possible to further configure the extension using a `publishing` block.

Be sure to check out the [Ivy Publishing User Guide Chapter](userguide/publishing_ivy.html) and the [IvyPublication DSL reference](dsl/org.gradle.api.publish.ivy.IvyPublication.html)
for complete description and examples of the new Ivy Publishing support.

### Changes to default PMD ruleset

The default value for ruleset extension has changed from `["basic"]` to `[]`. The default was moved to the `Pmd` task, so everything should just work as it did before.

### Project configuration order

Improving the usability of project dependencies (see the section above) might change the order in which projects are configured.
This is not expected to cause problems in existing builds, but is mentioned for completeness.

### Optimized order of task execution in parallel execution mode

Parallel builds are now much faster due to better utilisation of parallel workers. However, this means that tasks may be executed in different order in parallel builds.
This will not cause problems in a correctly [decoupled build](userguide/multi_project_builds.html#sec:decoupled_projects) but may bring problems to light in builds that are not properly decoupled.

### Changes to the incubating Java library distribution plugin

The `distribution` extension that is added by the `java-library-distribution` plugin was removed. The `main` distribution is now accessible using the `distributions` extension:

    distributions {
        main {
            ...
        }
    }

## External contributions

We would like to thank the following community members for making excellent contributions to this release of Gradle.

* [Joe Sortelli](https://github.com/sortelli) - Fixed incorrect handling of `ivy.xml` where dependency configuration contained wildcard values (GRADLE-2352)
* [David M. Carr](https://github.com/davidmc24)
    * When JUnit tests have assumption failures, treat them as "skipped" (GRADLE-2454)
    * Documentation cleanups.
* [Sébastien Cogneau](https://github.com/scogneau) - Introduce the distribution plugin
* [Kenny Stridh](https://github.com/kensi)
    * Allow specifying `targetJdk` for PMD code analysis (GRADLE-2106)
    * Added support for PMD version 5.0.+
* [Marcin Erdmann](https://github.com/erdi)
    * Add`build-dashboard` plugin
    * Make notify-send notifications transient in Gnome Shell
* [Michael R. Maletich](https://github.com/HawaiianSpork)
    * Add `maxHeapSize` property to `FindBugs` task to allow setting the max heap size for spawned FindBugs java process
    * Add `contentsCompression` property to the `Zip` task type to specify the compression level of the archive
* [Barry Pitman](https://github.com/barrypitman) - Fixed Maven conversion problem (GRADLE-2645)
* [Kallin Nagelberg](https://github.com/Kallin) - Fixed grammar in the `SourceSetOutput` documentation
* [Klaus Illusioni](https://github.com/illusioni) - Fixed Eclipse wtp component generation issue (GRADLE-2653)
* [Alex Birmingham](https://github.com/abirmingham) - Fixed PMD Javadoc
* [Matthieu Leclercq](https://github.com/mleclercq) - Fixed the nested configuration resolution issue (GRADLE-2477)
* [Dan Stine](https://github.com/dstine) - Userguide cleanups.

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
