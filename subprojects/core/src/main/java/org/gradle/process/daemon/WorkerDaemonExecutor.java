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

package org.gradle.process.daemon;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.io.Serializable;

/**
 * Worker Daemon Executor.
 *
 * @since 3.3
 */
@Incubating
public interface WorkerDaemonExecutor {
    /**
     * Adds a set of files to the classpath associated with the daemon process.
     *
     * @param files - the files to add to the classpath
     * @return this builder
     */
    WorkerDaemonExecutor classpath(Iterable<File> files);

    /**
     * Executes the provided action against the {@link JavaForkOptions} object associated with this builder.
     *
     * @param forkOptionsAction - An action to configure the {@link JavaForkOptions} for this builder
     * @return this builder
     */
    WorkerDaemonExecutor forkOptions(Action<? super JavaForkOptions> forkOptionsAction);

    /**
     * Returns the {@link JavaForkOptions} object associated with this builder.
     *
     * @return the {@link JavaForkOptions} of this builder
     */
    JavaForkOptions getForkOptions();

    /**
     * Sets any initialization parameters to use when constructing an instance of the implementation class.
     *
     * @param params - the parameters to use during construction
     * @return this builder
     */
    WorkerDaemonExecutor params(Serializable... params);

    /**
     * Synchronously executes the work in a daemon process.  Each call will execute in an idle daemon that meets the requirements set on this builder.  If no
     * idle daemons are available, a new daemon will be started.
     *
     * @throws WorkerDaemonExecutionException when a failure occurs while executing the work.
     */
    void execute() throws WorkerDaemonExecutionException;
}
