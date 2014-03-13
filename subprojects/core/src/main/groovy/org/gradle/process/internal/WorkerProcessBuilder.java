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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.LogLevel;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>A builder which configures and creates a {@link WorkerProcess} instance.</p>
 *
 * <p>A worker process is specified using an {@link Action}. The given action instance is serialized across into the worker process and executed.</p>
 *
 * <p>A worker process can optionally specify an application classpath. The classes of this classpath are loaded into an isolated ClassLoader, which is made visible to the worker action ClassLoader.
 * Only the packages specified in the set of shared packages are visible to the worker action ClassLoader.</p>
 */
public abstract class WorkerProcessBuilder {
    private final JavaExecHandleBuilder javaCommand;
    private final Set<String> packages = new HashSet<String>();
    private final Set<File> applicationClasspath = new LinkedHashSet<File>();
    private Action<? super WorkerProcessContext> action;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private boolean loadApplicationInSystemClassLoader;
    private String baseName = "Gradle Worker";

    public WorkerProcessBuilder(FileResolver fileResolver) {
        javaCommand = new JavaExecHandleBuilder(fileResolver);
    }

    public WorkerProcessBuilder setBaseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    public String getBaseName() {
        return baseName;
    }

    public WorkerProcessBuilder applicationClasspath(Iterable<File> files) {
        GUtil.addToCollection(applicationClasspath, files);
        return this;
    }

    public Set<File> getApplicationClasspath() {
        return applicationClasspath;
    }

    public WorkerProcessBuilder sharedPackages(String... packages) {
        sharedPackages(Arrays.asList(packages));
        return this;
    }

    public WorkerProcessBuilder sharedPackages(Iterable<String> packages) {
        GUtil.addToCollection(this.packages, packages);
        return this;
    }

    public Set<String> getSharedPackages() {
        return packages;
    }

    public WorkerProcessBuilder worker(Action<? super WorkerProcessContext> action) {
        this.action = action;
        return this;
    }

    public Action<? super WorkerProcessContext> getWorker() {
        return action;
    }

    public JavaExecHandleBuilder getJavaCommand() {
        return javaCommand;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isLoadApplicationInSystemClassLoader() {
        return loadApplicationInSystemClassLoader;
    }

    public void setLoadApplicationInSystemClassLoader(boolean loadApplicationInSystemClassLoader) {
        this.loadApplicationInSystemClassLoader = loadApplicationInSystemClassLoader;
    }

    public abstract WorkerProcess build();
}
