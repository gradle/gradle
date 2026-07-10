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

import org.gradle.api.logging.LogLevel;
import org.gradle.process.internal.JavaExecHandleBuilder;

import java.io.File;
import java.util.Set;

/**
 * <p>Settings common to all worker processes.</p>
 *
 * <p>A worker process runs some action in a child process launched by this processes.</p>
 *
 * <p>A worker process can optionally specify an application classpath. The classes of this classpath are loaded into an isolated ClassLoader, which is made visible to the worker action ClassLoader.
 * Only the packages specified in the set of shared packages are visible to the worker action ClassLoader.</p>
 */
public interface WorkerProcessSettings {
    WorkerProcessSettings setBaseName(String baseName);

    String getBaseName();

    WorkerProcessSettings applicationClasspath(Iterable<File> files);

    Set<File> getApplicationClasspath();

    WorkerProcessSettings applicationModulePath(Iterable<File> files);

    Set<File> getApplicationModulePath();

    WorkerProcessSettings sharedPackages(String... packages);

    WorkerProcessSettings sharedPackages(Iterable<String> packages);

    /**
     * The packages which are allowed to leak from the application classpath into the implementation classpath.
     * These packages affect both classes and resources.
     * Subpackages of the provided packages are also shared with the implementation classpath.
     *
     * @return The list of packages which are shared from the application to the implementation classpath.
     */
    Set<String> getSharedPackages();

    JavaExecHandleBuilder getJavaCommand();

    LogLevel getLogLevel();

    WorkerProcessSettings setLogLevel(LogLevel logLevel);
}
