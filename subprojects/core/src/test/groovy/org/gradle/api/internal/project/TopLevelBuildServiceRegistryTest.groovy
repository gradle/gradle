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

import org.gradle.StartParameter
import org.gradle.api.internal.*
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.classpath.PluginModuleRegistry
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.configuration.BuildConfigurer
import org.gradle.configuration.DefaultBuildConfigurer
import org.gradle.configuration.DefaultScriptPluginFactory
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory
import org.gradle.groovy.scripts.ScriptCompilerFactory
import org.gradle.initialization.*
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.DefaultListenerManager
import org.gradle.listener.ListenerManager
import org.gradle.logging.LoggingManagerInternal
import org.gradle.messaging.remote.MessagingServer
import org.gradle.process.internal.DefaultWorkerProcessFactory
import org.gradle.process.internal.WorkerProcessBuilder
import org.gradle.profile.ProfileEventAdapter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ClassLoaderFactory
import org.gradle.util.MultiParentClassLoader
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Timeout

import static org.hamcrest.Matchers.instanceOf
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

public class TopLevelBuildServiceRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    StartParameter startParameter = new StartParameter()
    ServiceRegistry parent = Mock()
    Factory<CacheFactory> cacheFactoryFactory = Mock()
    ClosableCacheFactory cacheFactory = Mock()
    ClassLoaderRegistry classLoaderRegistry = Mock()

    TopLevelBuildServiceRegistry registry = new TopLevelBuildServiceRegistry(parent, startParameter)

    def setup() {
        startParameter.gradleUserHomeDir = tmpDir.testDirectory
        parent.getFactory(CacheFactory) >> cacheFactoryFactory
        cacheFactoryFactory.create() >> cacheFactory
        parent.get(ClassLoaderRegistry) >> classLoaderRegistry
        parent.getFactory(LoggingManagerInternal) >> Mock(Factory)
        parent.get(ModuleRegistry) >> new DefaultModuleRegistry()
        parent.get(PluginModuleRegistry) >> Mock(PluginModuleRegistry)
        parent.get(Instantiator) >> ThreadGlobalInstantiator.getOrCreate()
    }

    def delegatesToParentForUnknownService() {
        setup:
        parent.get(String) >> "value"

        expect:
        registry.get(String) == "value"
    }

    def throwsExceptionForUnknownDomainObject() {
        when:
        registry.createFor("string")
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot create services for unknown domain object of type String."
    }

    def canCreateServicesForAGradleInstance() {
        setup:
        GradleInternal gradle = Mock()
        ServiceRegistryFactory registry = this.registry.createFor(gradle)
        expect:
        registry instanceof GradleInternalServiceRegistry
    }

    def providesAListenerManager() {
        setup:
        ListenerManager listenerManager = expectListenerManagerCreated()
        expect:
        assertThat(registry.get(ListenerManager), sameInstance(listenerManager))
    }

    @Timeout(5)
    def providesAScriptCompilerFactory() {
        setup:
        expectListenerManagerCreated()

        expect:
        registry.get(ScriptCompilerFactory) instanceof DefaultScriptCompilerFactory
        registry.get(ScriptCompilerFactory) == registry.get(ScriptCompilerFactory)
    }

    def providesACacheRepositoryAndCleansUpOnClose() {
        setup:
        1 * cacheFactory.close()

        expect:
        registry.get(CacheRepository) instanceof DefaultCacheRepository
        registry.get(CacheRepository) == registry.get(CacheRepository)
        registry.close()
    }

    def providesAnInitScriptHandler() {
        setup:
        allowGetCoreImplClassLoader()
        expectScriptClassLoaderCreated()
        expectListenerManagerCreated()
        allowGetGradleDistributionLocator()

        expect:
        registry.get(InitScriptHandler) instanceof InitScriptHandler
        registry.get(InitScriptHandler) == registry.get(InitScriptHandler)
    }

    def providesAScriptObjectConfigurerFactory() {
        setup:
        allowGetCoreImplClassLoader()
        expectListenerManagerCreated()
        expectScriptClassLoaderCreated()
        expect:
        assertThat(registry.get(ScriptPluginFactory), instanceOf(DefaultScriptPluginFactory))
        assertThat(registry.get(ScriptPluginFactory), sameInstance(registry.get(ScriptPluginFactory)))
    }

    def providesASettingsProcessor() {
        setup:
        allowGetCoreImplClassLoader()
        expectListenerManagerCreated()
        expectScriptClassLoaderCreated()
        expect:
        assertThat(registry.get(SettingsProcessor), instanceOf(PropertiesLoadingSettingsProcessor))
        assertThat(registry.get(SettingsProcessor), sameInstance(registry.get(SettingsProcessor)))
    }

    def providesAnExceptionAnalyser() {
        setup:
        expectListenerManagerCreated()
        expect:
        assertThat(registry.get(ExceptionAnalyser), instanceOf(MultipleBuildFailuresExceptionAnalyser))
        assertThat(registry.get(ExceptionAnalyser).delegate, instanceOf(DefaultExceptionAnalyser))
        assertThat(registry.get(ExceptionAnalyser), sameInstance(registry.get(ExceptionAnalyser)))
    }

    def providesAWorkerProcessFactory() {
        setup:
        expectParentServiceLocated(MessagingServer)
        allowGetCoreImplClassLoader()

        expect:
        assertThat(registry.getFactory(WorkerProcessBuilder), instanceOf(DefaultWorkerProcessFactory))
    }

    def providesAnIsolatedAntBuilder() {
        setup:
        expectParentServiceLocated(ClassLoaderFactory)
        allowGetCoreImplClassLoader()
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

    def providesAnExecutorFactory() {
        expect:
        assertThat(registry.get(ExecutorFactory), instanceOf(DefaultExecutorFactory))
        assertThat(registry.get(ExecutorFactory), sameInstance(registry.get(ExecutorFactory)))
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

    private <T> T expectParentServiceLocated(Class<T> type) {
        T t = Mock(type)
        parent.get(type) >> t
        t
    }

    private ListenerManager expectListenerManagerCreated() {
        final ListenerManager listenerManager = new DefaultListenerManager()
        final ListenerManager listenerManagerParent = Mock()
        parent.get(ListenerManager) >> listenerManagerParent
        1 * listenerManagerParent.createChild() >> listenerManager
        listenerManager
    }

    private void allowGetCoreImplClassLoader() {
        classLoaderRegistry.getCoreImplClassLoader() >> new ClassLoader() {}
    }

    private void expectScriptClassLoaderCreated() {
        1 * classLoaderRegistry.createScriptClassLoader() >> new MultiParentClassLoader()
    }

    private void allowGetGradleDistributionLocator() {
        parent.get(GradleDistributionLocator) >> Mock(GradleDistributionLocator)
    }

    public interface ClosableCacheFactory extends CacheFactory {
        void close()
    }
}