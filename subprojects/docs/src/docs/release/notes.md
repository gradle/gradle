## New and noteworthy

Here are the new features introduced in this Gradle release.

### Groovy 2.2.2

Gradle now uses Groovy 2.2.2 to compile and running build scripts and plugins.

### New API for resolving source and javadoc artifacts (i)

Gradle 2.0 introduces a new, incubating API for resolving component artifacts. With this addition, Gradle now offers separate dedicated APIs for resolving
components and artifacts. The entry point to the new 'artifact query' API is `dependencies.createArtifactResolutionQuery()`.

Presently, this feature is limited to the resolution of `SourcesArtifact` and `JavadocArtifact` artifacts for a `JvmLibrary` components.
Over time this will be expanded to permit querying of other component and artifact types.

For example, to get the source artifacts for all 'compile' dependencies:

    task resolveSources << {
        def componentIds = configurations.compile.incoming.resolutionResult.allDependencies.collect { it.selected.id }

        def result = dependencies.createArtifactResolutionQuery()
                          .forComponents(componentIds)
                          .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
                          .execute()

        for (component in result.resolvedComponents) {
            component.getArtifacts(SourcesArtifact).each { println "Source artifact for ${component.id}: ${it.file}" }
        }
    }


For an example usage of the new API, see <a href="dsl/org.gradle.api.artifacts.query.ArtifactResolutionQuery.html">the DSL Reference</a>.

### Accessing Ivy extra info from component metadata rules

It's now possible to access Ivy extra info from component metadata rules. Roughly speaking, Ivy extra info is a set of user-defined
key-value pairs published in the Ivy module descriptor. Rules wishing to access the extra info need to specify a parameter of type
`IvyModuleMetadata`. Here is an example:

    dependencies {
        components {
            eachComponent { component, IvyModuleMetadata ivyMetadata ->
                println ivyMetadata.extraInfo["expired"] // TODO: what's a real-world use case?
            }
        }
    }

### Cleaner build scripts with `plugins.withId`

