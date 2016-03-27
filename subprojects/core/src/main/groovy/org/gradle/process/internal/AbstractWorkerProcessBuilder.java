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

package org.gradle.process.internal;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractWorkerProcessBuilder implements WorkerProcessBuilder {
    private final JavaExecHandleBuilder javaCommand;
    private final Set<String> packages = new HashSet<String>();
    private final Set<File> applicationClasspath = new LinkedHashSet<File>();
    private Action<? super WorkerProcessContext> action;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private String baseName = "Gradle Worker";
    private File gradleUserHomeDir;

    public AbstractWorkerProcessBuilder(JavaExecHandleBuilder javaCommand) {
        this.javaCommand = javaCommand;
    }

    public AbstractWorkerProcessBuilder setBaseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    public String getBaseName() {
        return baseName;
    }

    public AbstractWorkerProcessBuilder applicationClasspath(Iterable<File> files) {
        GUtil.addToCollection(applicationClasspath, files);
        return this;
    }

    public Set<File> getApplicationClasspath() {
        return applicationClasspath;
    }

    public AbstractWorkerProcessBuilder sharedPackages(String... packages) {
        sharedPackages(Arrays.asList(packages));
        return this;
    }

    public AbstractWorkerProcessBuilder sharedPackages(Iterable<String> packages) {
        GUtil.addToCollection(this.packages, packages);
        return this;
    }

    public Set<String> getSharedPackages() {
        return packages;
    }

    public AbstractWorkerProcessBuilder worker(Action<? super WorkerProcessContext> action) {
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

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    public abstract WorkerProcess build();
}
