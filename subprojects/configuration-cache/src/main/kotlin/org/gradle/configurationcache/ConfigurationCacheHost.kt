/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.execution.plan.Node
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.BuildLoader
import org.gradle.initialization.BuildOperationFiringSettingsPreparer
import org.gradle.initialization.BuildOperationFiringTaskExecutionPreparer
import org.gradle.initialization.BuildOperationSettingsProcessor
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.NotifyingBuildLoader
import org.gradle.initialization.SettingsLocation
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildState
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.service.scopes.BuildScopeServiceRegistryFactory
import org.gradle.util.Path
import java.io.File


class ConfigurationCacheHost internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val gradle: GradleInternal,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
    private val projectFactory: IProjectFactory
) : DefaultConfigurationCache.Host {

    override val currentBuild: VintageGradleBuild =
        DefaultVintageGradleBuild()

    override fun createBuild(rootProjectName: String): ConfigurationCacheBuild =
        DefaultConfigurationCacheBuild(gradle, service(), rootProjectName)

    override fun <T> service(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

    override fun <T> factory(serviceType: Class<T>): Factory<T> =
        gradle.services.getFactory(serviceType)

    inner class DefaultVintageGradleBuild : VintageGradleBuild {

        override val gradle: GradleInternal
            get() = this@ConfigurationCacheHost.gradle

        override val scheduledWork: List<Node>
            get() = gradle.taskGraph.scheduledWork

        override val rootProject: ProjectInternal
            get() = gradle.rootProject
    }

    inner class DefaultConfigurationCacheBuild(
        override val gradle: GradleInternal,
        private val fileResolver: PathToFileResolver,
        private val rootProjectName: String
    ) : ConfigurationCacheBuild {
        private
        val buildDirs = mutableMapOf<Path, File>()

        init {
            gradle.run {
                // Fire build operation required by build scan to determine startup duration and settings evaluated duration
                val settingsPreparer = BuildOperationFiringSettingsPreparer(
                    SettingsPreparer {
                        settings = processSettings()
                    },
                    service<BuildOperationExecutor>(),
                    service<BuildDefinition>().fromBuild
                )
                settingsPreparer.prepareSettings(this)

                includedBuilds = emptyList()

                setBaseProjectClassLoaderScope(coreScope)
                projectDescriptorRegistry.rootProject!!.name = rootProjectName
            }
        }

        override fun createProject(path: String, dir: File, buildDir: File) {
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
            buildDirs[projectPath] = buildDir
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
                override fun run(context: BuildOperationContext) = Unit

                override fun description(): BuildOperationDescriptor.Builder {
                    val project = gradle.rootProject
                    val displayName = "Configure project " + project.identityPath
                    return BuildOperationDescriptor.displayName(displayName)
                        .metadata(BuildOperationCategory.CONFIGURE_PROJECT)
                        .progressDisplayName(displayName)
                        .details(ConfigureProjectBuildOperationType.DetailsImpl(project.projectPath, gradle.identityPath, project.rootDir))
                }
            })
        }

        private
        fun createProject(descriptor: ProjectDescriptor, parent: ProjectInternal?): ProjectInternal {
            val project = projectFactory.createProject(gradle, descriptor, parent, coreAndPluginsScope, coreAndPluginsScope)
            // Build dir is restored in order to use the correct workspace directory for transforms of project dependencies when the build dir has been customized
            buildDirs[project.identityPath]?.let {
                project.buildDir = it
            }
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
            // Currently this operation is not around the actual task graph calculation/populate for configuration cache (just to make this a smaller step)
            // This might be better done as a new build operation type
            BuildOperationFiringTaskExecutionPreparer(
                TaskExecutionPreparer {
                    // Nothing to do
                    // TODO:configuration-cache - perhaps move this so it wraps loading tasks from cache file
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
                { _, _, _, _ -> createSettings() },
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
            return TextResourceScriptSource(StringTextResource("settings", "")).let { settingsSource ->
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
    fun getProjectDescriptor(parentPath: Path?): DefaultProjectDescriptor? =
        parentPath?.let { projectDescriptorRegistry.getProject(it.path) }

    private
    val projectDescriptorRegistry
        get() = (gradle.settings as DefaultSettings).projectDescriptorRegistry
}
