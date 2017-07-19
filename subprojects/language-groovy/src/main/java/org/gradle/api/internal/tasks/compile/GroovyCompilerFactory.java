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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.DaemonGroovyCompiler;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;
import org.gradle.workers.internal.WorkerFactory;
import org.gradle.workers.internal.WorkerDaemonFactory;

public class GroovyCompilerFactory implements CompilerFactory<GroovyJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final JavaCompilerFactory javaCompilerFactory;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final IsolatedClassloaderWorkerFactory inProcessWorkerFactory;
    private final FileResolver fileResolver;

    public GroovyCompilerFactory(ProjectInternal project, JavaCompilerFactory javaCompilerFactory, WorkerDaemonFactory workerDaemonFactory,
                                 IsolatedClassloaderWorkerFactory inProcessWorkerFactory, FileResolver fileResolver) {
        this.project = project;
        this.javaCompilerFactory = javaCompilerFactory;
        this.workerDaemonFactory = workerDaemonFactory;
        this.inProcessWorkerFactory = inProcessWorkerFactory;
        this.fileResolver = fileResolver;
    }

    @Override
    public Compiler<GroovyJavaJointCompileSpec> newCompiler(GroovyJavaJointCompileSpec spec) {
        GroovyCompileOptions groovyOptions = spec.getGroovyCompileOptions();
        Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(spec.getClass());
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler);
        WorkerFactory workerFactory;
        if (groovyOptions.isFork()) {
            workerFactory = workerDaemonFactory;
        } else {
            workerFactory = inProcessWorkerFactory;
        }
        groovyCompiler = new DaemonGroovyCompiler(project.getServices().get(WorkerDirectoryProvider.class).getIdleWorkingDirectory(), groovyCompiler, project.getServices().get(ClassPathRegistry.class), workerFactory, fileResolver);
        return new NormalizingGroovyCompiler(groovyCompiler);
    }
}
