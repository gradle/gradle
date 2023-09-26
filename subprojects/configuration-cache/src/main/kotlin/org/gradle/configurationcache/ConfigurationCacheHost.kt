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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.execution.plan.Node
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.SettingsState
import org.gradle.initialization.layout.BuildLocations
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.internal.service.scopes.SettingsScopeServices
import org.gradle.util.Path
import java.io.File


class ConfigurationCacheHost internal constructor(
    private val gradle: GradleInternal,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
) : DefaultConfigurationCache.Host {

    override val currentBuild: VintageGradleBuild =
        DefaultVintageGradleBuild(gradle.owner)

    override fun visitBuilds(visitor: (VintageGradleBuild) -> Unit) {
        service<BuildStateRegistry>().visitBuilds { build ->
            visitor(DefaultVintageGradleBuild(build))
        }
    }

    override fun createBuild(settingsFile: File?): ConfigurationCacheBuild =
        DefaultConfigurationCacheBuild(gradle.owner, service(), service(), settingsFile)

    override fun <T> service(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

    override fun <T> factory(serviceType: Class<T>): Factory<T> =
        gradle.services.getFactory(serviceType)

    private
    class DefaultVintageGradleBuild(override val state: BuildState) : VintageGradleBuild {
        override val isRootBuild: Boolean
            get() = state is RootBuildState

        override val gradle: GradleInternal
            get() = state.mutableModel

        override val hasScheduledWork: Boolean
            get() = gradle.taskGraph.size() > 0

        override val scheduledWork: List<Node>
            get() {
                lateinit var nodes: List<Node>
                gradle.taskGraph.visitScheduledNodes { nodes = it }
                return nodes
            }
    }

    private
    inner class DefaultConfigurationCacheBuild(
        override val state: BuildState,
        private val fileResolver: PathToFileResolver,
        private val buildStateRegistry: BuildStateRegistry,
        private val settingsFile: File?
    ) : ConfigurationCacheBuild {

        private
        val buildDirs = mutableMapOf<Path, File>()

        init {
            gradle.run {
                attachSettings(createSettings())
                setBaseProjectClassLoaderScope(coreScope)
            }
        }

        override val gradle: GradleInternal
            get() = state.mutableModel

        override fun registerRootProject(rootProjectName: String, projectDir: File, buildDir: File) {
            // Root project is registered when the settings are created, just need to adjust its properties
            val descriptor = rootProjectDescriptor()
            descriptor.name = rootProjectName
            descriptor.projectDir = projectDir
            buildDirs[Path.ROOT] = buildDir
        }

        override fun registerProject(projectPath: Path, dir: File, buildDir: File) {
            val name = projectPath.name
            require(name != null)
            // Adds the descriptor to the registry as a side effect
            DefaultProjectDescriptor(
                getProjectDescriptor(projectPath.parent),
                name,
                dir,
                projectDescriptorRegistry,
                fileResolver
            )
            buildDirs[projectPath] = buildDir
        }

        override fun createProjects() {
            // Ensure projects are registered for look up e.g. by dependency resolution
            val projectRegistry = service<ProjectStateRegistry>()
            projectRegistry.registerProjects(state, projectDescriptorRegistry)
            createRootProject()
        }

        private
        fun createRootProject() {
            val rootProject = createProject(rootProjectDescriptor())
            gradle.rootProject = rootProject
            gradle.defaultProject = rootProject
        }

        private
        fun rootProjectDescriptor() = projectDescriptorRegistry.rootProject!!

        private
        fun createProject(descriptor: DefaultProjectDescriptor): ProjectInternal {
            val projectState = state.projects.getProject(descriptor.path())
            projectState.createMutableModel(coreAndPluginsScope, coreAndPluginsScope)
            val project = projectState.mutableModel
            // Build dir is restored in order to use the correct workspace directory for transforms of project dependencies when the build dir has been customized
            buildDirs[project.projectPath]?.let {
                project.layout.buildDirectory.set(it)
            }
            for (child in descriptor.children()) {
                createProject(child)
            }
            return project
        }

        override fun getProject(path: String): ProjectInternal =
            state.projects.getProject(Path.path(path)).mutableModel

        override fun addIncludedBuild(buildDefinition: BuildDefinition, settingsFile: File?, buildPath: Path): ConfigurationCacheBuild {
            return DefaultConfigurationCacheBuild(buildStateRegistry.addIncludedBuild(buildDefinition, buildPath), fileResolver, buildStateRegistry, settingsFile)
        }

        override fun getBuildSrcOf(ownerId: BuildIdentifier): ConfigurationCacheBuild {
            return DefaultConfigurationCacheBuild(buildStateRegistry.getBuildSrcNestedBuild(buildStateRegistry.getBuild(ownerId))!!, fileResolver, buildStateRegistry, null)
        }

        private
        fun createSettings(): SettingsState {
            val baseClassLoaderScope = gradle.classLoaderScope
            val classLoaderScope = baseClassLoaderScope.createChild("settings", null)
            val settingsSource = TextResourceScriptSource(service<TextFileResourceLoader>().loadFile("settings file", settingsFile))
            lateinit var services: SettingsScopeServices
            val serviceRegistryFactory = object : ServiceRegistryFactory {
                override fun createFor(domainObject: Any): ServiceRegistry {
                    services = SettingsScopeServices(service<ServiceRegistry>(), domainObject as SettingsInternal)
                    return services
                }
            }
            val settings = service<Instantiator>().newInstance(
                DefaultSettings::class.java,
                serviceRegistryFactory,
                gradle,
                classLoaderScope,
                baseClassLoaderScope,
                service<ScriptHandlerFactory>().create(settingsSource, classLoaderScope),
                settingsDir(),
                settingsSource,
                gradle.startParameter
            )
            return SettingsState(settings, services)
        }

        private
        fun settingsDir() =
            service<BuildLocations>().settingsDir

        private
        fun getProjectDescriptor(parentPath: Path?): DefaultProjectDescriptor? =
            parentPath?.let { projectDescriptorRegistry.getProject(it.path) }

        private
        val projectDescriptorRegistry
            get() = (gradle.settings as DefaultSettings).projectDescriptorRegistry
    }

    private
    val coreScope: ClassLoaderScope
        get() = classLoaderScopeRegistry.coreScope

    private
    val coreAndPluginsScope: ClassLoaderScope
        get() = classLoaderScopeRegistry.coreAndPluginsScope
}
