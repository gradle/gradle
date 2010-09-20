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
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.tasks.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultCacheRepository;
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
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessBuilder;
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

import java.io.File;
import java.util.Collections;

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
    private final CacheFactory cacheFactory = context.mock(CacheFactory.class);
    private final ClassPathRegistry classPathRegistry = context.mock(ClassPathRegistry.class);
    private final TopLevelBuildServiceRegistry factory = new TopLevelBuildServiceRegistry(parent, startParameter);
    private final ClassLoaderFactory classLoaderFactory = context.mock(ClassLoaderFactory.class);
    private final Factory<LoggingManagerInternal> loggingManagerFactory = context.mock(Factory.class);
    private final ProgressLoggerFactory progressLoggerFactory = context.mock(ProgressLoggerFactory.class);

    @Before
    public void setUp() {
        startParameter.setGradleUserHomeDir(tmpDir.getDir());
        context.checking(new Expectations(){{
            allowing(parent).get(CacheFactory.class);
            will(returnValue(cacheFactory));
            allowing(parent).get(ClassPathRegistry.class);
            will(returnValue(classPathRegistry));
            allowing(parent).get(ClassLoaderFactory.class);
            will(returnValue(classLoaderFactory));
            allowing(parent).getFactory(LoggingManagerInternal.class);
            will(returnValue(loggingManagerFactory));
            allowing(parent).get(ProgressLoggerFactory.class);
            will(returnValue(progressLoggerFactory));
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
    public void providesATaskExecuter() {
        expectListenerManagerCreated();
        context.checking(new Expectations(){{
            allowing(cacheFactory).open(with(notNullValue(File.class)), with(equalTo(startParameter.getCacheUsage())), with(equalTo(Collections.EMPTY_MAP)));
        }});
        assertThat(factory.get(TaskExecuter.class), instanceOf(ExecuteAtMostOnceTaskExecuter.class));
        assertThat(factory.get(TaskExecuter.class), sameInstance(factory.get(TaskExecuter.class)));
    }

    @Test
    public void providesARepositoryHandlerFactory() {
        assertThat(factory.getFactory(RepositoryHandler.class), instanceOf(DefaultRepositoryHandlerFactory.class));
    }

    @Test
    public void providesAScriptCompilerFactory() {
        expectListenerManagerCreated();
        assertThat(factory.get(ScriptCompilerFactory.class), instanceOf(DefaultScriptCompilerFactory.class));
        assertThat(factory.get(ScriptCompilerFactory.class), sameInstance(factory.get(ScriptCompilerFactory.class)));
    }

    @Test
    public void providesACacheRepository() {
        assertThat(factory.get(CacheRepository.class), instanceOf(DefaultCacheRepository.class));
        assertThat(factory.get(CacheRepository.class), sameInstance(factory.get(CacheRepository.class)));
    }

    @Test
    public void providesAnInitScriptHandler() {
        expectScriptClassLoaderCreated();
        expectListenerManagerCreated();
        assertThat(factory.get(InitScriptHandler.class), instanceOf(InitScriptHandler.class));
        assertThat(factory.get(InitScriptHandler.class), sameInstance(factory.get(InitScriptHandler.class)));
    }

    @Test
    public void providesAScriptObjectConfigurerFactory() {
        expectListenerManagerCreated();
        expectScriptClassLoaderCreated();
        assertThat(factory.get(ScriptPluginFactory.class), instanceOf(DefaultScriptPluginFactory.class));
        assertThat(factory.get(ScriptPluginFactory.class), sameInstance(factory.get(ScriptPluginFactory.class)));
    }

    @Test
    public void providesASettingsProcessor() {
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
        context.checking(new Expectations() {{
            one(classLoaderFactory).getRootClassLoader();
            will(returnValue(new ClassLoader() {
            }));
        }});

        assertThat(factory.getFactory(WorkerProcessBuilder.class), instanceOf(DefaultWorkerProcessFactory.class));
    }

    @Test
    public void providesAProjectFactory() {
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
            one(classLoaderFactory).createScriptClassLoader();
            will(returnValue(new MultiParentClassLoader()));
        }});
    }
}
