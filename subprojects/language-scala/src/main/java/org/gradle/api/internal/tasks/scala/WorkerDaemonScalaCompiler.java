/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.ForkOptionsMerger;
import org.gradle.api.internal.tasks.compile.daemon.AbstractWorkerCompiler;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import java.io.File;
import java.util.Arrays;

public class WorkerDaemonScalaCompiler<T extends ScalaJavaJointCompileSpec> extends AbstractWorkerCompiler<T> {
    private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("scala", "com.typesafe.zinc", "xsbti", "com.sun.tools.javac", "sbt");
    private final Iterable<File> zincClasspath;

    public WorkerDaemonScalaCompiler(File daemonWorkingDir, Compiler<T> delegate, WorkerExecutor workerExecutor, Iterable<File> zincClasspath) {
        super(daemonWorkingDir, delegate, workerExecutor, IsolationMode.PROCESS);
        this.zincClasspath = zincClasspath;
    }

    @Override
    protected void applyWorkerConfiguration(T spec, WorkerConfigurationInternal config) {
        final BaseForkOptions forkOptions = new ForkOptionsMerger().merge(spec.getCompileOptions().getForkOptions(), spec.getScalaCompileOptions().getForkOptions());
        config.forkOptions(new Action<JavaForkOptions>() {
            @Override
            public void execute(JavaForkOptions javaForkOptions) {
                javaForkOptions.setJvmArgs(forkOptions.getJvmArgs());
                javaForkOptions.setMinHeapSize(forkOptions.getMemoryInitialSize());
                javaForkOptions.setMaxHeapSize(forkOptions.getMemoryMaximumSize());
            }
        });
        config.setStrictClasspath(true);
        config.setClasspath(zincClasspath);
        config.setSharedPackages(SHARED_PACKAGES);
    }
}

