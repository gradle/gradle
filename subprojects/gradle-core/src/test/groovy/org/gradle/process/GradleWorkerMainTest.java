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

package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.LoggingConfigurer;
import org.gradle.util.ObservableUrlClassLoader;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class GradleWorkerMainTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ObservableUrlClassLoader sharedClassLoader = new ObservableUrlClassLoader(
            getClass().getClassLoader());
    private final LoggingConfigurer loggingConfigurer = context.mock(LoggingConfigurer.class);
    private InputStream original;
    private final ObservableUrlClassLoader implementationClassLoader = new ImplementationClassLoader(sharedClassLoader);

    @Before
    public void setUp() {
        original = System.in;
        System.setProperty("action.result", "old value");
    }

    @After
    public void tearDown() {
        System.setIn(original);
    }

    @Test
    public void readsConfigFromStdInAndExecutesSuppliedAction() throws Exception {
        TestAction action = new TestAction();
        System.setIn(serialize(LogLevel.DEBUG, new ArrayList<File>(), new ArrayList<String>(),
                new ArrayList<File>(), action, new URI("test:server")));

        context.checking(new Expectations() {{
            one(loggingConfigurer).configure(LogLevel.LIFECYCLE);
            one(loggingConfigurer).configure(LogLevel.DEBUG);
        }});

        new TestGradleWorkerMain().run();
        assertThat(System.getProperty("action.result"), equalTo("result"));
    }

    private InputStream serialize(Object... config) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
        for (Object configObject : config) {
            objectStream.writeObject(configObject);
        }
        objectStream.close();
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private class TestGradleWorkerMain extends GradleWorkerMain {

        @Override
        protected LoggingConfigurer createLoggingConfigurer() {
            return loggingConfigurer;
        }

        @Override
        protected ObservableUrlClassLoader createSharedClassLoader() {
            return sharedClassLoader;
        }

        @Override
        protected ObservableUrlClassLoader createImplementationClassLoader(ClassLoader system,
                                                                           ClassLoader application) {
            return implementationClassLoader;
        }
    }

    private class ImplementationClassLoader extends ObservableUrlClassLoader {
        private ImplementationClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String className) throws ClassNotFoundException {
            if (className.equals(WorkerMain.class.getName())) {
                return TestWorkerMain.class;
            }
            return super.loadClass(className);
        }
    }

    public static class TestWorkerMain implements Runnable {
        private final Action<WorkerProcessContext> action;
        private final URI serverAddress;
        private final ClassLoader classLoader;

        public TestWorkerMain(Action<WorkerProcessContext> action, URI serverAddress, ClassLoader classLoader) {
            this.action = action;
            this.serverAddress = serverAddress;
            this.classLoader = classLoader;
        }

        public void run() {
            assertThat(action, instanceOf(TestAction.class));
            assertThat(serverAddress, notNullValue());
            assertThat(classLoader, notNullValue());
            System.setProperty("action.result", "result");
        }
    }

    private static class TestAction implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
        }
    }
}
