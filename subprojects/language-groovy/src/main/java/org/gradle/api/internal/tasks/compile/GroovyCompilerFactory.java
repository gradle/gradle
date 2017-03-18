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

import org.gradle.api.Project;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.daemon.WorkerGroovyCompiler;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;

public class GroovyCompilerFactory implements CompilerFactory<GroovyJavaJointCompileSpec> {
    private final Project project;
    private final JavaCompilerFactory javaCompilerFactory;
    private final ClassPathRegistry classPathRegistry;
    private final WorkerExecutor workerExecutor;

    public GroovyCompilerFactory(Project project, JavaCompilerFactory javaCompilerFactory, ClassPathRegistry classPathRegistry, WorkerExecutor workerExecutor) {
        this.project = project;
        this.javaCompilerFactory = javaCompilerFactory;
        this.classPathRegistry = classPathRegistry;
        this.workerExecutor = workerExecutor;
    }

    @Override
    public Compiler<GroovyJavaJointCompileSpec> newCompiler(GroovyJavaJointCompileSpec spec) {
        GroovyCompileOptions groovyOptions = spec.getGroovyCompileOptions();
        Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(spec.getClass());
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler);
        IsolationMode isolationMode = groovyOptions.isFork() ? IsolationMode.PROCESS : IsolationMode.CLASSLOADER;
        groovyCompiler = new WorkerGroovyCompiler(project.getRootProject().getProjectDir(), groovyCompiler, classPathRegistry, workerExecutor, isolationMode);
        return new NormalizingGroovyCompiler(groovyCompiler);
    }
}
