/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers;

import org.gradle.api.ActionConfiguration;
import org.gradle.api.Describable;

import java.io.File;

/**
 * Represents the configuration of a worker.  Used when submitting an item of work
 * to the {@link WorkerExecutor}.
 *
 * <pre>
 *      workerExecutor.submit(RunnableWorkImpl.class) { WorkerConfiguration conf -&gt;
 *          conf.isolationMode = IsolationMode.PROCESS
 *
 *          forkOptions { JavaForkOptions options -&gt;
 *              options.maxHeapSize = "512m"
 *              options.systemProperty 'some.prop', 'value'
 *              options.jvmArgs "-server"
 *          }
 *
 *          classpath configurations.fooLibrary
 *
 *          conf.params = [ "foo", file('bar') ]
 *      }
 * </pre>
 *
 * @since 3.5
 */
public interface WorkerConfiguration extends ActionConfiguration, ForkingWorkerSpec, Describable {
    /**
     * Gets the isolation mode for this worker, see {@link IsolationMode}.
     *
     * @return the isolation mode for this worker, see {@link IsolationMode}, defaults to {@link IsolationMode#AUTO}
     *
     * @since 4.0
     */
    IsolationMode getIsolationMode();

    /**
     * Sets the isolation mode for this worker, see {@link IsolationMode}.
     *
     * @param isolationMode the forking mode for this worker, see {@link IsolationMode}
     *
     * @since 4.0
     */
    void setIsolationMode(IsolationMode isolationMode);

    /**
     * Adds a set of files to the classpath associated with the worker.
     *
     * @param files - the files to add to the classpath
     */
    void classpath(Iterable<File> files);

    /**
     * Sets the classpath associated with the worker.
     *
     * @param files - the files to set the classpath to
     */
    void setClasspath(Iterable<File> files);

    /**
     * Gets the classpath associated with the worker.
     *
     * @return the classpath associated with the worker
     */
    Iterable<File> getClasspath();

    /**
     * Gets the forking mode for this worker, see {@link ForkMode}.
     *
     * @return the forking mode for this worker, see {@link ForkMode}, defaults to {@link ForkMode#AUTO}
     */
    @Deprecated
    ForkMode getForkMode();

    /**
     * Sets the forking mode for this worker, see {@link ForkMode}.
     *
     * @param forkMode the forking mode for this worker, see {@link ForkMode}
     */
    @Deprecated
    void setForkMode(ForkMode forkMode);

    /**
     * Sets the name to use when displaying this item of work.
     *
     * @param displayName the name of this item of work
     */
    void setDisplayName(String displayName);
}
