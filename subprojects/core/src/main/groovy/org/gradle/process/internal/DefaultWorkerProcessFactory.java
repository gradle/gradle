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
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.id.IdGenerator;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.process.internal.child.ApplicationClassesInSystemClassLoaderWorkerFactory;

import java.io.File;

public class DefaultWorkerProcessFactory implements WorkerProcessFactory {
    private final LogLevel workerLogLevel;
    private final MessagingServer server;
    private final IdGenerator<?> idGenerator;
    private final File gradleUserHomeDir;
    private final ExecHandleFactory execHandleFactory;
    private final ApplicationClassesInSystemClassLoaderWorkerFactory workerFactory;
    private int connectTimeoutSeconds = 120;

    public DefaultWorkerProcessFactory(LogLevel workerLogLevel, MessagingServer server, ClassPathRegistry classPathRegistry, IdGenerator<?> idGenerator,
                                       File gradleUserHomeDir, TemporaryFileProvider temporaryFileProvider, ExecHandleFactory execHandleFactory) {
        this.workerLogLevel = workerLogLevel;
        this.server = server;
        this.idGenerator = idGenerator;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.execHandleFactory = execHandleFactory;
        workerFactory = new ApplicationClassesInSystemClassLoaderWorkerFactory(classPathRegistry, temporaryFileProvider);
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    @Override
    public WorkerProcessBuilder create(Action<? super WorkerProcessContext> workerAction) {
        DefaultWorkerProcessBuilder builder = newWorker();
        builder.worker(workerAction);
        builder.setImplementationClasspath(ClasspathUtil.getClasspath(workerAction.getClass().getClassLoader()));
        return builder;
    }

    private DefaultWorkerProcessBuilder newWorker() {
        DefaultWorkerProcessBuilder workerProcessBuilder = new DefaultWorkerProcessBuilder(execHandleFactory, server, idGenerator, workerFactory);
        workerProcessBuilder.setLogLevel(workerLogLevel);
        workerProcessBuilder.setGradleUserHomeDir(gradleUserHomeDir);
        workerProcessBuilder.setConnectTimeoutSeconds(connectTimeoutSeconds);
        return workerProcessBuilder;
    }

    @Override
    public <T> SingleUseWorkerProcessBuilder<T> create(Class<T> protocolType, Class<? extends T> workerImplementation) {
        final DefaultWorkerProcessBuilder builder = newWorker();
        return new DefaultSingleUseWorkerProcessBuilder<T>(protocolType, workerImplementation, builder);
    }

}
