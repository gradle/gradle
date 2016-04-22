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

import org.gradle.StartParameter
import org.gradle.api.internal.*
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.initialization.loadercache.ClassPathSnapshotter
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler
import org.gradle.api.internal.project.*
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheFactory
import org.gradle.configuration.*
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory
import org.gradle.groovy.scripts.ScriptCompilerFactory
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache
import org.gradle.initialization.*
import org.gradle.internal.Factory
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory
import org.gradle.internal.operations.logging.DefaultBuildOperationLoggerFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.plugin.use.internal.InjectedPluginClasspath
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.profile.ProfileEventAdapter
import spock.lang.Specification

import static org.hamcrest.Matchers.instanceOf
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

public class BuildScopeServicesTest extends Specification {
    StartParameter startParameter = new StartParameter()
    BuildSessionScopeServices sessionServices = Mock()
    Factory<CacheFactory> cacheFactoryFactory = Mock()
    ClosableCacheFactory cacheFactory = Mock()
    ClassLoaderRegistry classLoaderRegistry = Mock()

    BuildScopeServices registry

    def setup() {
        sessionServices.getFactory(CacheFactory) >> cacheFactoryFactory
        cacheFactoryFactory.create() >> cacheFactory
        sessionServices.get(ClassLoaderRegistry) >> classLoaderRegistry
        sessionServices.getFactory(LoggingManagerInternal) >> Stub(Factory)
        sessionServices.get(ModuleRegistry) >> new DefaultModuleRegistry(CurrentGradleInstallation.get())
        sessionServices.get(PluginModuleRegistry) >> Stub(PluginModuleRegistry)
        sessionServices.get(DependencyManagementServices) >> Stub(DependencyManagementServices)
        sessionServices.get(Instantiator) >> ThreadGlobalInstantiator.getOrCreate()
        sessionServices.get(FileResolver) >> Stub(FileResolver)
        sessionServices.get(DirectoryFileTreeFactory) >> Stub(DirectoryFileTreeFactory)
        sessionServices.get(ProgressLoggerFactory) >> Stub(ProgressLoggerFactory)
        sessionServices.get(DocumentationRegistry) >> new DocumentationRegistry()
        sessionServices.get(FileLookup) >> Stub(FileLookup)
        sessionServices.get(PluginRequestApplicator) >> Mock(PluginRequestApplicator)
        sessionServices.get(BuildCancellationToken) >> Mock(BuildCancellationToken)
        sessionServices.get(ModelRuleSourceDetector) >> Mock(ModelRuleSourceDetector)
        sessionServices.get(ClassLoaderCache) >> Mock(ClassLoaderCache)
        sessionServices.get(ImportsReader) >> Mock(ImportsReader)
        sessionServices.get(StartParameter) >> startParameter
        sessionServices.get(CachingFileSnapshotter) >> Mock(CachingFileSnapshotter)
        sessionServices.get(ClassPathSnapshotter) >> Mock(ClassPathSnapshotter)
        sessionServices.get(CrossBuildInMemoryCachingScriptClassCache) >> Mock(CrossBuildInMemoryCachingScriptClassCache)
        sessionServices.get(InjectedPluginClasspath) >> Mock(InjectedPluginClasspath)
        sessionServices.get(PluginRepositoryHandler) >> Mock(PluginRepositoryHandler)
        sessionServices.getAll(_) >> []

        registry = new BuildScopeServices(sessionServices, false)
    }

    def cleanup() {
        registry?.close()
    }

    def delegatesToParentForUnknownService() {
        setup:
        sessionServices.get(String) >> "value"

        expect:
        registry.get(String) == "value"
    }

    def addsAllPluginBuildScopeServices() {
        def plugin2 = Mock(PluginServiceRegistry)
        def plugin1 = Mock(PluginServiceRegistry)

        given:
        def sessionServices = Mock(BuildSessionScopeServices) {
            getAll(PluginServiceRegistry) >> [plugin1, plugin2]
        }

        when:
        new BuildScopeServices(sessionServices, false)

        then:
        1 * plugin1.registerBuildServices(_)
        1 * plugin2.registerBuildServices(_)
    }

    def throwsExceptionForUnknownDomainObject() {
        when:
        registry.get(ServiceRegistryFactory).createFor("string")
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot create services for unknown domain object of type String."
    }

    def canCreateServicesForAGradleInstance() {
        setup:
        GradleInternal gradle = Mock()
        def registry = registry.get(ServiceRegistryFactory).createFor(gradle)
        expect:
        registry instanceof GradleScopeServices
    }

    def "closing the registry closes gradle scoped services, closing project services"() {
        given:
        GradleInternal gradle = Mock()
        def gradleRegistry = registry.get(ServiceRegistryFactory).createFor(gradle)
        def project = Mock(ProjectInternal)
        def projectRegistry = gradleRegistry.get(ServiceRegistryFactory).createFor(project)

        expect:
        !gradleRegistry.closed
        !projectRegistry.closed

        when:
        registry.close()

        then:
        gradleRegistry.closed
        projectRegistry.closed
    }

    def canCreateServicesForASettingsInstance() {
        setup:
        SettingsInternal settings = Mock()
        def registry = registry.get(ServiceRegistryFactory).createFor(settings)
        expect:
        registry instanceof SettingsScopeServices
    }

    def providesAListenerManager() {
        setup:
        ListenerManager listenerManager = expectListenerManagerCreated()
        expect:
        assertThat(registry.get(ListenerManager), sameInstance(listenerManager))
    }

