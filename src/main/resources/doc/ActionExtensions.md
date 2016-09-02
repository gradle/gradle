This file holds the documentation for all extension members automatically generated
from their `Action<T>` counterparts in the Gradle API.

The documentation is merged in the final file by the `GenerateActionExtensions` task.

Parameter names for each generated extension are also taken from this file.

# org.gradle.api.invocation.Gradle.rootProject(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.invocation.Gradle.allprojects(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Script.javaexec(org.gradle.process.JavaExecSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Script.exec(org.gradle.process.ExecSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.TaskContainer.create(String, java.lang.Class<T>, T.() -> Unit)

Lorem ipsum.

@param name
@param type
@param action

# org.gradle.api.tasks.incremental.IncrementalTaskInputs.outOfDate(org.gradle.api.tasks.incremental.InputFileDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.incremental.IncrementalTaskInputs.removed(org.gradle.api.tasks.incremental.InputFileDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.Sync.preserve(org.gradle.api.tasks.util.PatternFilterable.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.bundling.AbstractArchiveTask.into(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param target
@param action

# org.gradle.api.tasks.AbstractCopyTask.filesMatching(String, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param pattern
@param action

# org.gradle.api.tasks.AbstractCopyTask.filesMatching(Iterable<String>, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param patterns
@param action

# org.gradle.api.tasks.AbstractCopyTask.filesNotMatching(String, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param pattern
@param action

# org.gradle.api.tasks.AbstractCopyTask.filesNotMatching(Iterable<String>, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param patterns
@param action

# org.gradle.api.tasks.AbstractCopyTask.from(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.tasks.AbstractCopyTask.into(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param target
@param action

# org.gradle.api.tasks.AbstractCopyTask.eachFile(org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Task.doFirst(org.gradle.api.Task.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Task.doLast(org.gradle.api.Task.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.delete(org.gradle.api.file.DeleteSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.javaexec(org.gradle.process.JavaExecSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.exec(org.gradle.process.ExecSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.subprojects(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.allprojects(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.beforeEvaluate(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.afterEvaluate(org.gradle.api.Project.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.configure(Iterable<T>, T.() -> Unit)

Lorem ipsum.

@param objects
@param action

# org.gradle.api.Project.copy(org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.Project.copySpec(org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.PluginAware.apply(org.gradle.api.plugins.ObjectConfigurationAction.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.ExtensionContainer.configure(java.lang.Class<T>, T.() -> Unit)

Lorem ipsum.

@param extensionType
@param action

# org.gradle.api.plugins.PluginManager.withPlugin(String, org.gradle.api.plugins.AppliedPlugin.() -> Unit)

Lorem ipsum.

@param pluginId
@param action

# org.gradle.api.plugins.PluginContainer.withId(String, org.gradle.api.Plugin<*>.() -> Unit)

Executes or registers an action for a plugin with given id.
If the plugin was already applied, the action is executed.
If the plugin is applied sometime later the action will be executed after the plugin is applied.
If the plugin is never applied, the action is never executed.
The behavior is similar to [org.gradle.api.plugins.PluginContainer.withType].

@param pluginId The id of the plugin
@param action The action

# org.gradle.api.artifacts.result.ResolutionResult.allDependencies(org.gradle.api.artifacts.result.DependencyResult.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.result.ResolutionResult.allComponents(org.gradle.api.artifacts.result.ResolvedComponentResult.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.Configuration.defaultDependencies(org.gradle.api.artifacts.DependencySet.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ResolvableDependencies.beforeResolve(org.gradle.api.artifacts.ResolvableDependencies.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ResolvableDependencies.afterResolve(org.gradle.api.artifacts.ResolvableDependencies.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.DependencySubstitutions.all(org.gradle.api.artifacts.DependencySubstitution.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.cache.ResolutionRules.eachDependency(org.gradle.api.artifacts.cache.DependencyResolutionControl.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.cache.ResolutionRules.eachModule(org.gradle.api.artifacts.cache.ModuleResolutionControl.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.cache.ResolutionRules.eachArtifact(org.gradle.api.artifacts.cache.ArtifactResolutionControl.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ComponentSelectionRules.all(org.gradle.api.artifacts.ComponentSelection.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ComponentSelectionRules.withModule(Any, org.gradle.api.artifacts.ComponentSelection.() -> Unit)

Lorem ipsum.

@param id
@param action

# org.gradle.api.artifacts.repositories.AuthenticationSupported.credentials(org.gradle.api.artifacts.repositories.PasswordCredentials.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.repositories.AuthenticationSupported.credentials(java.lang.Class<T>, T.() -> Unit)

Lorem ipsum.

@param type
@param action

# org.gradle.api.artifacts.repositories.AuthenticationSupported.authentication(org.gradle.api.artifacts.repositories.AuthenticationContainer.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.repositories.IvyArtifactRepository.layout(String, org.gradle.api.artifacts.repositories.RepositoryLayout.() -> Unit)

Lorem ipsum.

@param layoutName
@param action

# org.gradle.api.artifacts.ResolutionStrategy.eachDependency(org.gradle.api.artifacts.DependencyResolveDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ResolutionStrategy.componentSelection(org.gradle.api.artifacts.ComponentSelectionRules.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.ResolutionStrategy.dependencySubstitution(org.gradle.api.artifacts.DependencySubstitutions.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler.module(Any, org.gradle.api.artifacts.ComponentModuleMetadata.() -> Unit)