New <a href="javadoc/org/gradle/api/plugins/PluginContainer.html#withId(java.lang.String, org.gradle.api.Action)">plugins.withId()</a>
enables referring to plugins more conveniently.
In previous releases, some times it was necessary for the client of a custom plugin to know the fully qualified type of the plugin:

    import com.my.custom.InterestingPlugin
    plugins.withType(InterestingPlugin) { ...

    //now possible, given InterestingPlugin uses "interesting-plugin" id:
    plugins.withId("interesting-plugin") { ...

Benefits of the new API for the users:

* less pressure to know the exact java class of the plugin
* build scripts are more likely to be decoupled from the plugin types (e.g. it's easier for plugin author to refactor/change the type)
* some build scripts are cleaner and more consistent because plugins are applied by 'id' and are also filtered by 'id'

### Support for Ivy and Maven repositories with SFTP scheme

In addition to `file`, `http` and `https`, Ivy and Maven repositories now also support the `sftp` transport scheme. Currently, authentication with the SFTP server only works based on
providing username and password credentials.

Here is an example usage of resolving dependencies from a SFTP server with Ivy:

    apply plugin: 'java'

    repositories {
        ivy {
            url 'sftp://127.0.0.1:22/repo'
            credentials {
                username 'sftp'
                password 'sftp'
            }
            layout 'maven'
        }
    }

    dependencies {
        compile 'org.apache.commons:commons-lang3:3.3.1'
    }

Resolving dependencies from a SFTP server with Maven works accordingly. Publishing is not supported yet. The following example demonstrates the use case:

    apply plugin: 'java'

    repositories {
        maven {
            url 'sftp://127.0.0.1:22/repo'
            credentials {
                username 'sftp'
                password 'sftp'
            }
        }
    }

    dependencies {
        compile 'org.apache.commons:commons-lang3:3.3.1'
    }

Here is an example usage of publishing an artifact to an Ivy repository hosted on a SFTP server:

    apply plugin: 'java'
    apply plugin: 'ivy-publish'

    version = '2'
    group = 'org.group.name'

    publishing {
        repositories {
            ivy {
                url 'sftp://127.0.0.1:22/repo'
                credentials {
                    username 'sftp'
                    password 'sftp'
                }
                layout 'maven'
            }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }


### Consumed Apache Maven POM profile activation through absence of system property

On top of the support for POM profiles that [are active by default](http://books.sonatype.com/mvnref-book/reference/profiles-sect-activation.html), a profile also becomes active if the
corresponding system property is _not_ set. The following POM file demonstrates such a use case:

    <project>
        ...
        <profiles>
            <profile>
                <id>profile-1</id>
                <property>
                    <name>!env</name>
                </property>
            </profile>
        </profiles>
    </project>

### Allow control of the exact set of arguments passed to a toolchain executable

Gradle now provides a 'hook' allowing the build author to control the exact set of arguments passed a toolchain executable.
This will allow a build author to work around any limitations in Gradle, or incorrect assumptions that Gradle makes.

    apply plugin:'cpp'

    model {
        toolChains {
            visualCpp(VisualCpp) {
                cppCompiler.withArguments { args ->
                    args << "-DFRENCH"
                }
            }
            clang(Clang){
                cCompiler.withArguments { args ->
                    Collections.replaceAll(args, "CUSTOM", "-DFRENCH")
                }
                linker.withArguments { args ->
                    args.remove "CUSTOM"
                }
                staticLibArchiver.withArguments { args ->
                    args.remove "CUSTOM"
                }
            }

        }
    }

### Support for adding target platform specific configurations in native binary projects (GCC based toolchains)

It's now a lot easier to define a target platform and how to build for it. Instead of implementing an interface you can describe a target platform
and it's configuration in the Gradle DSL.

When declaring a toolchain, the targeted platforms can be configured directly in the toolChain model. Furthermore target platform specific configurations
can be declared:

	model {
	    toolChains {
	        gcc(Gcc) {
	            target("arm"){
	                cppCompiler.executable = "custom-gcc"
	                cppCompiler.withArguments { args ->
	                    args << "-m32"
	                }
	                linker.withArguments { args ->
	                    args << "-m32"
	                }
	            }
	            target("sparc")
	        }
	    }
		platforms {
			arm {
		    	architecture "arm"
			}
			sparc {
		    	architecture "sparc"
			}
		}

Have a look at the [User Guide](userguide/nativeBinaries.html#native_binaries:tool_chain) for more details on how to configure Gcc compatbile
toolchains for cross compilation.

### New 'ivy' layout support for Ivy repositories

When defining an 'ivy' repository, you can provide a named layout to describe how artifacts are organised within that repository.

In addition to the 'gradle' (default) and 'maven' layouts, you can now specify the 'ivy' layout which tells Gradle that your repository
is configured with the default ivy artifact and metadata patterns.

    repositories {
        ivy {
            url 'http://my.server/repo'
            layout 'ivy'
        }
    }

See the [User Guide](/dependency_management.html#N150B8) and the
<a href="dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html#org.gradle.api.artifacts.repositories.IvyArtifactRepository:layout(java.lang.String, groovy.lang.Closure)">
DSL Reference</a> for more detail on how to use named layouts.

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
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Upgraded to Groovy 2.2.2

Gradle now uses Groovy 2.2.2 to compile and run scripts and plugins. Generally, this should be backwards compatible. However, this change may require
that you recompile some plugins and may also require some source changes.

### Custom TestNG listeners are applied before Gradle's listeners

This way the custom listeners are more robust because they can affect the test status.
There should be no impact of this change because majority of users do not employ custom listeners
and even if they do healthy listeners will work correctly regardless of the listeners' order.

### Support for reading or changing file permissions on certain platforms with Java 5 or 6

Gradle previously supported file permissions on Solaris and on Linux ia-64 using Java 5 and Java 6. This support has
been removed. You will receive a warning when attempting to use file permissions on these platforms.

Note that file permissions are supported on these platforms when you use Java 7 and later, and is supported for all Java
versions on Linux, OS X, Windows and FreeBSD for x86 and amd64 architectures.

If you wish to have support for file permissions on other platforms and architectures, please help us port our native integration to these platforms.

### Support for terminal integration on certain platforms

Gradle previously supported terminal integration on Solaris and Linux ia-64. This support has been removed. When you use Gradle on these
platforms, Gradle will fall back to using plain text output.

Note that terminal integration is supported on Linux, OS X, Windows and FreeBSD for x86 and amd64 architectures.

If you wish to have terminal integration on other platforms and architectures, please help us port our native integration to these platforms.

### Build script changes

Gradle now assumes that all Gradle scripts are encoded using UTF-8. Previously, Gradle assumed the system encoding. This change
affects all build scripts, settings scripts and init scripts.

It is now an error to include a label on a statement in a Gradle script:

    someLabel:
    group = 'my.group'

### Native binaries model changes

A bunch of changes and renames have been made to the incubating 'native binaries' support.
For certain common usages, a backward-compatible api has been maintained.

- Package structure has changed, with many public classes being moved.
- `Library`, `Executable` and `TestSuite` renamed to `NativeLibrary`, `NativeExecutable` and `NativeTestSuite`
    - Related binary types have also been renamed
    - Kept `StaticLibraryBinary`, `SharedLibraryBinary`, `ExecutableBinary` and `TestSuiteExecutableBinary` for compatibility
- `NativeBinariesPlugin` has been renamed to `NativeComponentPlugin` with id `'native-component'`
- `NativeBinariesModelPlugin` renamed to `NativeComponentModelPlugin`
- `BuildableModelElement.lifecycleTask` renamed to `buildTask`
- `NativeBinaryTasks.getBuilder()` renamed to `getCreateOrLink()`
- `NativeBinaryTasks.getLifecycle()` renamed to `getBuild()`
- `BuildBinaryTask` renamed to `ObjectFilesToBinary`
- `PlatformConfigurableToolChain.addPlatformConfiguration(TargetPlatformConfiguration)` has been replaced by
   `PlatformConfigurableToolChain.target(...)`.

### New Java component model changes

A bunch of changes and renames have been made to the new, incubating 'java component' support.

- Package structure has changed, with many public classes being moved.

### Support for the deprecated Gradle Open API removed

- `GradleRunnerFactory` removed.
- `UIFactory` removed.
- `ExternalUtility` removed.

### Properties are no longer dynamically created on assignment

Previously, Gradle would create a property on a domain object when assigning a value to a property that does not exist:

    project.myProperty = 'some value'
    assert myProperty == 'some value'

This behaviour has been deprecated since Gradle 1.?? and is now an error. Instead, you should either use the `ext` namespace or use a local variable:

    def myProperty = 'some value'
    assert myProperty == 'some value'

    // or
    ext.myProperty == 'some value'
    assert myProperty == 'some value'

### Removed deprecated plugins

- `code-quality` plugin replaced by `checkstyle` and `codenarc`.

### Changes to Groovy and Scala plugins

- The Groovy plugin no longer defines the `groovy` configuration.
- The Groovy plugin no longer defines the `scalaTools` configuration.

### Removed deprecated command line options

- `--cache` and `-C` replaced with `--refresh-dependencies`, `--recompile-scripts` or `--rerun-tasks`.
- `--no-opt` replaced with `--rerun-tasks`.
- `--refresh` replaced with `--refresh-dependencies`.

### Removed deprecated classes

- `GradleLauncher` replaced by the tooling API.
- `Compile` replaced by `JavaCompile`.
- `org.gradle.api.tasks.Directory` with no replacement.
- `CodeQualityPlugin` replaced by the `checkstyle` and `codenarc` plugins.
- `GroovyCodeQualityPluginConvention` with no replacement.
- `JavaCodeQualityPluginConvention` with no replacement.
- `IllegalOperationAtExecutionTimeException` with no replacement.
- `AntJavadoc` with no replacement.
- `org.gradle.RefreshOptions` with no replacement.
- `org.gradle.tooling.model.eclipse.EclipseTask` with no replacement.
- `org.gradle.CacheUsage` with no replacement.
- `org.gradle.api.internal.plugins.ProcessResources` replaced by `org.gradle.language.jvm.tasks.ProcessResources`.
- `org.gradle.api.tasks.testing.TestLogging` replaced by `org.gradle.api.tasks.testing.logging.TestLogging`.

### Removed deprecated methods

- `Project.dir()` replaced with `mkdir()`
- `Project.dependsOn()` replaced with task dependencies.
- `Project.childrenDependOnMe()` replaced with task dependencies.
- `Project.dependsOnChildren()` replaced with task dependencies.
- `Project.getDependsOnProjects()` with no replacement.
- `Project.createTask()` replaced with `task()` or `getTasks().create()`.
- `Project.fileTree(Closure)` replaced with `fileTree(Object)`.
- `Script.fileTree(Closure)` replaced with `fileTree(Object)`.
- `SourceSetContainer.add()` replaces with `create()`.
- `Test.isTestReport()` replaced with `getReports().getHtml().isEnabled()`.
- `Test.disableTestReport()` replaced with `getReports().getHtml().setEnabled()`.
- `Test.enableTestReport()` replaced with `getReports().getHtml().setEnabled()`.
- `Test.setTestReport()` replaced with `getReports().getHtml().setEnabled()`.
- `Test.getTestResultsDir()` replaced with `getReports().getJunitXml().getDestination()`.
- `Test.setTestResultsDir()` replaced with `getReports().getJunitXml().setDestination()`.
- `Test.getTestReportDir()` replaced with `getReports().getHtml().getDestination()`.
- `Test.setTestReportDir()` replaced with `getReports().getHtml().setDestination()`.
- `CompileOptions.getFailOnError()` replaced with `isFailOnError()`
- `CompileOptions.getDebug()` replaced with `isDebug()`
- `StartParameter.getCacheUsage()` replaced with `isRecompileScripts()` and `isRerunTasks()` and `isRefreshDependencies()`.
- `StartParameter.setCacheUsage()` replaced with `setRecompileScripts()` and `setRerunTasks()` and `setRefreshDependencies()`.
- `StartParameter.isNoOpt()` replaced with `isRerunTasks()`.
- `StartParameter.setNoOpt()` replaced with `setRerunTasks()`.
- `StartParameter.getRefreshOptions()` replaced with `getRefreshDependencies()`.
- `StartParameter.setRefreshOptions()` replaced with `setRefreshDependencies()`.
- `StartParameter.useEmptySettingsScript()` replaced with `useEmptySettings()`.
- `StartParameter.getMergedSystemProperties()` with no replacement.
- `Specs.filterIterable()` with no replacement.
- `ExcludeRule.getExcludeArgs()` replaced by `getModule()` and `getGroup()`.
- `ExtensionContainer.add(String, Class<?>, Object...)` replaced by `create()`
- `TaskContainer.add()` replaced by `create()`.
- `ConfigurationContainer.add()` replaced by `create()`.
- `RepositoryHandler.mavenRepo()` replaced by `maven()`.
- `ArtifactRepositoryContainer.add(DependencyResolver)` replaced with `add(ArtifactRepository)`.
- `ArtifactRepositoryContainer.addLast(Object)` replaced with `add(ArtifactRepository)` or `maven()`.
- `ArtifactRepositoryContainer.addFirst()` with no replacement.
- `ArtifactRepositoryContainer.addBefore()` with no replacement.
- `ArtifactRepositoryContainer.addAfter()` with no replacement.
- `ArtifactRepositoryContainer.getResolvers()` with no replacement.
- `GenerateEclipseClasspath.containers()` replaced with `eclipse.classpath.containers()`.
- `GenerateEclipseClasspath.variables()` replaced with `eclipse.pathVariables()`.
- `GenerateEclipseWtpComponent.resources()` replaced with `eclipse.wtp.component.resource()`.
- `GenerateEclipseWtpComponent.properties()` replaced with `eclipse.wtp.component.property()`.
- `GenerateEclipseWtpComponent.variables()` replaced with `eclipse.pathVariables()`.

### Removed deprecated properties

- `UnresolvedDependency.id` replaced with `selector`.
- `AbstractCopyTask.defaultSource` replaced with `source`.
- `SourceTask.defaultSource` replaced with `source`.
- `JavaPluginConvention.metaInf` replaced with `Jar.metaInf`.
- `JavaPluginConvention.manifest` replaced with `Jar.manifest`.
- `Compression.extension` replaced with `defaultExtension`.
- `CompileOptions.compiler` replaced with `CompileOptions.fork` and `CompileOptions.forkOptions.executable`
- `CompileOptions.useAnt` with no replacement.
- `CompileOptions.optimize` with no replacement.
- `CompileOptions.includeJavaRuntime` with no replacement.
- `GroovyCompileOptions.useAnt` with no replacement.
- `GroovyCompileOptions.stacktrace` with no replacement.
- `GroovyCompileOptions.includeJavaRuntime` with no replacement.
- `Checkstyle.properties` replaced with `Checkstyle.configProperties`.
- `Checkstyle.resultFile` replaced with `Checkstyle.reports.xml.destination`.
- `CodeNarc.reportFormat` replaced with `CodeNarc.reports.<type>.enabled`.
- `CodeNarc.reportFile` replaced with `CodeNarc.reports.<type>.destination`.
- `ResolvedArtifact.resolvedDependency` with `ResolvedArtifact.moduleVersion` as a partial replacement.
- `ArtifactsRepositoryContainer` constants with no replacement.
- `GenerateEclipseClasspath.sourceSets` replaced with `eclipse.classpath.sourceSets`.
- `GenerateEclipseClasspath.plusConfigurations` replaced with `eclipse.classpath.plusConfigurations`.
- `GenerateEclipseClasspath.minusConfigurations` replaced with `eclipse.classpath.minusConfigurations`.
- `GenerateEclipseClasspath.containers` replaced with `eclipse.classpath.containers`.
- `GenerateEclipseClasspath.defaultOutputDir` replaced with `eclipse.classpath.defaultOutputDir`.
- `GenerateEclipseClasspath.downloadSources` replaced with `eclipse.classpath.downloadSources`.
- `GenerateEclipseClasspath.downloadJavadoc` replaced with `eclipse.classpath.downloadJavadoc`.
- `GenerateEclipseClasspath.variables` replaced with `eclipse.pathVariables`.
- `GenerateEclipseWtpComponent.contextPath` replaced with `eclipse.wtp.component.contextPath`.
- `GenerateEclipseWtpComponent.deployName` replaced with `eclipse.wtp.component.deployName`.
- `GenerateEclipseWtpComponent.sourceDirs` replaced with `eclipse.wtp.component.sourceDirs`.
- `GenerateEclipseWtpComponent.plusConfigurations` replaced with `eclipse.wtp.component.plusConfigurations`.
- `GenerateEclipseWtpComponent.minusConfigurations` replaced with `eclipse.wtp.component.minusConfigurations`.
- `GenerateEclipseWtpComponent.resources` replaced with `eclipse.wtp.component.resources`.
- `GenerateEclipseWtpComponent.properties` replaced with `eclipse.wtp.component.properties`.
- `GenerateEclipseWtpComponent.variables` replaced with `eclipse.pathVariables`.

### Changes to file DSL

The `Project.file()` method no longer accepts arbitrary inputs. Previously, this method would attempt to convert the result of calling `toString()`
on its parameter, if the parameter type was not recognized. This is now an error.

This method is used indirectly in many places in the Gradle DSL.

The `CopySpec.into()` method also no longer accepts arbitrary inputs. Previously, this method would attempt to conver the result of calling `toString()` on
its parameter, if the parameter type was not recognized. This is now an error.

The `Project.tarTree()` and `zipTree()` methods no longer ignores missing files. This is now an error.

### Changes to repository DSL

- The `RepositoryHandler.mavenCentral()` method no longer handles the `urls` option. You should use the `artifactUrls` instead.
- It is now an error to change the name of an `ArtifactRepository` after it has been added to a repository container.
- The `RepositoryHandler.mavenLocal()` method no longer supports the `M2_HOME` system properties. You should use the `M2_HOME` environment variable instead.

### Removed incubating method

- `StartParameter.setProjectPath` and `StartParameter.getProjectPath` were replaced with `TaskParameter`.

### Task constructor injection changes

Tasks are now constructed according to JSR-330. This means that a task type must either have a single public zero-args constructor, or annotate one constructor with `@Inject`.

Previously, Gradle would accept a class with a single constructor with multiple parameters that was not annotated with `@Inject`. This was deprecated in Gradle 1.2 and is now an error.

### Task constructor changes

All core Gradle task types now have a zero args constructor. The following types are affected:

- `org.gradle.api.tasks.testing.Test`
- `org.gradle.api.tasks.Upload`
- `org.gradle.api.plugins.quality.Checkstyle`
- `org.gradle.api.plugins.quality.CodeNarc`
- `org.gradle.api.plugins.quality.FindBugs`
- `org.gradle.api.plugins.quality.Pmd`
- `org.gradle.api.plugins.quality.JDepend`
- `org.gradle.testing.jacoco.tasks.JacocoReport`
- `org.gradle.api.tasks.GradleBuild`
- `org.gradle.api.tasks.diagnostics.DependencyInsightReportTask`
- `org.gradle.api.reporting.GenerateBuildDashboard`
- `org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor`
- `org.gradle.api.publish.maven.tasks.GenerateMavenPom`
- `org.gradle.api.publish.maven.tasks.PublishToMavenRepository`
- `org.gradle.api.publish.maven.tasks.PublishToMavenLocal`
- `org.gradle.nativebinaries.tasks.InstallExecutable`
- `org.gradle.api.plugins.buildcomparison.gradle.CompareGradleBuilds`

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Marcin Erdmann](https://github.com/erdi) - Support an ivy repository declared with 'sftp' as the URL scheme
* [Lukasz Kryger](https://github.com/kryger) - Documentation improvements
* [Ben McCann](https://github.com/benmccann) - Added named 'ivy' layout to 'ivy' repositories
* [Alex Selesse](https://github.com/selesse) - Fixed announce plugin in headless mode on OS X

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
