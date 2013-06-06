/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.api.AntBuilder
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.*
import org.gradle.api.internal.artifacts.ArtifactPublicationServices
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.file.*
import org.gradle.api.internal.initialization.DefaultScriptHandler
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider
import org.gradle.api.internal.plugins.DefaultPluginContainer
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.logging.LoggingManager
import org.gradle.api.plugins.PluginContainer
import org.gradle.configuration.project.DefaultProjectConfigurationActionContainer
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.Factory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.invocation.BuildClassLoaderRegistry
import org.gradle.logging.LoggingManagerInternal
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry

import spock.lang.Specification

class ProjectInternalServiceRegistryTest extends Specification {
    ProjectInternal project = Mock()
    ConfigurationContainerInternal configurationContainer = Mock()
    GradleInternal gradle = Mock()
    DependencyManagementServices dependencyManagementServices = Mock()
    ITaskFactory taskFactory = Mock()
    DependencyFactory dependencyFactory = Mock()
    ServiceRegistry parent = Stub()
    ProjectInternalServiceRegistry registry = new ProjectInternalServiceRegistry(parent, project)
    PluginRegistry pluginRegistry = Mock()
    DependencyResolutionServices dependencyResolutionServices = Stub()
    RepositoryHandler repositoryHandler = Mock()
    ArtifactPublicationServices publicationServices = Mock()
    DependencyHandler dependencyHandler = Mock()
    ArtifactHandler artifactHandler = Mock()

    def setup() {
        project.gradle >> gradle
        project.projectDir >> new File("project-dir").absoluteFile
        project.buildScriptSource >> Stub(ScriptSource)
        parent.get(ITaskFactory) >> taskFactory
        parent.get(DependencyFactory) >> dependencyFactory
        parent.get(PluginRegistry) >> pluginRegistry
        parent.get(DependencyManagementServices) >> dependencyManagementServices
        parent.get(Instantiator) >> new DirectInstantiator()
        parent.get(FileSystem) >> Stub(FileSystem)
        parent.get(ClassGenerator) >> Stub(ClassGenerator)
        parent.get(ProjectAccessListener) >> Stub(ProjectAccessListener)
        parent.get(BuildClassLoaderRegistry) >> Stub(BuildClassLoaderRegistry) {
            getScriptClassLoader() >> new ClassLoader() { }
        }
    }

    def "creates a registry for a task"() {
        expect:
        registry.createFor(Stub(TaskInternal)) instanceof TaskInternalServiceRegistry
    }

    def "provides a TaskContainerFactory"() {
        1 * taskFactory.createChild({ it.is project }, { it instanceof ClassGeneratorBackedInstantiator }) >> Stub(ITaskFactory)

        expect:
        registry.getFactory(TaskContainerInternal) instanceof DefaultTaskContainerFactory
    }

    def "provides a PluginContainer"() {
        expectScriptClassLoaderProviderCreated()

        1 * pluginRegistry.createChild(!null, _ as DependencyInjectingInstantiator) >> Stub(PluginRegistry)

        expect:
        provides(PluginContainer, DefaultPluginContainer)
    }

    def "provides a ToolingModelBuilderRegistry"() {
        expect:
        provides(ToolingModelBuilderRegistry, DefaultToolingModelBuilderRegistry)
    }

    def "provides an ArtifactPublicationServices factory"() {
        expectDependencyResolutionServicesCreated()

        expect:
        registry.get(ArtifactPublicationServices).is publicationServices
    }

    def "provides a RepositoryHandler"() {
        expectDependencyResolutionServicesCreated()

        expect:
        registry.get(RepositoryHandler).is repositoryHandler
        registry.get(RepositoryHandler).is registry.get(RepositoryHandler)
    }

    def "provides a ConfigurationContainer"() {
        expectDependencyResolutionServicesCreated()

        expect:
        registry.get(ConfigurationContainerInternal).is configurationContainer
        registry.get(ConfigurationContainerInternal).is registry.get(ConfigurationContainerInternal)
    }

    def "provides an ArtifactHandler"() {
        expectDependencyResolutionServicesCreated()

        registry.get(ArtifactHandler).is artifactHandler
        registry.get(ArtifactHandler).is registry.get(ArtifactHandler)
    }

    def "provides a DependencyHandler"() {
        expectDependencyResolutionServicesCreated()

        expect:
        registry.get(DependencyHandler).is dependencyHandler
        registry.get(DependencyHandler).is registry.get(DependencyHandler)
    }

    def "provides an AntBuilder factory"() {
        expect:
        registry.getFactory(AntBuilder) instanceof DefaultAntBuilderFactory
        registry.getFactory(AntBuilder).is registry.getFactory(AntBuilder)
    }

    def "provides a ScriptHandler and ScriptClassLoaderProvider"() {
        expectScriptClassLoaderProviderCreated()

        expect:
        provides(ScriptHandler, DefaultScriptHandler)
        registry.get(ScriptClassLoaderProvider).is registry.get(ScriptHandler)
    }

    def "provides a FileResolver"() {
        expect:
        provides(FileResolver, BaseDirFileResolver)
    }

    def "provides a FileOperations instance"() {
        1 * project.tasks

        expect:
        provides(FileOperations, DefaultFileOperations)
    }
    
    def "provides a TemporaryFileProvider"() {
        expect:
        provides(TemporaryFileProvider, DefaultTemporaryFileProvider)
    }

    def "provides a ProjectConfigurationActionContainer"() {
        expect:
        provides(ProjectConfigurationActionContainer, DefaultProjectConfigurationActionContainer)
    }

    def "provides a LoggingManager"() {
        Factory<LoggingManagerInternal> loggingManagerFactory = Mock()
        LoggingManager loggingManager = Mock(LoggingManagerInternal)

        parent.getFactory(LoggingManagerInternal) >> loggingManagerFactory
        1 * loggingManagerFactory.create() >> loggingManager

        expect:
        registry.get(LoggingManager).is loggingManager
        registry.get(LoggingManager).is registry.get(LoggingManager)
    }

    void provides(Class<?> contractType, Class<?> implementationType) {
        assert implementationType.isInstance(registry.get(contractType))
        assert registry.get(contractType).is(registry.get(contractType))
    }

    private void expectScriptClassLoaderProviderCreated() {
        1 * dependencyManagementServices.create(!null, !null, !null, !null) >> dependencyResolutionServices
        // return mock rather than stub; workaround for fact that Spock doesn't substitute generic method return type as it should
        dependencyResolutionServices.configurationContainer >> Mock(ConfigurationContainerInternal)
    }

    private void expectDependencyResolutionServicesCreated() {
        1 * dependencyManagementServices.create(!null, !null, !null, !null) >> dependencyResolutionServices
        dependencyResolutionServices.resolveRepositoryHandler >> repositoryHandler
        dependencyResolutionServices.createArtifactPublicationServices() >> publicationServices
        dependencyResolutionServices.configurationContainer >> configurationContainer
        dependencyResolutionServices.dependencyHandler >> dependencyHandler
        dependencyResolutionServices.artifactHandler >> artifactHandler
    }
}
