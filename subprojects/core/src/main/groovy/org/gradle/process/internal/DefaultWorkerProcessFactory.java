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
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.ConnectionAcceptor;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.child.ApplicationClassesInIsolatedClassLoaderWorkerFactory;
import org.gradle.process.internal.child.ApplicationClassesInSystemClassLoaderWorkerFactory;
import org.gradle.process.internal.child.EncodedStream;
import org.gradle.process.internal.child.WorkerFactory;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultWorkerProcessFactory implements Factory<WorkerProcessBuilder> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerProcessFactory.class);
    private final LogLevel workerLogLevel;
    private final MessagingServer server;
    private final ClassPathRegistry classPathRegistry;
    private final FileResolver resolver;
    private final IdGenerator<?> idGenerator;

    public DefaultWorkerProcessFactory(LogLevel workerLogLevel, MessagingServer server,
                                       ClassPathRegistry classPathRegistry, FileResolver resolver,
                                       IdGenerator<?> idGenerator) {
        this.workerLogLevel = workerLogLevel;
        this.server = server;
        this.classPathRegistry = classPathRegistry;
        this.resolver = resolver;
        this.idGenerator = idGenerator;
    }

    public WorkerProcessBuilder create() {
        return new DefaultWorkerProcessBuilder();
    }

    private class DefaultWorkerProcessBuilder extends WorkerProcessBuilder {
        public DefaultWorkerProcessBuilder() {
            super(resolver);
            setLogLevel(workerLogLevel);
        }

        @Override
        public WorkerProcess build() {
            if (getWorker() == null) {
                throw new IllegalStateException("No worker action specified for this worker process.");
            }

            final DefaultWorkerProcess workerProcess = new DefaultWorkerProcess(120, TimeUnit.SECONDS);
            ConnectionAcceptor acceptor = server.accept(new Action<ObjectConnection>() {
                public void execute(ObjectConnection connection) {
                    workerProcess.onConnect(connection);
                }
            });
            workerProcess.startAccepting(acceptor);
            Address localAddress = acceptor.getAddress();

            // Build configuration for GradleWorkerMain
            List<URL> implementationClassPath = ClasspathUtil.getClasspath(getWorker().getClass().getClassLoader());
            Object id = idGenerator.generateId();
            String displayName = getBaseName() + " " + id;

            WorkerFactory workerFactory;
            if (isLoadApplicationInSystemClassLoader()) {
                workerFactory = new ApplicationClassesInSystemClassLoaderWorkerFactory(id, displayName, this,
                        implementationClassPath, localAddress, classPathRegistry);
            } else {
                workerFactory = new ApplicationClassesInIsolatedClassLoaderWorkerFactory(id, displayName, this,
                        implementationClassPath, localAddress, classPathRegistry);
            }

            LOGGER.debug("Creating {}", displayName);
            LOGGER.debug("Using application classpath {}", getApplicationClasspath());
            LOGGER.debug("Using implementation classpath {}", implementationClassPath);

            JavaExecHandleBuilder javaCommand = getJavaCommand();
            attachStdInContent(workerFactory, javaCommand);
            workerFactory.prepareJavaCommand(javaCommand);
            javaCommand.setDisplayName(displayName);
            javaCommand.args("'" + displayName + "'");
            ExecHandle execHandle = javaCommand.build();

            workerProcess.setExecHandle(execHandle);

            return workerProcess;
        }

        private void attachStdInContent(WorkerFactory workerFactory, JavaExecHandleBuilder javaCommand) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            OutputStream encoded = new EncodedStream.EncodedOutput(bytes);
            GUtil.serialize(workerFactory.create(), encoded);
            ByteArrayInputStream stdinContent = new ByteArrayInputStream(bytes.toByteArray());
            javaCommand.setStandardInput(stdinContent);
        }
    }
}
