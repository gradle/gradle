/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.worker;

import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.logging.LoggingManager;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.child.ApplicationClassesInSystemClassLoaderWorkerImplementationFactory;

import java.io.File;

public class DefaultWorkerProcessFactory implements WorkerProcessFactory {

    private final LoggingManager loggingManager;
    private final MessagingServer server;
    private final IdGenerator<Long> idGenerator;
    private final JavaExecHandleFactory execHandleFactory;
    private final JvmVersionDetector jvmVersionDetector;
    private final OutputEventListener outputEventListener;
    private final ApplicationClassesInSystemClassLoaderWorkerImplementationFactory workerImplementationFactory;
    private final MemoryManager memoryManager;
    private int connectTimeoutSeconds = 120;

    public DefaultWorkerProcessFactory(LoggingManager loggingManager, MessagingServer server, ClassPathRegistry classPathRegistry, IdGenerator<Long> idGenerator,
                                       File gradleUserHomeDir, TemporaryFileProvider temporaryFileProvider, JavaExecHandleFactory execHandleFactory,
                                       JvmVersionDetector jvmVersionDetector, OutputEventListener outputEventListener, MemoryManager memoryManager) {
        this.loggingManager = loggingManager;
        this.server = server;
        this.idGenerator = idGenerator;
        this.execHandleFactory = execHandleFactory;
        this.jvmVersionDetector = jvmVersionDetector;
        this.outputEventListener = outputEventListener;
        this.workerImplementationFactory = new ApplicationClassesInSystemClassLoaderWorkerImplementationFactory(classPathRegistry, temporaryFileProvider, gradleUserHomeDir);
        this.memoryManager = memoryManager;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    @Override
    public WorkerProcessBuilder create(Action<? super WorkerProcessContext> workerAction) {
        DefaultWorkerProcessBuilder builder = newWorkerProcessBuilder();
        builder.worker(workerAction);
        builder.setImplementationClasspath(ClasspathUtil.getClasspath(workerAction.getClass().getClassLoader()).getAsURLs());
        return builder;
    }

    @Override
    public <IN, OUT> SingleRequestWorkerProcessBuilder<IN, OUT> singleRequestWorker(Class<? extends RequestHandler<? super IN, ? extends OUT>> workerImplementation) {
        return new DefaultSingleRequestWorkerProcessBuilder<>(workerImplementation, newWorkerProcessBuilder(), outputEventListener);
    }

    @Override
    public <IN, OUT> MultiRequestWorkerProcessBuilder<IN, OUT> multiRequestWorker(Class<? extends RequestHandler<? super IN, ? extends OUT>> workerImplementation) {
        return new DefaultMultiRequestWorkerProcessBuilder<>(workerImplementation, newWorkerProcessBuilder(), outputEventListener);
    }

    private DefaultWorkerProcessBuilder newWorkerProcessBuilder() {
        DefaultWorkerProcessBuilder builder = new DefaultWorkerProcessBuilder(execHandleFactory, server, idGenerator, workerImplementationFactory, outputEventListener, memoryManager, jvmVersionDetector);
        builder.setLogLevel(loggingManager.getLevel());
        builder.setConnectTimeoutSeconds(connectTimeoutSeconds);
        return builder;
    }
}
