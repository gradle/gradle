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

package org.gradle.internal.service.scopes

import org.gradle.api.AntBuilder
import org.gradle.api.RecordingAntBuildListener
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.DependencyInjectingInstantiator
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.InstantiatorFactory
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.file.DefaultFileOperations
import org.gradle.api.internal.file.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.DefaultScriptHandler
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.DefaultAntBuilderFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.tasks.DefaultTaskContainerFactory
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.logging.LoggingManager
import org.gradle.configuration.project.DefaultProjectConfigurationActionContainer
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.Factory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistry
import org.gradle.model.internal.inspect.ModelRuleExtractor
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry
import org.junit.Rule
import spock.lang.Specification

class ProjectScopeServicesTest extends Specification {
    ProjectInternal project = Mock()
    ConfigurationContainer configurationContainer = Mock()
    GradleInternal gradle = Mock()
    DependencyManagementServices dependencyManagementServices = Mock()
    ITaskFactory taskFactory = Mock()
    DependencyFactory dependencyFactory = Mock()
    ServiceRegistry parent = Stub()
    ProjectScopeServices registry
    PluginRegistry pluginRegistry = Mock() {
        createChild(_) >> Mock(PluginRegistry)
    }
    ModelRegistry modelRegistry = Mock()
    ModelRuleSourceDetector modelRuleSourceDetector = Mock()
    def classLoaderScope = Mock(ClassLoaderScope)
    DependencyResolutionServices dependencyResolutionServices = Stub()
    Factory<LoggingManagerInternal> loggingManagerInternalFactory = Mock()
    InstantiatorFactory instantiatorFactory = Mock()

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        project.gradle >> gradle
        project.projectDir >> testDirectoryProvider.file("project-dir").createDir().absoluteFile
        project.buildScriptSource >> Stub(ScriptSource)
        project.getClassLoaderScope() >> classLoaderScope
        project.getClassLoaderScope().createChild(_) >> classLoaderScope
        project.getClassLoaderScope().lock() >> classLoaderScope
        parent.get(ITaskFactory) >> taskFactory
        parent.get(DependencyFactory) >> dependencyFactory
        parent.get(PluginRegistry) >> pluginRegistry
        parent.get(DependencyManagementServices) >> dependencyManagementServices
        parent.get(Instantiator) >> DirectInstantiator.INSTANCE
        parent.get(FileSystem) >> Stub(FileSystem)
        parent.get(ClassGenerator) >> Stub(ClassGenerator)
        parent.get(ProjectAccessListener) >> Stub(ProjectAccessListener)
        parent.get(FileLookup) >> Stub(FileLookup)
        parent.get(DirectoryFileTreeFactory) >> Stub(DirectoryFileTreeFactory)
        parent.get(ModelRuleSourceDetector) >> modelRuleSourceDetector
        parent.get(ModelRuleExtractor) >> Stub(ModelRuleExtractor)
        parent.get(DependencyInjectingInstantiator.ConstructorCache) >> Stub(DependencyInjectingInstantiator.ConstructorCache)
        parent.get(ToolingModelBuilderRegistry) >> Mock(ToolingModelBuilderRegistry)
        parent.get(InstantiatorFactory) >> instantiatorFactory
        parent.hasService(_) >> true
        registry = new ProjectScopeServices(parent, project, loggingManagerInternalFactory)
    }

    def "adds all project scoped plugin services"() {
        def plugin2 = Mock(PluginServiceRegistry)
        def plugin1 = Mock(PluginServiceRegistry)

        given:
        parent.getAll(PluginServiceRegistry) >> [plugin1, plugin2]

        when:
        new ProjectScopeServices(parent, project, loggingManagerInternalFactory)

        then:
        1 * plugin1.registerProjectServices(_)
        1 * plugin2.registerProjectServices(_)
    }

    def "provides a TaskContainerFactory"() {
        def instantiator = Stub(Instantiator)
        1 * instantiatorFactory.injectAndDecorate(registry) >> instantiator
        1 * taskFactory.createChild({ it.is project }, instantiator) >> Stub(ITaskFactory)

        expect:
        registry.getFactory(TaskContainerInternal) instanceof DefaultTaskContainerFactory
    }

    def "provides a ToolingModelBuilderRegistry"() {
        expect:
        provides(ToolingModelBuilderRegistry, DefaultToolingModelBuilderRegistry)
    }

    def "provides dependency management DSL services"() {
        def testDslService = Stub(Runnable)

        when:
        def registry = new ProjectScopeServices(parent, project, loggingManagerInternalFactory)
        def service = registry.get(Runnable)

        then:
        service.is(testDslService)

        and:
        1 * dependencyManagementServices.addDslServices(_) >> { ServiceRegistration registration ->
            registration.add(Runnable, testDslService)
        }
    }

    def "provides an AntBuilder factory"() {
        expect:
        registry.getFactory(AntBuilder) instanceof DefaultAntBuilderFactory
        registry.getFactory(AntBuilder).is registry.getFactory(AntBuilder)
    }

    def "provides a ScriptHandler"() {
        expectScriptClassLoaderProviderCreated()

        expect:
        provides(ScriptHandler, DefaultScriptHandler)
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
        LoggingManager loggingManager = Mock(LoggingManagerInternal)
        1 * loggingManagerInternalFactory.create() >> loggingManager

        expect:
        registry.get(LoggingManager).is loggingManager
        registry.get(LoggingManager).is registry.get(LoggingManager)
    }

    def "ant builder is closed when registry is closed"() {
        given:
        def antBuilder = registry.getFactory(AntBuilder).create()
        def listener = new RecordingAntBuildListener()
        antBuilder.project.addBuildListener(listener)

        expect:
        listener.buildFinished.empty

        when:
        registry.close()

        then:
        !listener.buildFinished.empty
    }

    void provides(Class<?> contractType, Class<?> implementationType) {
        assert implementationType.isInstance(registry.get(contractType))
        assert registry.get(contractType).is(registry.get(contractType))
    }

    private void expectScriptClassLoaderProviderCreated() {
        1 * dependencyManagementServices.create(!null, !null, !null, !null) >> dependencyResolutionServices
        // return mock rather than stub; workaround for fact that Spock doesn't substitute generic method return type as it should
        dependencyResolutionServices.configurationContainer >> Mock(ConfigurationContainer)
    }
}
