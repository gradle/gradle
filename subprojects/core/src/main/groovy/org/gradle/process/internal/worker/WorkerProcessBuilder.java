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
import org.gradle.api.logging.LogLevel;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * <p>A builder which configures and creates worker processes.</p>
 *
 * <p>A worker process runs an {@link Action} instance. The given action instance is serialized across into the worker process and executed.
 * The worker action is supplied with a {@link WorkerProcessContext} which it can use to receive messages from and send messages to the server process (ie this process).
 * </p>
 *
 * <p>The server process (ie this process) can send messages to and receive message from the worker process using the methods on {@link WorkerProcess#getConnection()}.</p>
 */
public interface WorkerProcessBuilder extends WorkerProcessSettings {
    @Override
    WorkerProcessBuilder applicationClasspath(Iterable<File> files);

    @Override
    WorkerProcessBuilder setBaseName(String baseName);

    @Override
    WorkerProcessBuilder setLogLevel(LogLevel logLevel);

    @Override
    WorkerProcessBuilder sharedPackages(Iterable<String> packages);

    @Override
    WorkerProcessBuilder sharedPackages(String... packages);

    Action<? super WorkerProcessContext> getWorker();

    void setImplementationClasspath(List<URL> implementationClasspath);

    /**
     * Creates the worker process. The process is not started until {@link WorkerProcess#start()} is called.
     *
     * <p>This method can be called multiple times, to create multiple worker processes.</p>
     */
    WorkerProcess build();
}
