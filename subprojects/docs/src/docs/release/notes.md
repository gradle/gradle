Gradle 2.2 delivers some nice general features and improvements, as well as profound new capabilities for dependency management.

The new support for arbitrary Component Selection Rules and modelling of module “replacement” brings powerful new dependency management capabilities.
Component Selection Rules allow extremely fine grained, custom, conflict resolution strategies.
Support for declaring module replacements allows Gradle to consider modules that have different identities but that conflict in some way during conflict resolution.
This can be used to avoid have duplicate copies of libraries at different versions that changed their published coordinates over time or that transitioned into other libraries entirely.

Support for the [SonarQube](http://www.sonarqube.org) code quality management platform has significantly improved in this release through support for 
controlling the Sonar Runner process (e.g. memory settings) and to use arbitrary versions of the Sonar Runner.
This will allow leveraging of new Sonar features without updates to the plugin and more control over how Gradle integrates with Sonar.

The new support for “text resources”, added to the code quality plugins (e.g. the Checkstyle plugin), opens up new possibilities for sharing configuration/settings for code quality checks.
More generally, support for “text resources” opens up new possibilities for obtaining and/or generating text to be used in the build process, typically as a file.
While only in use by the code quality plugins at this release, this new mechanism will be leveraged by other tasks and plugins in future versions of Gradle.

Gradle 2.1 previously set the high watermark for contributions to Gradle with contributions by 18 different contributors. 
This release raises that high watermark to contributions by 23 different contributors.
Thank you to all who have contributed and helped to make Gradle an even better build system.

We hope you enjoy Gradle 2.2.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Component Selection Rules (i)
Fine tuning the dependency resolution process is even more powerful now with the use of component selection rules.  These allow custom rules to be applied whenever
multiple versions of a component are being evaluated.  Using such rules, one can explicitly reject a version that might otherwise be accepted by the default version matching
strategy.

    configurations {
        conf {
            resolutionStrategy {
                componentSelection {
                    // Accept the newest version that matches the dynamic selector
                    // but does not end with "-experimental".
                    all { ComponentSelection selection ->
                        if (selection.candidate.group == 'org.sample'
                                && selection.candidate.name == 'api'
                                && selection.candidate.version.endsWith('-experimental')) {
                            selection.reject("rejecting experimental")
                        }
                    }

                    // Rules can consider component metadata as well
                    // Accept the highest version with a branch of 'testing' or a status of 'milestone'
                    all { ComponentSelection selection, IvyModuleDescriptor descriptor, ComponentMetadata metadata ->
                        if (descriptor.branch != 'testing' && metadata.status != 'milestone') {
                            selection.reject("does not match branch or status")
                        }
                    }

                    // Rules can target specific modules
                    // Reject the 1.1 version of org.sample:api
                    withModule("org.sample:api") { ComponentSelection selection ->
                        if (selection.candidate.version == "1.1") {
                            selection.reject("known bad version")
                        }
                    }

                    // Rules can be specified as "rule source" objects
                    // Reject any version without a branch of "master" (see class definition below)
                    withModule("org.sample:api", new RejectNotMasterRule())
                }
            }
        }
    }

    dependencies {
        conf "org.sample:api:1.+"
    }

    class RejectNotMasterRule {
        // Rule source objects must have exactly one method annotated with @Mutate
        @org.gradle.module.Mutate
        void rejectNotMaster(ComponentSelection selection, IvyModuleDescriptor descriptor) {
            if (descriptor.branch != "master") {
                selection.reject("branch is not master")
            }
        }
    }

See the [User Guide section](userguide/dependency_management.html#component_selection_rules) on component selection rules for further information.

### Declaring module replacements (i)

It is now possible to declare that a certain module has been replaced by some other. 
An example of this happening in the real world is the [replacement of the Google Collections project by Google Guava](https://code.google.com/p/google-collections).
By making Gradle aware that this happened, Gradle can consider that these modules are the same thing when resolving conflicts in the dependency graph.
Another common example of this phenomenon is when a module changes its group or name. 
Examples of such changes are `org.jboss.netty -> io.netty`, `spring -> spring-core` and there are many more.
 
Module replacement declarations can ship with as part of custom Gradle plugins and enable stronger and smarter dependency resolution for all Gradle-powered projects in the enterprise. 

This new incubating feature is described in detail in the [User Guide](userguide/dependency_management.html#sec:module_replacement).

    dependencies {
      modules {
        module("com.google.collections:google-collections") {
          replacedBy("com.google.guava:guava")
        }
      }
    }

### Sonar Runner plugin improvements

The [Sonar Runner Plugin](userguide/sonar_runner_plugin.html) has been improved to fork the Sonar Runner process, whereas in previous Gradle versions the runner was executed within the build process.
This was problematic is it made controlling the environment (e.g. JVM memory settings) for the runner difficult and meant the runner could destabilize the build process.
Importantly, because the Sonar Runner process is now forked, the version of Sonar Runner to use can now be configured in the build allowing choice of the version of Sonar Runner to use.

The `sonar-runner` plugin defaults to using version 2.3 of the runner.
Upgrading to a later version is now simple:

    apply plugin: "sonar-runner"
    
    sonarRunner {
      toolVersion = "2.4"
      
      // Fine grained control over the runner process
      forkOptions {
        maxHeapSize = '1024m'
      }
    }

This feature was contributed by [Andrea Panattoni](https://github.com/zeeke).

### Native language cross-compilation improvements (i)

- Uses the file naming scheme of the target platform, rather than then host platform.
- Uses compiler and linker arguments based on the target platform, rather than the host platform.
- Added `eachPlatform()` method to each `ToolChain` type, to allow fine-grained customization of a particular tool chain on a per-platform basis.
- Added `TargetedPlatformToolChain.getPlatform()` to allow tool chain customization logic access to the target platform.

### Support for building x64 binaries on Windows using GCC (i)

Previous versions of Gradle have supported building x86 binaries using GCC on Windows. This Gradle release adds initial support for building
x64 binaries using GCC on Windows.

### Specify version control system for IntelliJ IDEA

When using the `idea` plugin, it is now possible to specify the version control system to configure IDEA to use when importing the project.

    apply plugin: "idea"

    idea {
      project {
        vcs = "Git"
      }
    }

Note: This setting is only respected when the project is opened in IDEA using the `.ipr` (and associated) files generated by the `./gradlew idea` task.
It is not respected when the project is imported into IDEA using IDEA's import feature.

This feature was contributed by [Kallin Nagelberg](https://github.com/Kallin).

### Specify location of local maven repository independently

The location of the local Maven repository can now be controlled by setting the system property `maven.repo.local` to the absolute path to the repo.
This has been added for parity with Maven itself.
This can be used to isolate the maven local repository for a particular build, without changing the location of the `~/.m2/settings.xml` which may 
contain information to be shared by all builds.

This feature was contributed by [Christoph Gritschenberger](https://github.com/ChristophGr).

### Compatibility with OpenShift

The [OpenShift PaaS](https://www.openshift.com) environment uses a proprietary mechanism for discovering the binding address of the network interface.
Gradle requires this information for inter process communication.
Support has been added for this environment which now makes it possible to use Gradle with OpenShift.
  
This feature was contributed by [Colin Findlay](https://github.com/silver2k).

### Support for renaming imported Ant targets

When [importing an Ant build](userguide/ant.html#N11485) it is now possible to specify an alternative name for tasks that corresponds to the targets of the imported Ant build.
This can be used to resolve naming collisions between Ant targets and existing Gradle tasks (GRADLE-771).

To do so, supply a transformer to the [`ant.importBuild()`] method that supplies the alternative name.

    apply plugin: "java" // adds 'clean' task
    
    ant.importBuild("build.xml") {
        it == "clean" ? "ant-clean" : it
    }

The above example avoids a name collision with the clean task.
See the [section on importing Ant builds in the Gradle Userguide](userguide/ant.html#N11485) for more information.

This feature was contributed by [Paul Watson](https://github.com/w4tson).

### Sharing configuration files across builds (i)

In previous Gradle versions, sharing external configuration files across builds (e.g. to enforce code quality standards) was difficult. 
To support this use case, a new [`TextResource`](dsl/org.gradle.api.resources.TextResource.html) abstraction was introduced. 

`TextResource`s are created using factory methods provided by [`project.resources.text`](dsl/org.gradle.api.resources.ResourceHandler.html#org.gradle.api.resources.ResourceHandler:text). 
They can be backed by various sources such as inline strings, local text files, or archives containing text files. 
A `TextResource` backed by an archive can then be shared across builds by publishing and resolving the archive from a binary repository, 
benefiting from Gradle's standard dependency management features (e.g. dependency caching).

Gradle's code quality plugins and tasks are the first to support `TextResource`. 
The following example shows how a Checkstyle configuration file can be sourced from different locations:

    apply plugin: "checkstyle"
    
    configurations {
        checkstyleConfig
    }
    
    dependencies {
        // a Jar/Zip/Tar archive containing one or more Checkstyle configuration files,
        // shared via a binary repository
        checkstyleConfig "com.company:checkstyle-config:1.0@zip" 
    }
    
    checkstyle { // affects all Checkstyle tasks
        // sourced from inline string
        config = resources.text.fromString("""<module name="Checker">...</module>""")
        // sourced from local file
        config = resources.text.fromFile("path/to/file.txt")
        // sourced from a task that produces a single file (and declares it as output)
        config = resources.text.fromFile(someTask)
        // sourced from shared archive
        config = resources.text.fromArchiveEntry(configurations.checkstyleConfig, "path/to/archive/entry.txt")
    }
    
Over time, `TextResource` will be leveraged by more existing and new Gradle APIs.
    
## Fixed issues

## Potential breaking changes

### filesMatching used in CopySpec now matches against source path rather than destination path

In the example below, both `filesMatching` blocks will now match against the source path of the files under `from`. 
In previous versions of Gradle, the second `filesMatching` block would match against the destination path that was set by executing the first block.

    task copy(type: Copy) {
        from 'from'
        into 'dest'
        filesMatching ('**/*.txt') {
            path = path + '.template'
        }
        filesMatching ('**/*.template') { // will not match the files from the first block anymore
            path = path.replace('template', 'concrete')
        }
    }

### Manually added AntTarget tasks no longer respect target dependencies

The `org.gradle.api.tasks.ant.AntTarget` task implementation adapts a target from an Ant build to a Gradle task and is used when Gradle [imports an Ant build](userguide/ant.html#N11485).

In previous Gradle versions, it was somewhat possible to manually add tasks of this type and wire them to Ant targets manually.
However, this was not recommended and can produce surprising and incorrect behaviour.
Instead, the `ant.importBuild()` method should be used to import Ant build and to run Ant targets.

As of Gradle 2.2, manually added `AntTarget` tasks no longer honor target dependencies.
Tasks created as a result of `ant.importBuild()` (i.e. the recommended practice) are unaffected and will continue to work.

### Sonar Runner Plugin changes

The Sonar Runner plugin now forks a new JVM to analyze the project. 
Projects using the [Sonar Runner Plugin](userguide/sonar_runner_plugin.html) should consider setting explicitly the memory settings for the runner process. 

Existing users of the `sonar-runner` plugin may have increased the memory allocation to the Gradle process to facilitate the Sonar Runner.
This can now be reduced.
    
Additionally, the plugin previously mandated the use of version 2.0 of the Sonar Runner.
The default version is now 2.3 and it is configurable.
If you require the previous default of 2.0, you can specify this version via the project extension.
    
    sonarRunner {
      toolVersion = '2.0'
    }

### Publishing plugins and Native Language Support plugins changes

In previous Gradle versions it was possible to use `afterEvaluate {}` blocks to configure tasks added to the project by `"maven-publish"`, `"ivy-publish"` and Native Language Support plugins.
These tasks are now created after execution of `afterEvaluate {}` blocks. 
This change was necessary to continue improving the new model configuration. 
Please use `model {}` blocks instead for that purpose, e.g.:

    model { 
        tasks.generatePomFileForMavenJavaPublication { 
            dependsOn 'someOtherTask' 
        } 
    }

### CodeNarc plugin Groovy version changes

The version of Groovy that the [CodeNarc plugin](userguide/codenarc_plugin.html) uses while analyzing Groovy source code has changed in this Gradle release.
Previously, the version of Groovy that Gradle ships with was used.
Now, the version of Groovy that the CodeNarc tool declares as a dependency is used. 

This should have no impact on users of the CodeNarc plugin.
Upon first use of the CodeNarc plugin with Gradle 2.1, you may see Gradle downloading a Groovy implementation for use with the CodeNarc plugin.

### Change of package names for sonar-runner plugin

The classes of the (incubating) [Sonar Runner Plugin](userguide/sonar_runner_plugin.html) have moved from the package `org.gradle.api.sonar.runner` to `org.gradle.sonar.runner`.

If you were depending on these classes explicitly, you will need to update the reference.

### Changes to incubating native language support

- Replaced `TargetedPlatformToolChain` with `GccPlatformToolChain` and `VisualCppPlatformToolChain`.
- Renamed `PlatformConfigurableToolChain` to `GccCompatibleToolChain`.
- Removed tool properties from tool chains. `target()` or `eachPlatform()` should be used instead.
- Removed deprecated `ExecutableBinary`: use `NativeExecutableBinary` instead.
- Renamed package `org.gradle.nativeplatform.sourceset` to `org.gradle.language.nativeplatform`
- Renamed package `org.gradle.language.nativebase` to `org.gradle.language.nativeplatform`
- Added `Native` prefix to existing `Platform`, `ToolChain`, `ToolChainRegistry` and `PlatformToolChain` types
- Changed `NativeComponentSpec.getBinaries()` to return `DomainObjectSet<BinarySpec>`
- Added `NativeComponentSpec.getNativeBinaries()` to return `DomainObjectSet<NativeBinarySpec>`

### Changes to incubating JVM-component plugins and language support

- Renamed `org.gradle.language.jvm.ResourceSet` to `JvmResourceSet`
- Moved `org.gradle.api.jvm.ClassDirectoryBinarySpec` to `org.gradle.jvm.ClassDirectoryBinarySpec`
- Moved `org.gradle.language.jvm.artifact.JavadocArtifact` to `org.gradle.language.java.artifact.JavadocArtifact`.

### Using convention mapping for code quality tasks/extensions 

Using the internal convention mapping feature for one of the following properties will no longer have an effect:

* org.gradle.api.plugins.quality.CheckstyleExtension#configFile
* org.gradle.api.plugins.quality.Checkstyle#configFile
* org.gradle.api.plugins.quality.CodeNarcExtension#configFile
* org.gradle.api.plugins.quality.CodeNarc#configFile
* org.gradle.api.plugins.quality.FindBugsExtension#includeFilter
* org.gradle.api.plugins.quality.FindBugsExtension#excludeFilter
* org.gradle.api.plugins.quality.FindBugs#includeFilter
* org.gradle.api.plugins.quality.FindBugs#excludeFilter

### Configuring code quality tasks/extensions with `File` objects representing relative paths

A `File` object that represents a relative path and is used to configure one of the following properties will now be 
interpreted relative to the current project, rather than relative to the current working directory of the Gradle process:

* org.gradle.api.plugins.quality.CheckstyleExtension#configFile
* org.gradle.api.plugins.quality.Checkstyle#configFile
* org.gradle.api.plugins.quality.CodeNarcExtension#configFile
* org.gradle.api.plugins.quality.CodeNarc#configFile
* org.gradle.api.plugins.quality.FindBugsExtension#includeFilter
* org.gradle.api.plugins.quality.FindBugsExtension#excludeFilter
* org.gradle.api.plugins.quality.FindBugs#includeFilter
* org.gradle.api.plugins.quality.FindBugs#excludeFilter

Note that this only affects files created with `new File("relative/path")` (which is not recommended), 
but not files created with `project.file("relative/path")`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Jake Wharton](https://github.com/JakeWharton) - clarification of hashing used for Gradle Wrapper downloads
* [Baron Roberts](https://github.com/baron1405) - fixes to JDepend plugin
* [Dinio Dinev](https://github.com/diniodinev) - various spelling corrections
* [Alex Selesse](https://github.com/selesse) - documentation improvements
* [Raymond Chiu](https://github.com/rschiu) - improve handling of install name in GCC tool chain
* [Kallin Nagelberg](https://github.com/Kallin) - support for specifying VCS with IDEA plugin
* [Christoph Gritschenberger](https://github.com/ChristophGr) - support for `maven.repo.local` system property
* [Colin Findlay](https://github.com/silver2k) - OpenShift compatibility [GRADLE-2871]
* [Paul Watson](https://github.com/w4tson) - Support for renaming Ant targets on import [GRADLE-771]
* [Andrea Panattoni](https://github.com/zeeke) - Provide option to fork Sonar analysis [GRADLE-2587]
* [Lóránt Pintér](https://github.com/lptr) 
    - `Action` overloads project `project.exec()` and `project.javaexec()`
    - DefaultResolutionStrategy.copy() should copy componentSelectionRules, too
* [Clark Brewer](https://github.com/brewerc) - spelling corrections
* [Guilherme Espada](https://github.com/GUIpsp) - allow to use OpenJDK with Gradle
* [Harald Schmitt](https://github.com/surfing) 
    - handle German-localised `readelf` when parsing output in integration tests
    - fix performance tests for Locale settings using not `.` as decimal separator
* [Derek Eskens](https://github.com/snekse) - documentation improvements.
* [Justin Ryan](https://github.com/quidryan) - documentation fixes.
* [Alexander Shutyaev](https://github.com/shutyaev) - log4j-over-slf4j version upgrade. [GRADLE-3167]
* [Schalk Cronjé](https://github.com/ysb33r) - DSL documentation improvements
* [Ryan Liptak](https://github.com/squeek502) - Eclipse integration test coverage improvements
* [Stuart Armitage](https://github.com/maiflai) - Fixed bug in AbstractTask.setActions
* [Björn Kautler](https://github.com/Vampire) - improvements to `'sonar-runner'` plugin
* [Andrii Liubimov](https://github.com/aliubimov) - enhancement to `IdeaModule` model to mark generated source directories
* [Damien Coraboeu  f](https://github.com/dcoraboeuf) - fix for IndexOutOfBoundsException thrown when determining task execution plan [GRADLE-2957]

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
