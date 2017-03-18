/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.toolchain;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.daemon.AbstractWorkerCompiler;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.spec.PlayCompileSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import java.io.File;

public class WorkerDaemonPlayCompiler<T extends PlayCompileSpec> extends AbstractWorkerCompiler<T> {
    private final Iterable<File> compilerClasspath;
    private final Iterable<String> classLoaderPackages;

    public WorkerDaemonPlayCompiler(File daemonWorkingDir, Compiler<T> compiler, WorkerExecutor workerExecutor, Iterable<File> compilerClasspath, Iterable<String> classLoaderPackages) {
        super(daemonWorkingDir, compiler, workerExecutor, IsolationMode.PROCESS);
        this.compilerClasspath = compilerClasspath;
        this.classLoaderPackages = classLoaderPackages;
    }

    @Override
    protected void applyWorkerConfiguration(T spec, WorkerConfigurationInternal config) {
        final BaseForkOptions forkOptions = spec.getForkOptions();
        config.forkOptions(new Action<JavaForkOptions>() {
            @Override
            public void execute(JavaForkOptions javaForkOptions) {
                javaForkOptions.setJvmArgs(forkOptions.getJvmArgs());
                javaForkOptions.setMinHeapSize(forkOptions.getMemoryInitialSize());
                javaForkOptions.setMaxHeapSize(forkOptions.getMemoryMaximumSize());
            }
        });
        config.setStrictClasspath(true);
        config.setClasspath(compilerClasspath);
        config.setSharedPackages(classLoaderPackages);
    }
}