Lorem ipsum.

@param moduleNotation
@param action

# org.gradle.api.artifacts.dsl.ComponentMetadataHandler.all(org.gradle.api.artifacts.ComponentMetadataDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.dsl.ComponentMetadataHandler.withModule(Any, org.gradle.api.artifacts.ComponentMetadataDetails.() -> Unit)

Lorem ipsum.

@param id
@param action

# org.gradle.api.artifacts.dsl.RepositoryHandler.flatDir(org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.dsl.RepositoryHandler.jcenter(org.gradle.api.artifacts.repositories.MavenArtifactRepository.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.dsl.RepositoryHandler.maven(org.gradle.api.artifacts.repositories.MavenArtifactRepository.() -> Unit)

Adds and configures a Maven repository.

@param configuration The Maven repository configuration

# org.gradle.api.artifacts.dsl.RepositoryHandler.ivy(org.gradle.api.artifacts.repositories.IvyArtifactRepository.() -> Unit)

Adds and configures an Ivy repository.

@param configuration The Ivy repository configuration

# org.gradle.api.artifacts.dsl.DependencyHandler.components(org.gradle.api.artifacts.dsl.ComponentMetadataHandler.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.dsl.DependencyHandler.modules(org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.initialization.Settings.includeBuild(Any, org.gradle.api.initialization.IncludedBuild.() -> Unit)

Includes a build at the specified path to the composite build.

@param rootProject The path to the root project directory for the build.
@param configuration An action to configure the included build.

# org.gradle.api.initialization.IncludedBuild.dependencySubstitution(org.gradle.api.artifacts.DependencySubstitutions.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.file.CopySourceSpec.from(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.file.CopySpec.filesMatching(String, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param pattern
@param action

# org.gradle.api.file.CopySpec.filesMatching(Iterable<String>, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param patterns
@param action

# org.gradle.api.file.CopySpec.filesNotMatching(String, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param pattern
@param action

# org.gradle.api.file.CopySpec.filesNotMatching(Iterable<String>, org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param patterns
@param action

# org.gradle.api.file.CopySpec.from(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.file.CopySpec.into(Any, org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param target
@param action

# org.gradle.api.file.CopySpec.eachFile(org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.file.CopyProcessingSpec.eachFile(org.gradle.api.file.FileCopyDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.buildcomparison.gradle.CompareGradleBuilds.sourceBuild(org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.buildcomparison.gradle.CompareGradleBuilds.targetBuild(org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.quality.Checkstyle.reports(org.gradle.api.plugins.quality.CheckstyleReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.quality.Pmd.reports(org.gradle.api.plugins.quality.PmdReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.quality.FindBugs.reports(org.gradle.api.plugins.quality.FindBugsReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.quality.JDepend.reports(org.gradle.api.plugins.quality.JDependReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.plugins.quality.CodeNarc.reports(org.gradle.api.plugins.quality.CodeNarcReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.reporting.dependencies.HtmlDependencyReportTask.reports(org.gradle.api.reporting.dependencies.DependencyReportContainer.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.ivy.IvyModuleDescriptorSpec.withXml(org.gradle.api.XmlProvider.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.ivy.IvyArtifactSet.artifact(Any, org.gradle.api.publish.ivy.IvyArtifact.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.publish.ivy.IvyPublication.descriptor(org.gradle.api.publish.ivy.IvyModuleDescriptorSpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.ivy.IvyPublication.configurations(org.gradle.api.publish.ivy.IvyConfigurationContainer.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.ivy.IvyPublication.artifact(Any, org.gradle.api.publish.ivy.IvyArtifact.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.artifacts.maven.MavenPom.whenConfigured(org.gradle.api.artifacts.maven.MavenPom.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.maven.MavenPom.withXml(org.gradle.api.XmlProvider.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.artifacts.maven.MavenResolver.beforeDeployment(org.gradle.api.artifacts.maven.MavenDeployment.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.maven.MavenPom.withXml(org.gradle.api.XmlProvider.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.maven.MavenArtifactSet.artifact(Any, org.gradle.api.publish.maven.MavenArtifact.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.publish.maven.MavenPublication.pom(org.gradle.api.publish.maven.MavenPom.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.maven.MavenPublication.artifact(Any, org.gradle.api.publish.maven.MavenArtifact.() -> Unit)

Lorem ipsum.

@param source
@param action

# org.gradle.api.java.archives.ManifestMergeSpec.eachEntry(org.gradle.api.java.archives.ManifestMergeDetails.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.distribution.Distribution.contents(org.gradle.api.file.CopySpec.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.PublishingExtension.repositories(org.gradle.api.artifacts.dsl.RepositoryHandler.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.publish.PublishingExtension.publications(org.gradle.api.publish.PublicationContainer.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.reporting.GenerateBuildDashboard.reports(org.gradle.api.reporting.BuildDashboardReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.debug(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.info(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.lifecycle(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.warn(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.quiet(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.logging.TestLoggingContainer.error(org.gradle.api.tasks.testing.logging.TestLogging.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.Test.reports(org.gradle.api.tasks.testing.TestTaskReports.() -> Unit)

Lorem ipsum.

@param action

# org.gradle.api.tasks.testing.Test.filter(org.gradle.api.tasks.testing.TestFilter.() -> Unit)

Lorem ipsum.

@param action