    def providesAScriptCompilerFactory() {
        setup:
        expectListenerManagerCreated()
        expectParentServiceLocated(CacheRepository)

        expect:
        registry.get(ScriptCompilerFactory) instanceof DefaultScriptCompilerFactory
        registry.get(ScriptCompilerFactory) == registry.get(ScriptCompilerFactory)
    }

    def providesAnInitScriptHandler() {
        setup:
        expectListenerManagerCreated()
        allowGetGradleInstallation()
        expectParentServiceLocated(CacheRepository)

        expect:
        registry.get(InitScriptHandler) instanceof InitScriptHandler
        registry.get(InitScriptHandler) == registry.get(InitScriptHandler)
    }

    def providesAScriptObjectConfigurerFactory() {
        setup:
        expectListenerManagerCreated()
        expectParentServiceLocated(CacheRepository)

        expect:
        assertThat(registry.get(ScriptPluginFactory), instanceOf(DefaultScriptPluginFactory))
        assertThat(registry.get(ScriptPluginFactory), sameInstance(registry.get(ScriptPluginFactory)))
    }

    def providesASettingsProcessor() {
        setup:
        expectListenerManagerCreated()
        expectParentServiceLocated(CacheRepository)

        expect:
        assertThat(registry.get(SettingsProcessor), instanceOf(NotifyingSettingsProcessor))
        assertThat(registry.get(SettingsProcessor), sameInstance(registry.get(SettingsProcessor)))
    }

    def providesAnExceptionAnalyser() {
        setup:
        expectListenerManagerCreated()
        expectParentServiceLocated(LoggingConfiguration)

        expect:
        assertThat(registry.get(ExceptionAnalyser), instanceOf(StackTraceSanitizingExceptionAnalyser))
        assertThat(registry.get(ExceptionAnalyser).analyser, instanceOf(MultipleBuildFailuresExceptionAnalyser))
        assertThat(registry.get(ExceptionAnalyser).analyser.delegate, instanceOf(DefaultExceptionAnalyser))
        assertThat(registry.get(ExceptionAnalyser), sameInstance(registry.get(ExceptionAnalyser)))
    }

    def providesAnIsolatedAntBuilder() {
        setup:
        def factory = expectParentServiceLocated(ClassLoaderFactory)
        _ * factory.createIsolatedClassLoader(_) >> new URLClassLoader([] as URL[], getClass().classLoader)

        expect:

        assertThat(registry.get(IsolatedAntBuilder), instanceOf(DefaultIsolatedAntBuilder))
        assertThat(registry.get(IsolatedAntBuilder), sameInstance(registry.get(IsolatedAntBuilder)))
    }

    def providesAProjectFactory() {
        setup:
        expectParentServiceLocated(Instantiator)
        expectParentServiceLocated(ClassGenerator)
        expect:
        assertThat(registry.get(IProjectFactory), instanceOf(ProjectFactory))
        assertThat(registry.get(IProjectFactory), sameInstance(registry.get(IProjectFactory)))
    }

    def providesABuildConfigurer() {
        expect:
        assertThat(registry.get(BuildConfigurer), instanceOf(DefaultBuildConfigurer))
        assertThat(registry.get(BuildConfigurer), sameInstance(registry.get(BuildConfigurer)))
    }

    def providesAPropertiesLoader() {
        expect:
        assertThat(registry.get(IGradlePropertiesLoader), instanceOf(DefaultGradlePropertiesLoader))
        assertThat(registry.get(IGradlePropertiesLoader), sameInstance(registry.get(IGradlePropertiesLoader)))
    }

    def providesABuildLoader() {
        setup:
        expectParentServiceLocated(Instantiator)
        expect:
        assertThat(registry.get(BuildLoader), instanceOf(ProjectPropertySettingBuildLoader))
        assertThat(registry.get(BuildLoader), sameInstance(registry.get(BuildLoader)))
    }

    def providesAProfileEventAdapter() {
        setup:
        expectParentServiceLocated(BuildRequestMetaData)
        expectListenerManagerCreated()

        expect:
        assertThat(registry.get(ProfileEventAdapter), instanceOf(ProfileEventAdapter))
        assertThat(registry.get(ProfileEventAdapter), sameInstance(registry.get(ProfileEventAdapter)))
    }

    def "provides a project registry"() {
        when:
        def projectRegistry = registry.get(ProjectRegistry)
        def secondRegistry = registry.get(ProjectRegistry)

        then:
        projectRegistry instanceof DefaultProjectRegistry
        projectRegistry sameInstance(secondRegistry)
    }

    def "provides an build operation logger factory"() {
        when:
        def operationLoggerFactory = registry.get(BuildOperationLoggerFactory)

        then:
        operationLoggerFactory instanceof DefaultBuildOperationLoggerFactory
    }

    def "closes session when single use"() {
        when:
        new BuildScopeServices(sessionServices, true).close()

        then:
        1 * sessionServices.close()
    }

    private <T> T expectParentServiceLocated(Class<T> type) {
        T t = Mock(type)
        sessionServices.get(type) >> t
        t
    }

    private ListenerManager expectListenerManagerCreated() {
        final ListenerManager listenerManager = new DefaultListenerManager()
        final ListenerManager listenerManagerParent = Mock()
        sessionServices.get(ListenerManager) >> listenerManagerParent
        1 * listenerManagerParent.createChild() >> listenerManager
        listenerManager
    }

    private void allowGetGradleInstallation() {
        sessionServices.get(GradleInstallation) >> Mock(GradleInstallation)
    }

    public interface ClosableCacheFactory extends CacheFactory {
        void close()
    }
}
