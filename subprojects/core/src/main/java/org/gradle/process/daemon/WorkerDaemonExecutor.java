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
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.io.Serializable;

/**
 * Worker Daemon Executor.
 *
 * @param <T> Type of the action
 */
public interface WorkerDaemonExecutor<T> {
    /**
     * Adds a set of files to the classpath associated with the daemon process.
     *
     * @param files - the files to add to the classpath
     * @return this builder
     */
    WorkerDaemonExecutor<T> classpath(Iterable<File> files);

    /**
     * Adds a set of shared packages to the packages exposed to this daemon process.
     *
     * @param packages - the packages to add
     * @return this builder
     */
    WorkerDaemonExecutor<T> sharedPackages(Iterable<String> packages);

    /**
     * Executes the provided action against the {@link JavaForkOptions} object associated with this builder.
     *
     * @param forkOptionsAction - An action to configure the {@link JavaForkOptions} for this builder
     * @return this builder
     */
    WorkerDaemonExecutor<T> forkOptions(Action<JavaForkOptions> forkOptionsAction);

    /**
     * Returns the {@link JavaForkOptions} object associated with this builder.
     *
     * @return the {@link JavaForkOptions} of this builder
     */
    JavaForkOptions getForkOptions();

    /**
     * Sets the implementationClass to use for this builder.
     *
     * @param implementationClass - the implementation class to use
     * @return this builder
     */
    WorkerDaemonExecutor<T> implementationClass(Class<? extends T> implementationClass);

    /**
     * Sets any initialization parameters to use when constructing an instance of the implementation class.
     *
     * @param params - the parameters to use during construction
     * @return this builder
     */
    WorkerDaemonExecutor<T> params(Serializable... params);

    /**
     * Synchronously executes the task in a daemon process.
     */
    void execute();
}
