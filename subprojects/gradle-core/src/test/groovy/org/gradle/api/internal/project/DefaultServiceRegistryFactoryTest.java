/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.tasks.DefaultTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultServiceRegistryFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ListenerManager listenerManager = context.mock(ListenerManager.class);
    private final DefaultServiceRegistryFactory factory = new DefaultServiceRegistryFactory(new StartParameter(),
            listenerManager);

    @Test
    public void throwsExceptionForUnknownService() {
        try {
            factory.get(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type String available."));
        }
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
        assertThat(factory.get(ListenerManager.class), sameInstance(listenerManager));
    }
    
    @Test
    public void providesAStandardOutputRedirector() {
        assertThat(factory.get(StandardOutputRedirector.class), instanceOf(DefaultStandardOutputRedirector.class));
        assertThat(factory.get(StandardOutputRedirector.class), sameInstance(factory.get(
                StandardOutputRedirector.class)));
    }

    @Test
    public void providesAPublishArtifactFactory() {
        assertThat(factory.get(PublishArtifactFactory.class), instanceOf(DefaultPublishArtifactFactory.class));
        assertThat(factory.get(PublishArtifactFactory.class), sameInstance(factory.get(PublishArtifactFactory.class)));
    }
    
    @Test
    public void providesATaskExecuter() {
        context.checking(new Expectations(){{
            allowing(listenerManager).createAnonymousBroadcaster(TaskActionListener.class);
            will(returnValue(new ListenerBroadcast<TaskActionListener>(TaskActionListener.class)));
            allowing(listenerManager).getBroadcaster(TaskActionListener.class);
            will(returnValue(context.mock(TaskActionListener.class)));
        }});

        assertThat(factory.get(TaskExecuter.class), instanceOf(DefaultTaskExecuter.class));
        assertThat(factory.get(TaskExecuter.class), sameInstance(factory.get(TaskExecuter.class)));
    }

    @Test
    public void providesARepositoryHandlerFactory() {
        assertThat(factory.get(RepositoryHandlerFactory.class), instanceOf(DefaultRepositoryHandlerFactory.class));
        assertThat(factory.get(RepositoryHandlerFactory.class), sameInstance(factory.get(RepositoryHandlerFactory.class)));
    }

    @Test
    public void providesAScriptCompilerFactory() {
        assertThat(factory.get(ScriptCompilerFactory.class), instanceOf(DefaultScriptCompilerFactory.class));
        assertThat(factory.get(ScriptCompilerFactory.class), sameInstance(factory.get(ScriptCompilerFactory.class)));
    }
}
