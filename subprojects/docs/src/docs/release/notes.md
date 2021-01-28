The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

[Martin d'Anjou](https://github.com/martinda),
[Till Krullmann](https://github.com/tkrullmann),
[Andreas Axelsson](https://github.com/judgeaxl),
[Pedro Tôrres](https://github.com/t0rr3sp3dr0),
[Stefan Oehme](https://github.com/oehme),
[Niels Doucet](https://github.com/NielsDoucet)

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

## Performance improvements for incremental development

This release contains further improvements for incremental development &mdash; the part of the software development process where you make frequent small changes.
These performance improvements are more pronounced for Android builds.

For example, `assembleDebug` for a non-abi change on the Santa Tracker Android project improved by 15% compared to Gradle 6.7:

<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAlgAAAGFCAYAAAAhLo2wAAAs50lEQVR42u3dB5QV133Hcaf33nvv/Ti9N9tx7LgmcRLbiR23xClOA9M7CIHAwggBwgIjIzqWhCQLLIokREcg0wSiSjSBRBNlYXfRjf5Xmue3b9u8XbC94vM953d2p7wyM3dmvnPvnXmvSgAAALiqvMoqAAAAIFgAAAAECwAAgGABAACAYAEAABAsAAAAggUAAACCBQAAQLAAAAAIFgAAAAgWAAAAwQIAACBYAAAABAsAAAAECwAAgGABAAAQLAAAABAsAAAAggUAAECwAAAAQLAAAAAIFgBcVS5evGglACBY6Dr/9E//lF71qlelLVu2WBlf4HW+bdu2Tue94YYb8rwLFizo8cvSUxg7dmxeprlz51636+B62b8AgvVF4Cu+4ivSr/zKr/TY9ydYBCuYM2dOfn2RL/uyL0vf+73fm/7sz/4sfepTn0rNzc1OZjXceuut6eu//uvTPffcU2qfvZ5O6EOHDs3L2rt379JlLhLrLsrdW97ylrR69eoW869duzbP8453vINggWARLIKFniVYv/mbv5n+93//Nyc+/4d/+Ifz+D/90z9Nzz33nJNZN/bZ62UdvPDCC+lHf/RH87J+z/d8T2pqaipd5v793/89ve51r0tf/uVfnr7yK78yffaznyVYAMEiWOj5gvXRj360xfg4OQ4aNChPe8Mb3uBkRrA65cEHH8zLGesg/t511111lblg4cKFedrv/d7vESzgWgvWHXfckavjo1q+mp07d6a//uu/Tt///d+fvvEbvzH92q/9WrrttttaNWns2bMnvfe9700/8AM/kL7ma74m/dRP/VTq1atXOnv2bJs7Xcz/P//zP+n7vu/70jd8wzfkq6zly5dX5osrs9rq7Ve/+tVd+sy26Oz9i+8Z3+nP//zP09d+7demm2++uUufHR11Bw8enOeJeWOZP/ShD6Vnn322Q8E6cuRI+sEf/MH0dV/3dS2q869cuZK/y8///M/n94tt88EPfjCdOHGizXXd1jK0t73rWa6yZaPsfGWX653vfGflwB3L+F3f9V152X7jN34jLVu2LM8zbdq09Iu/+It5/I//+I/nppTaDtPF+omr+He/+93pO77jO/K6jpPOqlWrSglW2e/c0cku+Ju/+Zs8/YEHHujS+9ezLG9/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"
alt=":santa-tracker:assembleDebug with non-abi change performance improvements"
/>

File system watching and configuration caching is enabled for the comparison.

You can find the performance test project [here](https://github.com/gradle/santa-tracker-performance).

## Build reliability improvements

Gradle employs a number of optimizations to ensure that builds are executed as fast as possible.
These optimizations rely on the inputs and outputs of tasks to be well-defined.
Gradle already applies some validation to tasks to check whether they are well-defined.

### Disable optimizations for validation problems

If a task is found to be invalid, Gradle will now execute it without the benefit of parallel execution, up-to-date checks and the build cache.
For more information see the [user manual on runtime validation](userguide/more_about_tasks.html#sec:task_input_validation).

### Validate missing dependencies between tasks

One of the potential problems now flagged is a task that consumes the output produced by another without declaring an [explicit or inferred task dependency](userguide/more_about_tasks.html#sec:link_output_dir_to_input_files).
Gradle now detects the missing dependency between the consumer and the producer and emits a warning in that case.
For more information see the [user manual on input and output validation](userguide/more_about_tasks.html#sec:task_input_output_validation). 

## Plugin development improvements

### Included plugin builds

Developing plugins as part of a composite build was so far only possible for project plugins.
Settings plugins always had to be developed in isolation and published to a binary repository.

This release introduces a new DSL construct in the settings file for including plugin builds.
Build included like that can provide both project and settings plugins.
```
pluginManagement {
    includeBuild("../my-settings-plugin")
}
plugins {
    id("my.settings-plugin") 
}
```
The above example assumes that the included build defines a settings plugin with the id `my.settings-plugin`.

Library components produced by builds included though the `pluginManagement` block are not automatically visible to the including build.
However, the same build can be included as plugin build and normal library build:
```
pluginManagement {
    // contributes plugins
    includeBuild("../project-with-plugin-and-library") 
}
// contributes libraries
includeBuild("../project-with-plugin-and-library") 
```
This distinction reflects what Gradle offers for repository declarations - 
repositories are specified separately for plugin dependencies and for production dependencies.

## General improvements

### Ignore empty `buildSrc` project

In earlier Gradle versions, the mere presence of a `buildSrc` directory was enough to trigger Gradle to execute all `buildSrc` tasks and to add the resulting `buildSrc.jar` to the buildscript class path.
Gradle will now ignore an empty `buildSrc` directory, and will only generate a `buildSrc.jar` if build files and/or source files are detected.

This has two benefits when an empty `buildSrc` directory is detected:
- `:buildSrc:*` tasks will not be needlessly executed.
- The empty `buildSrc.jar` will not be added to the buildscript class path, avoiding cache misses that this can cause.

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
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details 

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv



^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

### Java Module Support

[Compiling](userguide/java_library_plugin.html#sec:java_library_modular),
[testing](userguide/java_testing.html#sec:java_testing_modular) and
[executing](userguide/application_plugin.html#sec:application_modular)
Java modules is now a stable feature.
It is no longer required to activate the functionality using `java.modularity.inferModulePath.set(true)`.

### Dependency Verification

[Dependency verification](userguide/dependency_verification.html) is promoted to a stable feature.

### Java Toolchain

[Java Toolchains](userguide/toolchains.html) is promoted to a stable feature.

### Changing the priority of the daemon process

Changing the [priority of the daemon process](userguide/command_line_interface.html#sec:command_line_performance) with `--priority` is now a stable feature.

### Promoted APIs
In Gradle 7.0 we moved the following classes or methods out of incubation phase.

- Core
    - [Services](userguide/custom_gradle_types.html#service_injection)
        - org.gradle.api.file.ArchiveOperations
        - org.gradle.api.file.FileSystemOperations
        - org.gradle.process.ExecOperations
    - [Lazy configuration](userguide/lazy_configuration.html)
        - org.gradle.api.model.ObjectFactory.directoryProperty()
        - org.gradle.api.model.ObjectFactory.domainObjectContainer(Class)
        - org.gradle.api.model.ObjectFactory.domainObjectContainer(Class, NamedDomainObjectFactory)
        - org.gradle.api.model.ObjectFactory.domainObjectSet(Class)
        - org.gradle.api.model.ObjectFactory.fileCollection()
        - org.gradle.api.model.ObjectFactory.fileProperty()
        - org.gradle.api.model.ObjectFactory.fileTree()
        - org.gradle.api.model.ObjectFactory.namedDomainObjectList(Class<T>)
        - org.gradle.api.model.ObjectFactory.namedDomainObjectSet(Class<T>)
        - org.gradle.api.model.ObjectFactory.polymorphicDomainObjectContainer(Class<T>)
        - org.gradle.api.model.ObjectFactory.sourceDirectorySet(String, String)
        - org.gradle.api.file.FileCollection.getElements
        - org.gradle.api.file.FileContents
        - org.gradle.api.provider.ProviderFactory.credentials(Class, String)
        - org.gradle.api.provider.ProviderFactory.credentials(Class, Provider<String>)
        - org.gradle.api.provider.ProviderFactory.environmentVariable()
        - org.gradle.api.provider.ProviderFactory.fileContents()
        - org.gradle.api.provider.ProviderFactory.gradleProperty()
        - org.gradle.api.provider.ProviderFactory.systemProperty()
        - org.gradle.api.provider.ProviderFactory.zip(Provider, Provider, BiFunction)
        - org.gradle.api.provider.Provider.flatMap(Transformer)
        - org.gradle.api.provider.Provider.forUseAtConfigurationTime()
        - org.gradle.api.provider.Provider.orElse(T)
        - org.gradle.api.provider.Provider.orElse(org.gradle.api.provider.Provider<? extends T>)
        - org.gradle.api.provider.Provider.zip(Provider, BiFunction)
        - org.gradle.api.provider.HasConfigurableValue
        - org.gradle.api.provider.Property.value(T)
        - org.gradle.api.provider.Property.value(org.gradle.api.provider.Provider<? extends T>)
        - org.gradle.api.provider.Property.convention(T)
        - org.gradle.api.provider.Property.convention(org.gradle.api.provider.Provider<? extends T>)
        - org.gradle.api.provider.HasMultipleValues.empty
        - org.gradle.api.provider.HasMultipleValues.value(java.lang.Iterable<? extends T>)
        - org.gradle.api.provider.HasMultipleValues.value(org.gradle.api.provider.Provider<? extends java.lang.Iterable<? extends T>>)
        - org.gradle.api.provider.HasMultipleValues.convention(java.lang.Iterable<? extends T>)
        - org.gradle.api.provider.HasMultipleValues.convention(org.gradle.api.provider.Provider<? extends java.lang.Iterable<? extends T>>)
        - org.gradle.api.provider.MapProperty
        - org.gradle.api.file.FileSystemLocationProperty.getLocationOnly
        - org.gradle.api.file.FileSystemLocationProperty.fileValue
        - org.gradle.api.file.FileSystemLocationProperty.fileProvider
        - org.gradle.api.file.Directory.files
        - org.gradle.api.file.DirectoryProperty.files
    - [Worker API](userguide/worker_api.html)
        - org.gradle.workers.ClassLoaderWorkerSpec
        - org.gradle.workers.ForkingWorkerSpec
        - org.gradle.workers.ProcessWorkerSpec
        - org.gradle.workers.WorkAction
        - org.gradle.workers.WorkParameters
        - org.gradle.workers.WorkParameters.None
        - org.gradle.workers.WorkQueue
        - org.gradle.workers.WorkerExecutor.submit(Class actionClass, Action);
        - org.gradle.workers.WorkerExecutor.classLoaderIsolation()
        - org.gradle.workers.WorkerExecutor.classLoaderIsolation(Action)
        - org.gradle.workers.WorkerExecutor.noIsolation()
        - org.gradle.workers.WorkerExecutor.noIsolation(Action)
        - org.gradle.workers.WorkerExecutor.processIsolation()
        - org.gradle.workers.WorkerExecutor.processIsolation(Action)
        - org.gradle.workers.WorkerSpec
    - [Composite Builds](userguide/composite_builds.html)
        - org.gradle.api.initialization.ConfigurableIncludedBuild.setName(String)
        - org.gradle.api.tasks.GradleBuild.getBuildName()
        - org.gradle.api.tasks.GradleBuild.setBuildName(String)
    - [Build Caching](userguide/build_cache.html)
        - org.gradle.normalization.RuntimeClasspathNormalization.metaInf(Action)
        - org.gradle.normalization.MetaInfNormalization
        - org.gradle.caching.BuildCacheKey.toByteArray
    - Reporting
        - org.gradle.api.reporting.Report.getOutputLocation()
        - org.gradle.api.reporting.Report.getRequired()
        - org.gradle.api.tasks.diagnostics.TaskReportTask.getDisplayGroup()
        - org.gradle.api.tasks.diagnostics.TaskReportTask.setDisplayGroup(String)
    - Miscellaneous
        - org.gradle.buildinit.tasks.InitBuild.getSplitProject()
        - org.gradle.api.Generated
        - org.gradle.api.JavaVersion.VERSION_15
        - org.gradle.api.JavaVersion.VERSION_16
        - org.gradle.api.JavaVersion.VERSION_17
        - org.gradle.api.JavaVersion.isJava12
        - org.gradle.api.JavaVersion.isJava12Compatible
        - org.gradle.api.JavaVersion.isCompatibleWith
        - org.gradle.api.ProjectConfigurationException
        - org.gradle.api.invocation.BuildInvocationDetails
        - org.gradle.api.invocation.Gradle.beforeSettings(Action)
        - org.gradle.api.invocation.Gradle.beforeSettings(Closure)
        - org.gradle.api.logging.WarningMode.Fail
        - org.gradle.api.reflect.TypeOf.getConcreteClass()
        - org.gradle.api.resources.TextResourceFactory.fromInsecureUri(Object)
        - org.gradle.api.tasks.AbstractExecTask.getExecutionResult()  
        - org.gradle.plugin.management.PluginManagementSpec.getPlugins()
        - org.gradle.plugin.management.PluginManagementSpec.plugins(Action)  
        - org.gradle.testkit.runner.GradleRunner.getEnvironment()
        - org.gradle.testkit.runner.GradleRunner.withEnvironment(Map<String, String>)
        - org.gradle.api.reflect.InjectionPointQualifier
        - org.gradle.api.file.FileType
        - org.gradle.api.model.ReplacedBy
        - org.gradle.api.Task.getTimeout
        - org.gradle.process.JavaDebugOptions
        - org.gradle.process.JavaForkOptions.getDebugOptions()
        - org.gradle.process.JavaForkOptions.debugOptions(Action)
        - org.gradle.api.tasks.WorkResult.or
        - org.gradle.api.tasks.IgnoreEmptyDirectories
        - org.gradle.api.tasks.TaskInputFilePropertyBuilder.ignoreEmptyDirectories()
        - org.gradle.api.tasks.TaskInputFilePropertyBuilder.ignoreEmptyDirectories(boolean)
        - org.gradle.normalization.PropertiesFileNormalization
        - org.gradle.normalization.RuntimeClasspathNormalization.properties(java.lang.String, org.gradle.api.Action<? super org.gradle.normalization.PropertiesFileNormalization>)
        - org.gradle.normalization.RuntimeClasspathNormalization.properties(org.gradle.api.Action<? super org.gradle.normalization.PropertiesFileNormalization>)
        - org.gradle.api.file.DuplicatesStrategy.INHERIT
- Dependency management
    - Dependency notations
        - org.gradle.api.artifacts.dsl.DependencyHandler.enforcedPlatform(java.lang.Object)
        - org.gradle.api.artifacts.dsl.DependencyHandler.enforcedPlatform(java.lang.Object, org.gradle.api.Action<? super org.gradle.api.artifacts.Dependency>)
        - org.gradle.api.artifacts.dsl.DependencyHandler.testFixtures(java.lang.Object)
        - org.gradle.api.artifacts.dsl.DependencyHandler.testFixtures(java.lang.Object, org.gradle.api.Action<? super org.gradle.api.artifacts.Dependency>)
    - [Capabilities resolution](userguide/dependency_capability_conflict#sec:handling-mutually-exclusive-deps)
        - org.gradle.api.artifacts.CapabilitiesResolution
        - org.gradle.api.artifacts.CapabilityResolutionDetails
        - org.gradle.api.artifacts.ResolutionStrategy.capabilitiesResolution
        - org.gradle.api.artifacts.ResolutionStrategy.getCapabilitiesResolution
    - [Resolution strategy tuning](userguide/resolution_strategy_tuning.html#reproducible)
        - org.gradle.api.artifacts.ResolutionStrategy.failOnDynamicVersions
        - org.gradle.api.artifacts.ResolutionStrategy.failOnChangingVersions
    - [Dependency locking improvements](userguide/dependency_locking.html#dependency-locking)
        - org.gradle.api.artifacts.ResolutionStrategy.deactivateDependencyLocking
        - org.gradle.api.artifacts.dsl.DependencyLockingHandler.unlockAllConfigurations
        - org.gradle.api.artifacts.dsl.DependencyLockingHandler.getLockMode
        - org.gradle.api.artifacts.dsl.DependencyLockingHandler.getLockFile
        - org.gradle.api.artifacts.dsl.DependencyLockingHandler.getIgnoredDependencies
        - org.gradle.api.artifacts.dsl.LockMode
        - org.gradle.api.initialization.dsl.ScriptHandler.dependencyLocking
        - org.gradle.api.initialization.dsl.ScriptHandler.getDependencyLocking
    - [Dependency verification](userguide/dependency_verification.html#verifying-dependencies)
        - org.gradle.api.artifacts.ResolutionStrategy.enableDependencyVerification
        - org.gradle.api.artifacts.ResolutionStrategy.disableDependencyVerification
        - org.gradle.StartParameter.getWriteDependencyVerifications
        - org.gradle.StartParameter.setWriteDependencyVerifications
        - org.gradle.StartParameter.setDependencyVerificationMode
        - org.gradle.StartParameter.getDependencyVerificationMode
        - org.gradle.StartParameter.setRefreshKeys
        - org.gradle.StartParameter.isRefreshKeys
        - org.gradle.StartParameter.isExportKeys
        - org.gradle.StartParameter.setExportKeys
        - org.gradle.api.artifacts.verification.DependencyVerificationMode
    - [Dependency constraints improvements](userguide/dependency_constraints.html#dependency-constraints)
        - org.gradle.api.artifacts.dsl.DependencyConstraintHandler.enforcedPlatform(java.lang.Object)
        - org.gradle.api.artifacts.dsl.DependencyConstraintHandler.enforcedPlatform(java.lang.Object, org.gradle.api.Action<? super org.gradle.api.artifacts.DependencyConstraint>)
        - org.gradle.api.artifacts.result.ComponentSelectionCause.BY_ANCESTOR
    - [Component metadata rules improvements](userguide/component_metadata_rules.html#sec:component_metadata_rules)
        - org.gradle.api.artifacts.DirectDependencyMetadata.endorseStrictVersions
        - org.gradle.api.artifacts.DirectDependencyMetadata.doNotEndorseStrictVersions
        - org.gradle.api.artifacts.DirectDependencyMetadata.isEndorsingStrictVersions
        - org.gradle.api.artifacts.DirectDependencyMetadata.getArtifactSelectors
        - org.gradle.api.artifacts.ModuleDependency.endorseStrictVersions
        - org.gradle.api.artifacts.ModuleDependency.doNotEndorseStrictVersions
        - org.gradle.api.artifacts.ModuleDependency.isEndorsingStrictVersions
        - org.gradle.api.artifacts.ComponentMetadataDetails.maybeAddVariant
    - [Repositories](userguide/declaring_repositories.html#declaring-repositories)
        - org.gradle.api.artifacts.repositories.IvyArtifactRepository.getMetadataSources
        - org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources.isGradleMetadataEnabled
        - org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources.isIvyDescriptorEnabled
        - org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources.isArtifactEnabled
        - org.gradle.api.artifacts.repositories.IvyArtifactRepository.MetadataSources.isIgnoreGradleMetadataRedirectionEnabled
        - org.gradle.api.artifacts.repositories.MavenArtifactRepository.getMetadataSources
        - org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources.isGradleMetadataEnabled
        - org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources.isMavenPomEnabled
        - org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources.isArtifactEnabled
        - org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources.isIgnoreGradleMetadataRedirectionEnabled
    - [Dependency substitution improvements](resolution_rules.html#sec:dependency_substitution_rules)
        - org.gradle.api.artifacts.ArtifactSelectionDetails
        - org.gradle.api.artifacts.DependencyArtifactSelector
        - org.gradle.api.artifacts.DependencyResolveDetails.artifactSelection
        - org.gradle.api.artifacts.DependencySubstitution.artifactSelection
        - org.gradle.api.artifacts.DependencySubstitutions.variant
        - org.gradle.api.artifacts.DependencySubstitutions.platform
        - org.gradle.api.artifacts.DependencySubstitutions.Substitution.withClassifier
        - org.gradle.api.artifacts.DependencySubstitutions.Substitution.withoutClassifier
        - org.gradle.api.artifacts.DependencySubstitutions.Substitution.withoutArtifactSelectors
        - org.gradle.api.artifacts.DependencySubstitutions.Substitution.using
        - org.gradle.api.artifacts.VariantSelectionDetails
    - [Publishing](publishing_setup.html#publishing_components)
        - org.gradle.api.publish.Publication.withoutBuildIdentifier
        - org.gradle.api.publish.Publication.withBuildIdentifier
        - org.gradle.plugins.signing.SigningExtension.useInMemoryPgpKeys(String, String, String)
        - org.gradle.plugins.signing.SigningExtension.useInMemoryPgpKeys(String, String)
        - org.gradle.plugins.signing.Sign.getSignaturesByKey()
    - Miscellaneous
        - org.gradle.api.artifacts.result.ResolutionResult.getRequestedAttributes
        - org.gradle.api.artifacts.result.ResolvedComponentResult.getDependenciesForVariant
        - org.gradle.api.artifacts.result.ResolvedDependencyResult.getResolvedVariant
        - org.gradle.api.artifacts.ComponentVariantIdentifier
        - org.gradle.api.artifacts.maven.PomModuleDescriptor
        - org.gradle.api.artifacts.repositories.AuthenticationSupported.credentials(java.lang.Class<? extends org.gradle.api.credentials.Credentials>)
        - org.gradle.jvm.JvmLibrary
        - org.gradle.language.base.artifact.SourcesArtifact
        - org.gradle.language.java.artifact.JavadocArtifact
- IDE
    - Eclipse plugin
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.getPublication()
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.setPublication(FileReference)
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.getPublicationSourcePath()
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.setPublicationSourcePath(FileReference)
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.getPublicationJavadocPath()
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.setPublicationJavadocPath(FileReference)
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.getBuildDependencies()
      - org.gradle.plugins.ide.eclipse.model.ProjectDependency.buildDependencies(Object...)
- Tooling API
    - Eclipse models
        - org.gradle.plugins.ide.eclipse.model.UnresolvedLibrary
        - org.gradle.tooling.model.eclipse.EclipseRuntime
        - org.gradle.tooling.model.eclipse.EclipseWorkspace
        - org.gradle.tooling.model.eclipse.EclipseWorkspaceProject
        - org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies
        - org.gradle.tooling.model.eclipse.EclipseExternalDependency.isResolved()
        - org.gradle.tooling.model.eclipse.EclipseExternalDependency.getAttemptedSelector()
        - org.gradle.tooling.model.ComponentSelector
    - Testing events
        - org.gradle.tooling.events.OperationType.TestOutput
        - org.gradle.tooling.events.test.Destination
        - org.gradle.tooling.events.test.TestOutputDescriptor
        - org.gradle.tooling.events.test.TestOutputEvent
    - Miscellaneous
        - org.gradle.tooling.events.OperationCompletionListener
        - org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent
    - Debugging
        - org.gradle.tooling.TestLauncher.withTaskAndTestClasses(String, Iterable)
        - org.gradle.tooling.TestLauncher.withTaskAndTestMethods(String, String, Iterable)
        - org.gradle.tooling.TestLauncher.debugTestsOn(int)
- Java Ecosystem
    - Antlr plugin
        - org.gradle.api.plugins.antlr.AntlrTask.getStableSources
        - org.gradle.api.plugins.antlr.AntlrTask.execute
    - Java plugins
        - org.gradle.api.file.SourceDirectorySet.getDestinationDirectory()
        - org.gradle.api.file.SourceDirectorySet.getClassesDirectory()
        - org.gradle.api.file.SourceDirectorySet.compiledBy(TaskProvider<T>, Function<T, DirectoryProperty>)
        - org.gradle.api.tasks.compile.AbstractCompile.getDestinationDirectory()
        - org.gradle.api.plugins.FeatureSpec.withJavadocJar()
        - org.gradle.api.plugins.FeatureSpec.withSourcesJar()
        - org.gradle.api.plugins.JavaBasePlugin.COMPILE_CLASSPATH_PACKAGING_SYSTEM_PROPERTY
        - org.gradle.api.plugins.JvmEcosystemPlugin
        - org.gradle.api.plugins.JavaPluginExtension.withJavadocJar()
        - org.gradle.api.plugins.JavaPluginExtension.withSourcesJar()
        - org.gradle.api.tasks.JavaExec.getExecutionResult()
        - org.gradle.api.tasks.SourceSet.getCompileOnlyApiConfigurationName()
        - org.gradle.api.tasks.SourceSet.getJavadocElementsConfigurationName()
        - org.gradle.api.tasks.SourceSet.getJavadocJarTaskName()
        - org.gradle.api.tasks.SourceSet.getJavadocTaskName()
        - org.gradle.api.tasks.SourceSet.getSourcesElementsConfigurationName()
        - org.gradle.api.tasks.SourceSet.getSourcesJarTaskName()
        - org.gradle.api.tasks.SourceSetOutput.getGeneratedSourcesDirs()
        - org.gradle.api.tasks.compile.CompileOptions.getGeneratedSourceOutputDirectory()
        - org.gradle.api.tasks.compile.CompileOptions.getRelease()
        - org.gradle.api.tasks.compile.JavaCompile.getStableSources
        - org.gradle.api.tasks.compile.JavaCompile.compile(org.gradle.work.InputChanges)
    - Java Module System
        - org.gradle.api.jvm.ModularitySpec
        - org.gradle.api.plugins.JavaApplication.getMainModule()
        - org.gradle.api.plugins.JavaPluginExtension.getModularity()
        - org.gradle.api.tasks.compile.JavaCompile.getModularity()
        - org.gradle.api.tasks.compile.CompileOptions.getJavaModuleMainClass()
        - org.gradle.api.tasks.compile.CompileOptions.getJavaModuleVersion()
        - org.gradle.api.tasks.javadoc.Javadoc.getModularity()
        - org.gradle.external.javadoc.MinimalJavadocOptions.getModulePath()
        - org.gradle.external.javadoc.MinimalJavadocOptions.setModulePath()
        - org.gradle.external.javadoc.MinimalJavadocOptions.modulePath()
        - org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails.getModulePath()
        - org.gradle.jvm.application.tasks.CreateStartScripts.getMainClass()
        - org.gradle.jvm.application.tasks.CreateStartScripts.getMainModule()
        - org.gradle.jvm.application.tasks.CreateStartScripts.getModularity()
        - org.gradle.process.JavaExecSpec.getMainClass()
        - org.gradle.process.JavaExecSpec.getMainModule()
        - org.gradle.process.JavaExecSpec.getModularity()
    - Java Toolchains
        - org.gradle.api.tasks.JavaExec.getJavaLauncher()
        - org.gradle.api.tasks.compile.JavaCompile.getJavaCompiler()
        - org.gradle.api.tasks.javadoc.Javadoc.getJavadocTool()
        - org.gradle.api.tasks.testing.Test.getJavaLauncher()
        - org.gradle.jvm.toolchain.JavaCompiler
        - org.gradle.jvm.toolchain.JavaInstallationMetadata
        - org.gradle.jvm.toolchain.JavaLanguageVersion
        - org.gradle.jvm.toolchain.JavaLauncher
        - org.gradle.jvm.toolchain.JavaToolchainService
        - org.gradle.jvm.toolchain.JavaToolchainSpec
        - org.gradle.jvm.toolchain.JavadocTool
        - org.gradle.jvm.toolchain.JvmImplementation
        - org.gradle.jvm.toolchain.JvmVendorSpec
        - org.gradle.api.plugins.JavaPluginExtension.toolchain(action)
        - org.gradle.api.plugins.JavaPluginExtension.getToolchain()
        - org.gradle.api.tasks.compile.GroovyCompile.getJavaLauncher()
    - Testing
        - org.gradle.api.plugins.JavaTestFixturesPlugin
        - org.gradle.api.tasks.testing.JUnitXmlReport.getMergeReruns()
        - org.gradle.api.tasks.testing.Test.getStableClasspath()
        - org.gradle.api.tasks.testing.TestDescriptor.getDisplayName()
        - org.gradle.api.tasks.testing.TestFilter.excludeTestsMatching(String)
        - org.gradle.api.tasks.testing.TestFilter.excludeTest(String, String)
        - org.gradle.api.tasks.testing.TestFilter.getExcludePatterns()
        - org.gradle.api.tasks.testing.TestFilter.setExcludePatterns()
        - org.gradle.testing.jacoco.tasks.JacocoReport.getReportProjectName()
    - Groovy
        - org.gradle.api.tasks.compile.GroovyCompile.getAstTransformationClasspath()
        - org.gradle.api.tasks.compile.GroovyCompile.getSourceClassesMappingFile()
        - org.gradle.api.tasks.compile.GroovyCompileOptions.isParameters()
        - org.gradle.api.tasks.compile.GroovyCompileOptions.setParameters(boolean)
        - org.gradle.api.tasks.compile.GroovyCompile.getStableSources
    - Scala
        - org.gradle.api.plugins.scala.ScalaBasePlugin.SCALA_COMPILER_PLUGINS_CONFIGURATION_NAME
        - org.gradle.api.plugins.scala.ScalaPluginExtension
        - org.gradle.api.tasks.scala.ScalaCompile.getScalaCompilerPlugins()
        - org.gradle.api.tasks.scala.ScalaCompile.setScalaCompilerPlugins(FileCollection)
        - org.gradle.api.tasks.scala.ScalaDoc.getMaxMemory()
        - org.gradle.api.tasks.scala.IncrementalCompileOptions.getClassfileBackupDir()
    - Miscellaneous
        - org.gradle.api.plugins.quality.CodeNarc.getCompilationClasspath()
        - org.gradle.api.plugins.quality.CodeNarc.setCompilationClasspath(FileCollection)
        - org.gradle.api.plugins.quality.Pmd.getIncrementalAnalysis()
        - org.gradle.api.plugins.quality.Pmd.getIncrementalCacheFile()
        - org.gradle.api.plugins.quality.Pmd.getMaxFailures()
        - org.gradle.api.plugins.quality.PmdExtension.getMaxFailures()
        - org.gradle.plugins.ear.Ear.getGenerateDeploymentDescriptor()
        - org.gradle.plugins.ear.EarPluginConvention.getGenerateDeploymentDescriptor()
    
- [Kotlin DSL](userguide/kotlin_dsl.html)
    - org.gradle.kotlin.dsl.KotlinScript
    - org.gradle.kotlin.dsl.KotlinSettingsScript.plugins(block: PluginDependenciesSpecScope.() -> Unit): Unit
    - org.gradle.kotlin.dsl.KotlinSettingsScript.pluginManagement(block: PluginManagementSpec.() -> Unit): Unit
    - org.gradle.kotlin.dsl.ExtensionContainer.add(name: String, extension: T): Unit
    - org.gradle.kotlin.dsl.ExtensionContainer.create(name: String, vararg constructionArguments: Any): T
    - org.gradle.kotlin.dsl.ExtensionContainer.getByType(): T
    - org.gradle.kotlin.dsl.ExtensionContainer.findByType(): T?
    - org.gradle.kotlin.dsl.ExtensionContainer.configure(noinline action: T.() -> Unit)
    - org.gradle.kotlin.dsl.ArtifactHandler.invoke(configuration: ArtifactHandlerScope.() -> Unit): Unit
    - org.gradle.kotlin.dsl.ScriptHandler.dependencyLocking(configuration: DependencyLockingHandler.() -> Unit): Unit
    - org.gradle.kotlin.dsl.PluginDependenciesSpec.`gradle-enterprise`: PluginDependencySpec
    - org.gradle.tooling.model.kotlin.dsl.EditorPosition
    - org.gradle.tooling.model.kotlin.dsl.EditorReport
    - org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
    - org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
    - org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
    - org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
    
- Plugin development
    - org.gradle.plugin.devel.tasks.ValidatePlugins

- org.gradle.api.distribution.Distribution.getDistributionBaseName()

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
