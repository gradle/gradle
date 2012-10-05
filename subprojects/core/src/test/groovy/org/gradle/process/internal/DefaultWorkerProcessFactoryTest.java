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

package org.gradle.process.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.Factory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.inet.SocketInetAddress;
import org.gradle.process.internal.child.IsolatedApplicationClassLoaderWorker;
import org.gradle.process.internal.launcher.GradleWorkerMain;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
@Ignore
public class DefaultWorkerProcessFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final MessagingServer messagingServer = context.mock(MessagingServer.class);
    private final ClassPathRegistry classPathRegistry = context.mock(ClassPathRegistry.class);
    private final FileResolver fileResolver = context.mock(FileResolver.class);
    private final IdGenerator<Object> idGenerator = context.mock(IdGenerator.class);
    private final DefaultWorkerProcessFactory factory = new DefaultWorkerProcessFactory(LogLevel.LIFECYCLE, messagingServer, classPathRegistry, fileResolver,
            idGenerator);

    @Test
    public void createsAndConfiguresAWorkerProcess() throws Exception {
        final Set<File> processClassPath = Collections.singleton(new File("something.jar"));
        final org.gradle.internal.classpath.ClassPath classPath = context.mock(org.gradle.internal.classpath.ClassPath.class);

        context.checking(new Expectations() {{
            one(classPathRegistry).getClassPath("WORKER_PROCESS");
            will(returnValue(classPath));
            allowing(classPath).getAsFiles();
            will(returnValue(processClassPath));
            allowing(fileResolver).resolveLater(".");
            will(returnValue(new Factory<File>() {
                public File create() {
                    return new File(".");
                }
            }));
            allowing(fileResolver).resolveFiles(with(Matchers.<Object>notNullValue()));
            will(returnValue(new SimpleFileCollection()));
        }});

        WorkerProcessBuilder builder = factory.create();

        assertThat(builder.getJavaCommand().getMain(), equalTo(GradleWorkerMain.class.getName()));
        assertThat(builder.getLogLevel(), equalTo(LogLevel.LIFECYCLE));

        builder.worker(new TestAction());
        builder.applicationClasspath(Arrays.asList(new File("app.jar")));
        builder.sharedPackages("package1", "package2");

        final Address serverAddress = new SocketInetAddress(InetAddress.getByName("127.0.0.1"), 40);

        context.checking(new Expectations(){{
            one(messagingServer).accept(with(notNullValue(Action.class)));
            will(returnValue(serverAddress));
            one(idGenerator).generateId();
            will(returnValue("<id>"));
        }});

        WorkerProcess process = builder.build();

        assertThat(process, instanceOf(DefaultWorkerProcess.class));

        ObjectInputStream instr = new ObjectInputStream(builder.getJavaCommand().getStandardInput());
        assertThat(instr.readObject(), instanceOf(IsolatedApplicationClassLoaderWorker.class));
    }

    private static class TestAction implements Action<WorkerProcessContext>, Serializable {
        public void execute(WorkerProcessContext workerProcessContext) {
            throw new UnsupportedOperationException();
        }
    }
}
