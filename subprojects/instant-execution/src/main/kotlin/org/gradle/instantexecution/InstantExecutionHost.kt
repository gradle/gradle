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

import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.project.DefaultProjectRegistry
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.execution.plan.Node
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.BuildLoader
import org.gradle.initialization.BuildOperatingFiringSettingsPreparer
import org.gradle.initialization.BuildOperatingFiringTaskExecutionPreparer
import org.gradle.initialization.BuildOperationSettingsProcessor
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.NotifyingBuildLoader
import org.gradle.initialization.PropertiesLoadingSettingsProcessor
import org.gradle.initialization.SettingsLocation
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.SettingsProcessor
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.internal.build.BuildState
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

    override val skipLoadingStateReason: String?
        get() = if (startParameter.isRefreshDependencies) {
            "--refresh-dependencies"
        } else {
            null
        }

    override val currentBuild: ClassicModeBuild =
        DefaultClassicModeBuild()

    override fun createBuild(rootProjectName: String): InstantExecutionBuild =
        DefaultInstantExecutionBuild(gradle, service(), rootProjectName)

    override fun <T> getService(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

    override fun getSystemProperty(propertyName: String) =
        startParameter.systemPropertiesArgs[propertyName] ?: System.getProperty(propertyName)

    override val requestedTaskNames: List<String> = startParameter.taskNames

    override val rootDir: File = startParameter.currentDir

    inner class DefaultClassicModeBuild : ClassicModeBuild {
        override val buildSrc: Boolean
            get() = gradle.parent != null && gradle.publicBuildPath.buildPath.name == SettingsInternal.BUILD_SRC

        override val gradle: GradleInternal
            get() = this@InstantExecutionHost.gradle

        override val scheduledWork: List<Node>
            get() = gradle.taskGraph.scheduledWork

        override val rootProject: ProjectInternal
            get() = gradle.rootProject
    }

    inner class DefaultInstantExecutionBuild(
        override val gradle: GradleInternal,
        private val fileResolver: PathToFileResolver,
        private val rootProjectName: String
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

                setBaseProjectClassLoaderScope(coreScope)
                projectDescriptorRegistry.rootProject!!.name = rootProjectName
            }
        }

        override fun createProject(path: String) {
            val projectPath = Path.path(path)
            val name = projectPath.name
            val projectDescriptor = DefaultProjectDescriptor(
                getProjectDescriptor(projectPath.parent),
                name ?: rootProjectName,
                rootDir,
                projectDescriptorRegistry,
                fileResolver
            )
            projectDescriptorRegistry.addProject(projectDescriptor)
        }

        override fun registerProjects() {
            // Ensure projects are registered for look up e.g. by dependency resolution
            service<ProjectStateRegistry>().registerProjects(service<BuildState>())
            for (project in projectDescriptorRegistry.allProjects) {
                projectFactory.createProject(gradle, project, getProject(project.path().parent), coreAndPluginsScope, coreAndPluginsScope)
            }
            gradle.rootProject = getProject(Path.ROOT)!!
            gradle.defaultProject = gradle.rootProject

            // Fire build operation required by build scans to determine build path (and settings execution time)
            // It may be better to instead point GE at the origin build that produced the cached task graph,
            // or replace this with a different event/op that carries this information and wraps some actual work
            val buildOperationExecutor = service<BuildOperationExecutor>()
            val settingsProcessor = BuildOperationSettingsProcessor(
                PropertiesLoadingSettingsProcessor(
                    SettingsProcessor { gradle, _, _, _ -> gradle.settings },
                    service()
                ),
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

        override fun scheduleNodes(nodes: Collection<Node>) {
            gradle.taskGraph.run {
                addNodes(nodes)
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
        parentPath?.let { service<DefaultProjectRegistry<ProjectInternal>>().getProject(it.path) }

    private
    fun getProjectDescriptor(parentPath: Path?): DefaultProjectDescriptor? =
        parentPath?.let { projectDescriptorRegistry.getProject(it.path) }

    private
    val projectDescriptorRegistry
        get() = (gradle.settings as DefaultSettings).projectDescriptorRegistry
}
