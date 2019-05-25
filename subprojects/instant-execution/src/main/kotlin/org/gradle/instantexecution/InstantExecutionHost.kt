/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution

import org.gradle.api.Task
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.BuildLoader
import org.gradle.initialization.BuildOperatingFiringSettingsPreparer
import org.gradle.initialization.BuildOperatingFiringTaskExecutionPreparer
import org.gradle.initialization.BuildOperationSettingsProcessor
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.NotifyingBuildLoader
import org.gradle.initialization.SettingsLocation
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.SettingsProcessor
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.internal.build.BuildState
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.scopes.BuildScopeServiceRegistryFactory
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.util.Path
import java.io.File


class InstantExecutionHost internal constructor(
    private val gradle: GradleInternal,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
    private val projectFactory: IProjectFactory
) : DefaultInstantExecution.Host {

    private
    val startParameter = gradle.startParameter

    override val isSkipLoadingState: Boolean
        get() = gradle.startParameter.isRefreshDependencies

    override val currentBuild: ClassicModeBuild =
        DefaultClassicModeBuild()

    override fun createBuild(rootProjectName: String): InstantExecutionBuild =
        DefaultInstantExecutionBuild(service(), rootProjectName)

    override fun <T> getService(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

    override fun getSystemProperty(propertyName: String) =
        startParameter.systemPropertiesArgs[propertyName]

    override val requestedTaskNames: List<String> = startParameter.taskNames

    override val rootDir: File = startParameter.currentDir

    override fun classLoaderFor(classPath: ClassPath): ClassLoader =
        service<ClassLoaderCache>().get(
            ClassLoaderIds.buildScript("instant-execution", "run"),
            classPath,
            coreAndPluginsScope.exportClassLoader,
            null
        )

    inner class DefaultClassicModeBuild : ClassicModeBuild {

        override val scheduledTasks: List<Task>
            get() = gradle.taskGraph.allTasks

        override val rootProject: ProjectInternal
            get() = gradle.rootProject

        override fun dependenciesOf(task: Task): Set<Task> =
            gradle.taskGraph.getDependencies(task)
    }

    inner class DefaultInstantExecutionBuild(
        private val fileResolver: PathToFileResolver,
        rootProjectName: String
    ) : InstantExecutionBuild {

        init {
            gradle.run {
                settings = createSettings()

                // Fire build operation required by build scan to determine startup duration and settings evaluated duration
                val settingsPreparer = BuildOperatingFiringSettingsPreparer(
                    SettingsPreparer {
                        // Nothing to do
                        // TODO:instant-execution - instead, create and attach the settings object
                    },
                    service<BuildOperationExecutor>(),
                    service<BuildDefinition>().fromBuild
                )
                settingsPreparer.prepareSettings(this)

                rootProject = createProject(null, rootProjectName)
            }
        }

        override fun createProject(path: String): ProjectInternal {
            val projectPath = Path.path(path)
            val name = projectPath.name
            return when {
                name != null -> createProject(projectPath.parent, name)
                else -> gradle.rootProject
            }
        }

        private
        fun InstantExecutionHost.createProject(parentPath: Path?, name: String): ProjectInternal {
            val projectDescriptor = DefaultProjectDescriptor(
                getProjectDescriptor(parentPath),
                name,
                rootDir,
                projectDescriptorRegistry,
                fileResolver
            )
            return projectFactory.createProject(
                projectDescriptor,
                getProject(parentPath),
                gradle,
                coreAndPluginsScope.createChild(projectDescriptor.path),
                coreAndPluginsScope
            )
        }

        override fun registerProjects() {
            // Ensure projects are registered for look up e.g. by dependency resolution
            service<ProjectStateRegistry>().registerProjects(service<BuildState>())

            // Fire build operation required by build scans to determine build path (and settings execution time)
            // It may be better to instead point GE at the origin build that produced the cached task graph,
            // or replace this with a different event/op that carries this information and wraps some actual work
            val buildOperationExecutor = service<BuildOperationExecutor>()
            val settingsProcessor = BuildOperationSettingsProcessor(
                SettingsProcessor { gradle, _, _, _ -> gradle.settings },
                buildOperationExecutor
            )
            val rootProject = gradle.rootProject
            val settingsLocation = SettingsLocation(rootProject.projectDir, File(rootProject.projectDir, "settings.gradle"))
            settingsProcessor.process(gradle, settingsLocation, coreAndPluginsScope, startParameter)

            // Fire build operation required by build scans to determine the build's project structure (and build load time)
            val buildLoader = NotifyingBuildLoader(BuildLoader { _, _ -> }, buildOperationExecutor)
            buildLoader.load(gradle.settings, gradle)

            // Fire build operation required by build scans to determine the root path
            buildOperationExecutor.run(object : RunnableBuildOperation {
                override fun run(context: BuildOperationContext?) = Unit

                override fun description(): BuildOperationDescriptor.Builder {
                    val project = gradle.rootProject
                    val displayName = "Configure project " + project.identityPath
                    return BuildOperationDescriptor.displayName(displayName)
                        .operationType(BuildOperationCategory.CONFIGURE_PROJECT)
                        .progressDisplayName(displayName)
                        .details(ConfigureProjectBuildOperationType.DetailsImpl(project.projectPath, gradle.identityPath, project.rootDir))
                }
            })
        }

        override fun getProject(path: String): ProjectInternal =
            gradle.rootProject.project(path)

        override fun autoApplyPlugins() {
            if (!startParameter.isBuildScan) {
                return
            }

            // System properties are currently set as during settings script execution, so work around for now
            // TODO - extract system properties setup into some that can be reused for instant execution
            val buildScanUrl = getSystemProperty("com.gradle.scan.server")
            if (buildScanUrl != null) {
                System.setProperty("com.gradle.scan.server", buildScanUrl)
            }

            val rootProject = gradle.rootProject
            val pluginRequests = service<AutoAppliedPluginRegistry>().getAutoAppliedPlugins(rootProject)
            service<PluginRequestApplicator>().applyPlugins(
                pluginRequests,
                rootProject.buildscript,
                rootProject.pluginManager,
                rootProject.classLoaderScope
            )
        }

        override fun scheduleTasks(tasks: Iterable<Task>) {
            gradle.taskGraph.run {
                addEntryTasks(tasks)
                populate()
            }

            // Fire build operation required by build scan to determine when task execution starts
            // Currently this operation is not around the actual task graph calculation/populate for instant execution (just to make this a smaller step)
            // This might be better done as a new build operation type
            BuildOperatingFiringTaskExecutionPreparer(
                TaskExecutionPreparer {
                    // Nothing to do
                    // TODO:instant-execution - perhaps move this so it wraps loading tasks from cache file
                },
                service<BuildOperationExecutor>()
            ).prepareForTaskExecution(gradle)
        }

        private
        fun createSettings(): SettingsInternal =
            StringScriptSource("settings", "").let { settingsSource ->
                service<Instantiator>().newInstance(
                    DefaultSettings::class.java,
                    service<BuildScopeServiceRegistryFactory>(),
                    gradle,
                    coreScope,
                    coreScope,
                    service<ScriptHandlerFactory>().create(settingsSource, coreScope),
                    rootDir,
                    settingsSource,
                    startParameter
                )
            }
    }

    private
    val coreScope: ClassLoaderScope
        get() = classLoaderScopeRegistry.coreScope

    private
    val coreAndPluginsScope: ClassLoaderScope
        get() = classLoaderScopeRegistry.coreAndPluginsScope

    private
    fun getProject(parentPath: Path?) =
        parentPath?.let { gradle.rootProject.project(it.path) }

    private
    fun getProjectDescriptor(parentPath: Path?): DefaultProjectDescriptor? =
        parentPath?.let { projectDescriptorRegistry.getProject(it.path) }

    private
    val projectDescriptorRegistry
        get() = (gradle.settings as DefaultSettings).projectDescriptorRegistry
}
