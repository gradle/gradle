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
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.DefaultBuildConfigurer;
import org.gradle.configuration.DefaultScriptPluginFactory;
import org.gradle.configuration.ScriptPluginFactory;
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
    private final TopLevelBuildServiceRegistry factory = new TopLevelBuildServiceRegistry(parent, startParameter);
    private final ClassLoaderRegistry classLoaderRegistry = context.mock(ClassLoaderRegistry.class);
    private final Factory<LoggingManagerInternal> loggingManagerFactory = context.mock(Factory.class);

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
            will(returnValue(loggingManagerFactory));
        }});
    }
    
    @Test
    public void delegatesToParentForUnknownService() {
        context.checking(new Expectations(){{
            allowing(parent).get(String.class);
            will(returnValue("value"));
        }});

        assertThat(factory.get(String.class), equalTo("value"));
    }

    @Test
    public void throwsExceptionForUnknownDomainObject() {
        try {
            factory.createFor("string");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Cannot create services for unknown domain object of type String."));
        }
    }

    @Test
    public void canCreateServicesForAGradleInstance() {
        GradleInternal gradle = context.mock(GradleInternal.class);
        ServiceRegistryFactory registry = factory.createFor(gradle);
        assertThat(registry, instanceOf(GradleInternalServiceRegistry.class));
    }

    @Test
    public void providesAListenerManager() {
        ListenerManager listenerManager = expectListenerManagerCreated();
        assertThat(factory.get(ListenerManager.class), sameInstance(listenerManager));
    }

    @Test
    public void providesAPublishArtifactFactory() {
        assertThat(factory.get(PublishArtifactFactory.class), instanceOf(DefaultPublishArtifactFactory.class));
        assertThat(factory.get(PublishArtifactFactory.class), sameInstance(factory.get(PublishArtifactFactory.class)));
    }

    @Test
    public void providesAScriptCompilerFactory() {
        expectListenerManagerCreated();
        assertThat(factory.get(ScriptCompilerFactory.class), instanceOf(DefaultScriptCompilerFactory.class));
        assertThat(factory.get(ScriptCompilerFactory.class), sameInstance(factory.get(ScriptCompilerFactory.class)));
    }

    @Test
    public void providesACacheRepositoryAndCleansUpOnClose() {
        assertThat(factory.get(CacheRepository.class), instanceOf(DefaultCacheRepository.class));
        assertThat(factory.get(CacheRepository.class), sameInstance(factory.get(CacheRepository.class)));

        context.checking(new Expectations() {{
            one(cacheFactory).close();
        }});

        factory.close();
    }

    @Test
    public void providesAnInitScriptHandler() {
        allowGetCoreImplClassLoader();
        expectScriptClassLoaderCreated();
        expectListenerManagerCreated();

        assertThat(factory.get(InitScriptHandler.class), instanceOf(InitScriptHandler.class));
        assertThat(factory.get(InitScriptHandler.class), sameInstance(factory.get(InitScriptHandler.class)));
    }

    @Test
    public void providesAScriptObjectConfigurerFactory() {
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();

        assertThat(factory.get(ScriptPluginFactory.class), instanceOf(DefaultScriptPluginFactory.class));
        assertThat(factory.get(ScriptPluginFactory.class), sameInstance(factory.get(ScriptPluginFactory.class)));
    }

    @Test
    public void providesASettingsProcessor() {
        allowGetCoreImplClassLoader();
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();

        assertThat(factory.get(SettingsProcessor.class), instanceOf(PropertiesLoadingSettingsProcessor.class));
        assertThat(factory.get(SettingsProcessor.class), sameInstance(factory.get(SettingsProcessor.class)));
    }

    @Test
    public void providesAnExceptionAnalyser() {
        expectListenerManagerCreated();

        assertThat(factory.get(ExceptionAnalyser.class), instanceOf(DefaultExceptionAnalyser.class));
        assertThat(factory.get(ExceptionAnalyser.class), sameInstance(factory.get(ExceptionAnalyser.class)));
    }

    @Test
    public void providesAWorkerProcessFactory() {
        expectParentServiceLocated(MessagingServer.class);
        allowGetCoreImplClassLoader();

        assertThat(factory.getFactory(WorkerProcessBuilder.class), instanceOf(DefaultWorkerProcessFactory.class));
    }

    @Test
    public void providesAnIsolatedAntBuilder() {
        expectParentServiceLocated(ClassLoaderFactory.class);
        allowGetCoreImplClassLoader();

        assertThat(factory.get(IsolatedAntBuilder.class), instanceOf(DefaultIsolatedAntBuilder.class));
        assertThat(factory.get(IsolatedAntBuilder.class), sameInstance(factory.get(IsolatedAntBuilder.class)));
    }

    @Test
    public void providesAProjectFactory() {
        expectParentServiceLocated(Instantiator.class);
        expectParentServiceLocated(ClassGenerator.class);

        assertThat(factory.get(IProjectFactory.class), instanceOf(ProjectFactory.class));
        assertThat(factory.get(IProjectFactory.class), sameInstance(factory.get(IProjectFactory.class)));
    }

    @Test
    public void providesAnExecutorFactory() {
        assertThat(factory.get(ExecutorFactory.class), instanceOf(DefaultExecutorFactory.class));
        assertThat(factory.get(ExecutorFactory.class), sameInstance(factory.get(ExecutorFactory.class)));
    }

    @Test
    public void providesABuildConfigurer() {
        assertThat(factory.get(BuildConfigurer.class), instanceOf(DefaultBuildConfigurer.class));
        assertThat(factory.get(BuildConfigurer.class), sameInstance(factory.get(BuildConfigurer.class)));
    }

    @Test
    public void providesAPropertiesLoader() {
        assertThat(factory.get(IGradlePropertiesLoader.class), instanceOf(DefaultGradlePropertiesLoader.class));
        assertThat(factory.get(IGradlePropertiesLoader.class), sameInstance(factory.get(IGradlePropertiesLoader.class)));
    }

    @Test
    public void providesABuildLoader() {
        expectParentServiceLocated(Instantiator.class);

        assertThat(factory.get(BuildLoader.class), instanceOf(ProjectPropertySettingBuildLoader.class));
        assertThat(factory.get(BuildLoader.class), sameInstance(factory.get(BuildLoader.class)));
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

    public interface ClosableCacheFactory extends CacheFactory {
        void close();
    }
}
