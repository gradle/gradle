/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.daemon.AbstractWorkerCompiler;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import java.io.File;
import java.util.Collections;

public class WorkerJavaCompiler extends AbstractWorkerCompiler<JavaCompileSpec> {
    private static final Iterable<String> SHARED_PACKAGES = Collections.singleton("com.sun.tools.javac");

    public WorkerJavaCompiler(File daemonWorkingDir, Compiler<JavaCompileSpec> delegate, WorkerExecutor workerExecutor, IsolationMode isolationMode) {
        super(daemonWorkingDir, delegate, workerExecutor, isolationMode);
    }

    @Override
    protected void applyWorkerConfiguration(JavaCompileSpec spec, WorkerConfigurationInternal config) {
        if (getIsolationMode() == IsolationMode.PROCESS) {
            final ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
            config.forkOptions(new Action<JavaForkOptions>() {
                @Override
                public void execute(JavaForkOptions javaForkOptions) {
                    javaForkOptions.setJvmArgs(forkOptions.getJvmArgs());
                    javaForkOptions.setMinHeapSize(forkOptions.getMemoryInitialSize());
                    javaForkOptions.setMaxHeapSize(forkOptions.getMemoryMaximumSize());
                }
            });
        }
        config.setStrictClasspath(true);
        config.setSharedPackages(SHARED_PACKAGES);
    }
}
