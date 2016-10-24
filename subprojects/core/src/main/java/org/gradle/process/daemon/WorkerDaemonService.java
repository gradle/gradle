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

import org.gradle.process.JavaForkOptions;

import java.io.File;

public interface WorkerDaemonService {
    /**
     * Returns a new {@link JavaForkOptions} suitable for use when requesting a daemon.
     *
     * @return a new {@link JavaForkOptions} object
     */
    JavaForkOptions newForkOptions();

    /**
     * Creates a new Runnable object that executes the provided class in a daemon process with the given process requirements.
     * If an idle daemon is available that meets those requirements, it will be used in preference over starting a new daemon.
     *
     * @param forkOptions - The process related options to use when starting the daemon process
     * @param classpath - The classpath to make available in the daemon process
     * @param sharedPackages - The packages to make visible to the provided class in the daemon process
     * @param runnableClass - The class to run in the daemon
     * @param params - Any initialization parameters that should be provided when creating an instance of the provided class
     * @return - A new Runnable that acquires (or starts) a daemon and executes the provided class when run
     */
    Runnable daemonRunnable(JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages, Class<? extends Runnable> runnableClass, Object... params);
}
