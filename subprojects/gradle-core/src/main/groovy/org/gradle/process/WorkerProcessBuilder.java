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
package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.util.GFileUtils;
import org.gradle.util.exec.JavaExecHandleBuilder;

import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * <p>A builder which configures and creates a {@link org.gradle.process.WorkerProcess} instance.</p>
 *
 * <p>A worker process is specified using an {@link Action}. The given action instance is serialized across into the
 * worker process and executed.</p>
 *
 * <p>A worker process can optionally specify an application classpath. The classes of this classpath are loaded into an
 * isolated ClassLoader, which is made visible to the worker action ClassLoader. Only the packages specified in the set
 * of shared packages are visible to the worker action ClassLoader.</p>
 */
public abstract class WorkerProcessBuilder {
    private final JavaExecHandleBuilder javaCommand;
    private final Set<String> packages = new HashSet<String>();
    private final Set<URL> applicationClasspath = new LinkedHashSet<URL>();
    private Action<WorkerProcessContext> action;

    public WorkerProcessBuilder(FileResolver fileResolver) {
        javaCommand = new JavaExecHandleBuilder(fileResolver);
    }

    public WorkerProcessBuilder applicationClasspath(Iterable<File> files) {
        applicationClasspath.addAll(GFileUtils.toURLs(files));
        return this;
    }

    public Set<URL> getApplicationClasspath() {
        return applicationClasspath;
    }

    public WorkerProcessBuilder sharedPackages(String... packages) {
        this.packages.addAll(Arrays.asList(packages));
        return this;
    }

    public Set<String> getSharedPackages() {
        return packages;
    }

    public WorkerProcessBuilder worker(Action<WorkerProcessContext> action) {
        this.action = action;
        return this;
    }

    public Action<WorkerProcessContext> getWorker() {
        return action;
    }

    public JavaExecHandleBuilder getJavaCommand() {
        return javaCommand;
    }

    public abstract WorkerProcess build();
}
