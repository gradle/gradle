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

import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
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
import org.gradle.initialization.SettingsLocation
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.SettingsProcessor
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
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
    private val startParameter: InstantExecutionStartParameter,
    private val gradle: GradleInternal,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
    private val projectFactory: IProjectFactory
) : DefaultInstantExecution.Host {

    override val currentBuild: ClassicModeBuild =
        DefaultClassicModeBuild()

    override fun createBuild(rootProjectName: String): InstantExecutionBuild =
        DefaultInstantExecutionBuild(gradle, service(), rootProjectName)

    override fun <T> getService(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

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
                // Fire build operation required by build scan to determine startup duration and settings evaluated duration
                val settingsPreparer = BuildOperatingFiringSettingsPreparer(
                    SettingsPreparer {
                        settings = processSettings()
                    },
                    service<BuildOperationExecutor>(),
                    service<BuildDefinition>().fromBuild
                )
                settingsPreparer.prepareSettings(this)

                setBaseProjectClassLoaderScope(coreScope)
                projectDescriptorRegistry.rootProject!!.name = rootProjectName
            }
        }

        override fun createProject(path: String, dir: File) {
            val projectPath = Path.path(path)
            val name = projectPath.name
            val projectDescriptor = DefaultProjectDescriptor(
                getProjectDescriptor(projectPath.parent),
                name ?: rootProjectName,
                dir,
                projectDescriptorRegistry,
                fileResolver
            )
            projectDescriptorRegistry.addProject(projectDescriptor)
        }

        override fun registerProjects() {
            // Ensure projects are registered for look up e.g. by dependency resolution
            service<ProjectStateRegistry>().registerProjects(service<BuildState>())
            val rootProject = createProject(projectDescriptorRegistry.rootProject!!, null)
            gradle.rootProject = rootProject
            gradle.defaultProject = gradle.rootProject

            // Fire build operation required by build scans to determine the build's project structure (and build load time)
            val buildOperationExecutor = service<BuildOperationExecutor>()
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

        private
        fun createProject(descriptor: ProjectDescriptor, parent: ProjectInternal?): ProjectInternal {
            val project = projectFactory.createProject(gradle, descriptor, parent, coreAndPluginsScope, coreAndPluginsScope)
            for (child in descriptor.children) {
                createProject(child, project)
            }
            return project
        }

        override fun getProject(path: String): ProjectInternal =
            gradle.rootProject.project(path)

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
        fun processSettings(): SettingsInternal {
            // Fire build operation required by build scans to determine build path (and settings execution time)
            // It may be better to instead point GE at the origin build that produced the cached task graph,
            // or replace this with a different event/op that carries this information and wraps some actual work
            return BuildOperationSettingsProcessor(
                SettingsProcessor { _, _, _, _ ->
                    createSettings().also {
                        applyAutoPluginRequestsTo(it)
                    }
                },
                service()
            ).process(
                gradle,
                SettingsLocation(rootDir, File(rootDir, "settings.gradle")),
                gradle.classLoaderScope,
                gradle.startParameter
            )
        }

        private
        fun createSettings(): SettingsInternal {
            val baseClassLoaderScope = gradle.classLoaderScope
            val classLoaderScope = baseClassLoaderScope.createChild("settings")
            return StringScriptSource("settings", "").let { settingsSource ->
                service<Instantiator>().newInstance(
                    DefaultSettings::class.java,
                    service<BuildScopeServiceRegistryFactory>(),
                    gradle,
                    classLoaderScope,
                    baseClassLoaderScope,
                    service<ScriptHandlerFactory>().create(settingsSource, classLoaderScope),
                    rootDir,
                    settingsSource,
                    gradle.startParameter
                )
            }
        }

        private
        fun applyAutoPluginRequestsTo(settingsInternal: SettingsInternal) {
            service<PluginRequestApplicator>().applyPlugins(
                autoAppliedPluginRequestsFor(settingsInternal),
                settingsInternal.buildscript as ScriptHandlerInternal?,
                settingsInternal.pluginManager,
                settingsInternal.classLoaderScope
            )
        }

        private
        fun autoAppliedPluginRequestsFor(settingsInternal: SettingsInternal) =
            service<AutoAppliedPluginRegistry>().getAutoAppliedPlugins(settingsInternal)
    }

    private
    val rootDir
        get() = startParameter.rootDirectory

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
