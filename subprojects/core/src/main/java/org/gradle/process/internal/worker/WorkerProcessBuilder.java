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
    WorkerProcessBuilder applicationModulePath(Iterable<File> files);

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

    void setImplementationModulePath(List<URL> implementationModulePath);

    void enableJvmMemoryInfoPublishing(boolean shouldPublish);

    /**
     * Specify whether the {@code --add-opens} command line args specified by
     * {@link org.gradle.internal.jvm.JpmsConfiguration#GRADLE_WORKER_JPMS_ARGS}
     * should be used for the process-to-build.
     * <p>
     * Note: This option will be removed in Gradle 8.0 so that no workers use
     * these args by default. We are leaving these args enabled by default for
     * most worker types in order to reduce breaking changes for those using
     * reflection already in their workers already. In 8.0, we will remove these
     * args entirely from all workers.
     *
     * @see <a href="https://github.com/gradle/gradle/issues/21013">8.0 Removal Issue</a>
     *
     * @param useLegacyAddOpens Whether to use the add-opens args.
     */
    @Deprecated
    WorkerProcessBuilder setUseLegacyAddOpens(boolean useLegacyAddOpens);

    /**
     * Creates the worker process. The process is not started until {@link WorkerProcess#start()} is called.
     *
     * <p>This method can be called multiple times, to create multiple worker processes.</p>
     */
    WorkerProcess build();
}
