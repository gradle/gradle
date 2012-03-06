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

package org.gradle.api.internal.project;


import org.gradle.StartParameter
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
import org.gradle.internal.Factory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.DefaultListenerManager
import org.gradle.listener.ListenerManager
import org.gradle.logging.LoggingManagerInternal
import org.gradle.messaging.concurrent.DefaultExecutorFactory
import org.gradle.messaging.concurrent.ExecutorFactory
import org.gradle.messaging.remote.MessagingServer
import org.gradle.process.internal.DefaultWorkerProcessFactory
import org.gradle.process.internal.WorkerProcessBuilder
import org.gradle.profile.ProfileEventAdapter
import org.gradle.util.ClassLoaderFactory
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.MultiParentClassLoader
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JUnit4Mockery
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Timeout
import org.gradle.api.internal.*
import org.gradle.initialization.*
import static org.hamcrest.Matchers.instanceOf
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

public class TopLevelBuildServiceRegistryTest extends Specification {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final StartParameter startParameter = new StartParameter();
    private final ServiceRegistry parent = Mock();
    private final Factory<CacheFactory> cacheFactoryFactory = Mock();
    private final ClosableCacheFactory cacheFactory = Mock();
    private final ClassLoaderRegistry classLoaderRegistry = Mock();

    private final TopLevelBuildServiceRegistry registry = new TopLevelBuildServiceRegistry(parent, startParameter);

    def setup() {
        startParameter.gradleUserHomeDir = tmpDir.dir
        _ * parent.getFactory(CacheFactory.class) >> cacheFactoryFactory
        _ * cacheFactoryFactory.create() >> cacheFactory
        _ * parent.get(ClassLoaderRegistry.class) >> classLoaderRegistry
        _ * parent.getFactory(LoggingManagerInternal) >> Mock(Factory)
        _ * parent.get(ModuleRegistry) >> new DefaultModuleRegistry()
        _ * parent.get(PluginModuleRegistry.class) >> Mock(PluginModuleRegistry)
    }

    def delegatesToParentForUnknownService() {
        setup:
        parent.get(String.class) >> "value"

        expect:
        registry.get(String.class) == "value"
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
        GradleInternal gradle = Mock();
        ServiceRegistryFactory registry = this.registry.createFor(gradle);
        expect:
        registry instanceof GradleInternalServiceRegistry
    }

    def providesAListenerManager() {
        setup:
        ListenerManager listenerManager = expectListenerManagerCreated();
        expect:
        assertThat(registry.get(ListenerManager.class), sameInstance(listenerManager))
    }

    @Timeout(5)
    def providesAScriptCompilerFactory() {
        setup:
        expectListenerManagerCreated();

        expect:
        registry.get(ScriptCompilerFactory.class) instanceof DefaultScriptCompilerFactory
        registry.get(ScriptCompilerFactory.class) == registry.get(ScriptCompilerFactory)
    }

    def providesACacheRepositoryAndCleansUpOnClose() {
        setup:
        1 * cacheFactory.close()

        expect:
        registry.get(CacheRepository.class) instanceof DefaultCacheRepository
        registry.get(CacheRepository.class) == registry.get(CacheRepository.class)
        registry.close();
    }

    def providesAnInitScriptHandler() {
        setup:
        allowGetCoreImplClassLoader();
        expectScriptClassLoaderCreated();
        expectListenerManagerCreated();
        allowGetGradleDistributionLocator()

        expect:
        registry.get(InitScriptHandler.class) instanceof InitScriptHandler
        registry.get(InitScriptHandler.class) == registry.get(InitScriptHandler.class)
    }

    def providesAScriptObjectConfigurerFactory() {
        setup:
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();
        expect:
        assertThat(registry.get(ScriptPluginFactory.class), instanceOf(DefaultScriptPluginFactory.class));
        assertThat(registry.get(ScriptPluginFactory.class), sameInstance(registry.get(ScriptPluginFactory.class)));
    }

    def providesASettingsProcessor() {
        setup:
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();
        expect:
        assertThat(registry.get(SettingsProcessor.class), instanceOf(PropertiesLoadingSettingsProcessor.class));
        assertThat(registry.get(SettingsProcessor.class), sameInstance(registry.get(SettingsProcessor.class)));
    }

    def providesAnExceptionAnalyser() {
        setup:
        expectListenerManagerCreated();
        expect:
        assertThat(registry.get(ExceptionAnalyser.class), instanceOf(DefaultExceptionAnalyser.class));
        assertThat(registry.get(ExceptionAnalyser.class), sameInstance(registry.get(ExceptionAnalyser.class)));
    }

    def providesAWorkerProcessFactory() {
        setup:
        expectParentServiceLocated(MessagingServer.class);
        allowGetCoreImplClassLoader();

        expect:
        assertThat(registry.getFactory(WorkerProcessBuilder.class), instanceOf(DefaultWorkerProcessFactory.class));
    }

    def providesAnIsolatedAntBuilder() {
        setup:
        expectParentServiceLocated(ClassLoaderFactory.class);
        allowGetCoreImplClassLoader();
        expect:

        assertThat(registry.get(IsolatedAntBuilder.class), instanceOf(DefaultIsolatedAntBuilder.class));
        assertThat(registry.get(IsolatedAntBuilder.class), sameInstance(registry.get(IsolatedAntBuilder.class)));
    }

    def providesAProjectFactory() {
        setup:
        expectParentServiceLocated(Instantiator.class);
        expectParentServiceLocated(ClassGenerator.class);
        expect:
        assertThat(registry.get(IProjectFactory.class), instanceOf(ProjectFactory.class));
        assertThat(registry.get(IProjectFactory.class), sameInstance(registry.get(IProjectFactory.class)));
    }

    def providesAnExecutorFactory() {
        expect:
        assertThat(registry.get(ExecutorFactory.class), instanceOf(DefaultExecutorFactory.class));
        assertThat(registry.get(ExecutorFactory.class), sameInstance(registry.get(ExecutorFactory.class)));
    }

    def providesABuildConfigurer() {
        expect:
        assertThat(registry.get(BuildConfigurer.class), instanceOf(DefaultBuildConfigurer.class));
        assertThat(registry.get(BuildConfigurer.class), sameInstance(registry.get(BuildConfigurer.class)));
    }

    def providesAPropertiesLoader() {
        expect:
        assertThat(registry.get(IGradlePropertiesLoader.class), instanceOf(DefaultGradlePropertiesLoader.class));
        assertThat(registry.get(IGradlePropertiesLoader.class), sameInstance(registry.get(IGradlePropertiesLoader.class)));
    }

    def providesABuildLoader() {
        setup:
        expectParentServiceLocated(Instantiator.class);
        expect:
        assertThat(registry.get(BuildLoader.class), instanceOf(ProjectPropertySettingBuildLoader.class));
        assertThat(registry.get(BuildLoader.class), sameInstance(registry.get(BuildLoader.class)));
    }

    def providesAProfileEventAdapter() {
        setup:
        expectParentServiceLocated(BuildRequestMetaData.class);
        expectListenerManagerCreated();

        expect:
        assertThat(registry.get(ProfileEventAdapter.class), instanceOf(ProfileEventAdapter.class));
        assertThat(registry.get(ProfileEventAdapter.class), sameInstance(registry.get(ProfileEventAdapter.class)));
    }

    private <T> T expectParentServiceLocated(final Class<T> type) {
        final T t = Mock(type);
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
        parent.get(GradleDistributionLocator.class) >> Mock(GradleDistributionLocator)
    }

    public interface ClosableCacheFactory extends CacheFactory {
        void close();
    }
}