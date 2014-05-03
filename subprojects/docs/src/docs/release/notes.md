## New and noteworthy

Here are the new features introduced in this Gradle release.

### New API for artifact resolution (i)

Gradle 2.0 introduces a new, incubating API for resolving component artifacts. With this addition, Gradle now offers separate dedicated APIs for resolving
components and artifacts. (Component resolution is mainly concerned with computing the dependency graph, whereas artifact resolution is
mainly concerned with locating and downloading artifacts.) The entry points to the component and artifact resolution APIs are `configuration.incoming` and
`dependencies.createArtifactResolutionQuery()`, respectively.

TODO: This API examples are out of date. Add new tested samples to the Javadoc or Userguide and link instead

Here is an example usage of the new API:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
        .forArtifacts(JvmLibrary)

    def result = query.execute() // artifacts are downloaded at this point

    for (component in result.components) {
        assert component instanceof JvmLibrary
        println component.id
        component.sourceArtifacts.each { println it.file }
        component.javadocArtifacts.each { println it.file }
    }

    assert result.unresolvedComponents.isEmpty()

Artifact resolution can be limited to selected artifact types:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponent("org.springframework", "spring-core", "3.2.3.RELEASE")
        .forArtifacts(JvmLibrary, JvmLibrarySourcesArtifact)

    def result = query.execute()

    for (component in result.components) {
        assert !component.sourceArtifacts.isEmpty()
        assert component.javadocArtifacts.isEmpty()
    }

Artifacts for many components can be resolved together:

    def query = dependencies.createArtifactResolutionQuery()
        .forComponents(setOfComponentIds)
        .forArtifacts(JvmLibrary)

So far, only one component type (`JvmLibrary`) is available, but others will follow, also for platforms other than the JVM.

### Accessing Ivy extra info from component metadata rules

It's now possible to access Ivy extra info from component metadata rules. Roughly speaking, Ivy extra info is a set of user-defined
key-value pairs published in the Ivy module descriptor. Rules wishing to access the extra info need to specify a parameter of type
`IvyModuleDescriptor`. Here is an example:

    dependencies {
        components {
            eachComponent { component, IvyModuleDescriptor descriptor ->
                println descriptor.extraInfo["expired"] // TODO: what's a real-world use case?
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

### Support for adding target platform specific configurations in native binary projects (Gcc based toolchains)

When declaring a toolchain, the targetted platforms can be configured directly in the toolChain model. Furthermore target platform specific configurations
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

See the [User Guide](userguide/dependency_management.html#N150B8) and the
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

### Custom TestNG listeners are applied before Gradle's listeners

This way the custom listeners are more robust because they can affect the test status.
There should be no impact of this change because majority of users do not employ custom listeners
and even if they do healthy listeners will work correctly regardless of the listeners' order.

### Support for reading or setting file permissions on certain platforms with Java 5 or 6

Gradle previously supported file permissions on Solaris and Linux ia-64 using Java 5 and Java 6. This support has
been removed. You will receive a warning when attempting to use file permissions on these platforms.

Note that file permissions are supported on these platforms when you use Java 7 and later, and is supported for all Java
versions on Linux, OS X, Windows and FreeBSD for x86 and amd64 architectures.

### Support for terminal integration on certain platforms

Gradle previously supported terminal integration on Solaris and Linux ia-64. This support has been removed. When you use Gradle on these
platforms, Gradle will fall back to using plain text output.

Note that terminal integration is supported on Linux, OS X, Windows and FreeBSD for x86 and amd64 architectures.

If you wish to have terminal integration on other platforms and architectures, please help us out with porting our
native integration to these platforms.

### Build scripts must be encoded using UTF-8

Gradle now assumes that all Gradle scripts are encoded using UTF-8. Previously, Gradle assumes the system encoding. This change
affects all build scripts, settings scripts and init scripts.

### Support for the Gradle Open API removed

- `GradleRunnerFactory` removed.
- `UIFactory` removed.
- `ExternalUtility` removed.

### Removed Deprecated Plugins

- `code-quality` plugin replaced by `checkstyle` and `codenarc`.

### Removed Deprecated Classes

- `GradleLauncher` replaced by the tooling API.
- `Compile` replaced by `JavaCompile`.
- `CodeQualityPlugin` replaced by the `checkstyle` and `codenarc` plugins.
- `GroovyCodeQualityPluginConvention` with no replacement.
- `JavaCodeQualityPluginConvention` with no replacement.
- `IllegalOperationAtExecutionTimeException` with no replacement.
- `AntJavadoc` with no replacement.

### Removed Deprecated Methods

- `CompileOptions.getFailOnError()` replaced with `isFailOnError()`
- `CompileOptions.getDebug()` replaced with `isDebug()`

### Removed Deprecated Properties

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

### Removed incubating method

- `StartParameter.setProjectPath` and `StartParameter.getProjectPath` were replaced with `TaskParameter`.

### Task constructor changes

All task types now have a zero args constructor. The following types are affected:

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
