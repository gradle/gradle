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
import org.gradle.api.logging.LogLevel;

import java.io.File;
import java.util.Set;

/**
 * <p>A builder which configures and creates a {@link WorkerProcess} instance.</p>
 *
 * <p>A worker process is specified using an {@link Action}. The given action instance is serialized across into the worker process and executed.
 * The worker action is supplied with a {@link WorkerProcessContext} which it can use to receive messages from and send messages to the server process (ie this process).
 * </p>
 *
 * <p>The server process (ie this process) can send messages to and receive message from the worker process using the methods on {@link WorkerProcess#getConnection()}.</p>
 *
 * <p>A worker process can optionally specify an application classpath. The classes of this classpath are loaded into an isolated ClassLoader, which is made visible to the worker action ClassLoader.
 * Only the packages specified in the set of shared packages are visible to the worker action ClassLoader.</p>
 */
public interface WorkerProcessBuilder {
    WorkerProcessBuilder setBaseName(String baseName);

    String getBaseName();

    WorkerProcessBuilder applicationClasspath(Iterable<File> files);

    Set<File> getApplicationClasspath();

    WorkerProcessBuilder sharedPackages(String... packages);

    WorkerProcessBuilder sharedPackages(Iterable<String> packages);

    Set<String> getSharedPackages();

    WorkerProcessBuilder worker(Action<? super WorkerProcessContext> action);

    Action<? super WorkerProcessContext> getWorker();

    JavaExecHandleBuilder getJavaCommand();

    LogLevel getLogLevel();

    void setLogLevel(LogLevel logLevel);

    WorkerProcess build();
}
