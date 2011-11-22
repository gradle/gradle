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

import org.gradle.StartParameter;
import org.gradle.api.internal.*;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.DefaultBuildConfigurer;
import org.gradle.configuration.DefaultScriptPluginFactory;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.DefaultBuildExecuter;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.initialization.*;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.ClassLoaderFactory;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.MultiParentClassLoader;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class TopLevelBuildServiceRegistryTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final StartParameter startParameter = new StartParameter();
    private final Factory<CacheFactory> cacheFactoryFactory = context.mock(Factory.class);
    private final ClosableCacheFactory cacheFactory = context.mock(ClosableCacheFactory.class);
    private final ClassLoaderRegistry classLoaderRegistry = context.mock(ClassLoaderRegistry.class);
    private final TopLevelBuildServiceRegistry registry = new TopLevelBuildServiceRegistry(parent, startParameter);

    @Before
    public void setUp() {
        startParameter.setGradleUserHomeDir(tmpDir.getDir());
        context.checking(new Expectations(){{
            allowing(parent).getFactory(CacheFactory.class);
            will(returnValue(cacheFactoryFactory));
            allowing(cacheFactoryFactory).create();
            will(returnValue(cacheFactory));
            allowing(parent).get(ClassLoaderRegistry.class);
            will(returnValue(classLoaderRegistry));
            allowing(parent).getFactory(LoggingManagerInternal.class);
            will(returnValue(context.mock(Factory.class)));
            allowing(parent).get(ModuleRegistry.class);
            will(returnValue(new DefaultModuleRegistry()));
            allowing(parent).get(PluginModuleRegistry.class);
            will(returnValue(context.mock(PluginModuleRegistry.class)));
        }});
    }
    
    @Test
    public void delegatesToParentForUnknownService() {
        context.checking(new Expectations(){{
            allowing(parent).get(String.class);
            will(returnValue("value"));
        }});

        assertThat(registry.get(String.class), equalTo("value"));
    }

    @Test
    public void throwsExceptionForUnknownDomainObject() {
        try {
            registry.createFor("string");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Cannot create services for unknown domain object of type String."));
        }
    }

    @Test
    public void canCreateServicesForAGradleInstance() {
        GradleInternal gradle = context.mock(GradleInternal.class);
        ServiceRegistryFactory registry = this.registry.createFor(gradle);
        assertThat(registry, instanceOf(GradleInternalServiceRegistry.class));
    }

    @Test
    public void providesAListenerManager() {
        ListenerManager listenerManager = expectListenerManagerCreated();
        assertThat(registry.get(ListenerManager.class), sameInstance(listenerManager));
    }

    @Test
    public void providesAScriptCompilerFactory() {
        expectListenerManagerCreated();
        assertThat(registry.get(ScriptCompilerFactory.class), instanceOf(DefaultScriptCompilerFactory.class));
        assertThat(registry.get(ScriptCompilerFactory.class), sameInstance(registry.get(ScriptCompilerFactory.class)));
    }

    @Test
    public void providesACacheRepositoryAndCleansUpOnClose() {
        assertThat(registry.get(CacheRepository.class), instanceOf(DefaultCacheRepository.class));
        assertThat(registry.get(CacheRepository.class), sameInstance(registry.get(CacheRepository.class)));

        context.checking(new Expectations() {{
            one(cacheFactory).close();
        }});

        registry.close();
    }

    @Test
    public void providesAnInitScriptHandler() {
        allowGetCoreImplClassLoader();
        expectScriptClassLoaderCreated();
        expectListenerManagerCreated();
        allowGetGradleDistributionLocator();

        assertThat(registry.get(InitScriptHandler.class), instanceOf(InitScriptHandler.class));
        assertThat(registry.get(InitScriptHandler.class), sameInstance(registry.get(InitScriptHandler.class)));
    }

    @Test
    public void providesAScriptObjectConfigurerFactory() {
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();

        assertThat(registry.get(ScriptPluginFactory.class), instanceOf(DefaultScriptPluginFactory.class));
        assertThat(registry.get(ScriptPluginFactory.class), sameInstance(registry.get(ScriptPluginFactory.class)));
    }

    @Test
    public void providesASettingsProcessor() {
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();

        assertThat(registry.get(SettingsProcessor.class), instanceOf(PropertiesLoadingSettingsProcessor.class));
        assertThat(registry.get(SettingsProcessor.class), sameInstance(registry.get(SettingsProcessor.class)));
    }

    @Test
    public void providesAnExceptionAnalyser() {
        expectListenerManagerCreated();

        assertThat(registry.get(ExceptionAnalyser.class), instanceOf(DefaultExceptionAnalyser.class));
        assertThat(registry.get(ExceptionAnalyser.class), sameInstance(registry.get(ExceptionAnalyser.class)));
    }

    @Test
    public void providesAWorkerProcessFactory() {
        expectParentServiceLocated(MessagingServer.class);
        allowGetCoreImplClassLoader();

        assertThat(registry.getFactory(WorkerProcessBuilder.class), instanceOf(DefaultWorkerProcessFactory.class));
    }

    @Test
    public void providesAnIsolatedAntBuilder() {
        expectParentServiceLocated(ClassLoaderFactory.class);
        allowGetCoreImplClassLoader();

        assertThat(registry.get(IsolatedAntBuilder.class), instanceOf(DefaultIsolatedAntBuilder.class));
        assertThat(registry.get(IsolatedAntBuilder.class), sameInstance(registry.get(IsolatedAntBuilder.class)));
    }

    @Test
    public void providesAProjectFactory() {
        expectParentServiceLocated(Instantiator.class);
        expectParentServiceLocated(ClassGenerator.class);

        assertThat(registry.get(IProjectFactory.class), instanceOf(ProjectFactory.class));
        assertThat(registry.get(IProjectFactory.class), sameInstance(registry.get(IProjectFactory.class)));
    }

    @Test
    public void providesAnExecutorFactory() {
        assertThat(registry.get(ExecutorFactory.class), instanceOf(DefaultExecutorFactory.class));
        assertThat(registry.get(ExecutorFactory.class), sameInstance(registry.get(ExecutorFactory.class)));
    }

    @Test
    public void providesABuildConfigurer() {
        assertThat(registry.get(BuildConfigurer.class), instanceOf(DefaultBuildConfigurer.class));
        assertThat(registry.get(BuildConfigurer.class), sameInstance(registry.get(BuildConfigurer.class)));
    }

    @Test
    public void providesAPropertiesLoader() {
        assertThat(registry.get(IGradlePropertiesLoader.class), instanceOf(DefaultGradlePropertiesLoader.class));
        assertThat(registry.get(IGradlePropertiesLoader.class), sameInstance(registry.get(IGradlePropertiesLoader.class)));
    }

    @Test
    public void providesABuildLoader() {
        expectParentServiceLocated(Instantiator.class);

        assertThat(registry.get(BuildLoader.class), instanceOf(ProjectPropertySettingBuildLoader.class));
        assertThat(registry.get(BuildLoader.class), sameInstance(registry.get(BuildLoader.class)));
    }

    @Test
    public void providesABuildExecuter() {
        assertThat(registry.get(BuildExecuter.class), instanceOf(DefaultBuildExecuter.class));
        assertThat(registry.get(BuildExecuter.class), sameInstance(registry.get(BuildExecuter.class)));
    }

    private <T> T expectParentServiceLocated(final Class<T> type) {
        final T t = context.mock(type);
        context.checking(new Expectations() {{
            allowing(parent).get(type);
            will(returnValue(t));
        }});
        return t;
    }

    private ListenerManager expectListenerManagerCreated() {
        final ListenerManager listenerManager = new DefaultListenerManager();
        context.checking(new Expectations(){{
            allowing(parent).get(ListenerManager.class);
            ListenerManager parent = context.mock(ListenerManager.class);
            will(returnValue(parent));
            one(parent).createChild();
            will(returnValue(listenerManager));
        }});
        return listenerManager;
    }

    private void expectScriptClassLoaderCreated() {
        context.checking(new Expectations() {{
            one(classLoaderRegistry).createScriptClassLoader();
            will(returnValue(new MultiParentClassLoader()));
        }});
    }

    private void allowGetCoreImplClassLoader() {
        context.checking(new Expectations() {{
            allowing(classLoaderRegistry).getCoreImplClassLoader();
            will(returnValue(new ClassLoader() {
            }));
        }});
    }

    private void allowGetGradleDistributionLocator() {
        context.checking(new Expectations() {{
            allowing(parent).get(GradleDistributionLocator.class);
            will(returnValue(context.mock(GradleDistributionLocator.class)));
        }});
    }

    public interface ClosableCacheFactory extends CacheFactory {
        void close();
    }
}
