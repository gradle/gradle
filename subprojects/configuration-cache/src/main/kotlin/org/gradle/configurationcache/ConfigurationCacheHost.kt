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
import org.gradle.configurationcache.build.ConfigurationCacheIncludedBuildState
import org.gradle.execution.plan.Node
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.layout.BuildLayout
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.CompositeBuildParticipantBuildState
import org.gradle.internal.build.IncludedBuildFactory
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.resource.TextFileResourceLoader
import org.gradle.internal.service.scopes.BuildScopeServiceRegistryFactory
import org.gradle.util.Path
import java.io.File


class ConfigurationCacheHost internal constructor(
    private val gradle: GradleInternal,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
) : DefaultConfigurationCache.Host {

    override val currentBuild: VintageGradleBuild =
        DefaultVintageGradleBuild(gradle)

    override fun createBuild(settingsFile: File?, rootProjectName: String): ConfigurationCacheBuild =
        DefaultConfigurationCacheBuild(gradle, service(), settingsFile, rootProjectName)

    override fun <T> service(serviceType: Class<T>): T =
        gradle.services.get(serviceType)

    override fun <T> factory(serviceType: Class<T>): Factory<T> =
        gradle.services.getFactory(serviceType)

    private
    class DefaultVintageGradleBuild(override val gradle: GradleInternal) : VintageGradleBuild {
        override val scheduledWork: List<Node>
            get() = gradle.taskGraph.scheduledWorkPlusDependencies
    }

    private
    inner class DefaultConfigurationCacheBuild(
        override val gradle: GradleInternal,
        private val fileResolver: PathToFileResolver,
        private val settingsFile: File?,
        private val rootProjectName: String
    ) : ConfigurationCacheBuild, IncludedBuildFactory {

        private
        val buildDirs = mutableMapOf<Path, File>()

        init {
            require(gradle.owner is CompositeBuildParticipantBuildState)
            gradle.run {
                settings = createSettings()
                setBaseProjectClassLoaderScope(coreScope)
                rootProjectDescriptor().name = rootProjectName
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
                project.buildDir = it
            }
            for (child in descriptor.children()) {
                createProject(child)
            }
            return project
        }

        override fun getProject(path: String): ProjectInternal =
            state.projects.getProject(Path.path(path)).mutableModel

        override fun addIncludedBuild(buildDefinition: BuildDefinition): IncludedBuildState {
            return service<BuildStateRegistry>().addIncludedBuildOf(this, buildDefinition)
        }

        override fun createBuild(
            buildIdentifier: BuildIdentifier,
            identityPath: Path,
            buildDefinition: BuildDefinition,
            isImplicit: Boolean,
            owner: BuildState
        ): IncludedBuildState = ConfigurationCacheIncludedBuildState(
            buildIdentifier,
            identityPath,
            buildDefinition,
            isImplicit,
            owner,
            service(),
            service(),
            service(),
            service()
        )

        override fun prepareBuild(includedBuild: IncludedBuildState) {
        }

        private
        fun createSettings(): SettingsInternal {
            val baseClassLoaderScope = gradle.classLoaderScope
            val classLoaderScope = baseClassLoaderScope.createChild("settings")
            val settingsSource = if (settingsFile == null) {
                TextResourceScriptSource(StringTextResource("settings", ""))
            } else {
                TextResourceScriptSource(service<TextFileResourceLoader>().loadFile("settings file", settingsFile))
            }
            return service<Instantiator>().newInstance(
                DefaultSettings::class.java,
                service<BuildScopeServiceRegistryFactory>(),
                gradle,
                classLoaderScope,
                baseClassLoaderScope,
                service<ScriptHandlerFactory>().create(settingsSource, classLoaderScope),
                settingsDir(),
                settingsSource,
                gradle.startParameter
            )
        }

        private
        fun settingsDir() =
            service<BuildLayout>().settingsDir

        override
        val state: CompositeBuildParticipantBuildState = gradle.owner as CompositeBuildParticipantBuildState
    }

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
