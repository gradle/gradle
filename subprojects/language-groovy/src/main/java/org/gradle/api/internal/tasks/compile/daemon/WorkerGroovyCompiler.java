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

package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.ForkOptionsMerger;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class WorkerGroovyCompiler extends AbstractWorkerCompiler<GroovyJavaJointCompileSpec> {
    private final static Iterable<String> SHARED_PACKAGES = Arrays.asList("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac");
    private final ClassPathRegistry classPathRegistry;

    public WorkerGroovyCompiler(File daemonWorkingDir, Compiler<GroovyJavaJointCompileSpec> delegate, ClassPathRegistry classPathRegistry, WorkerExecutor workerExecutor, IsolationMode isolationMode) {
        super(daemonWorkingDir, delegate, workerExecutor, isolationMode);
        this.classPathRegistry = classPathRegistry;
    }

    @Override
    protected void applyWorkerConfiguration(GroovyJavaJointCompileSpec spec, WorkerConfigurationInternal config) {
        if(getIsolationMode() == IsolationMode.PROCESS) {
            final BaseForkOptions forkOptions = new ForkOptionsMerger().merge(spec.getCompileOptions().getForkOptions(), spec.getGroovyCompileOptions().getForkOptions());
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
        config.setClasspath(getClasspath(spec));
        config.setSharedPackages(SHARED_PACKAGES);
    }

    private Iterable<File> getClasspath(GroovyJavaJointCompileSpec spec) {
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        GroovyForkOptions options = spec.getGroovyCompileOptions().getForkOptions();
        Collection<File> antFiles = classPathRegistry.getClassPath("ANT").getAsFiles();
        return Iterables.concat(spec.getGroovyClasspath(), antFiles);
    }
}
