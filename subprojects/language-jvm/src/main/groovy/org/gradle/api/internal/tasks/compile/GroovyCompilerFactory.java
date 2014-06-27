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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.DaemonGroovyCompiler;
import org.gradle.api.internal.tasks.compile.daemon.InProcessCompilerDaemonFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompileOptions;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerFactory;

public class GroovyCompilerFactory implements CompilerFactory<GroovyJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final JavaCompilerFactory javaCompilerFactory;
    private final CompilerDaemonManager compilerDaemonFactory;
    private final InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory;

    public GroovyCompilerFactory(ProjectInternal project, JavaCompilerFactory javaCompilerFactory, CompilerDaemonManager compilerDaemonManager,
                                 InProcessCompilerDaemonFactory inProcessCompilerDaemonFactory) {
        this.project = project;
        this.javaCompilerFactory = javaCompilerFactory;
        this.compilerDaemonFactory = compilerDaemonManager;
        this.inProcessCompilerDaemonFactory = inProcessCompilerDaemonFactory;
    }

    public Compiler<GroovyJavaJointCompileSpec> newCompiler(GroovyJavaJointCompileSpec spec) {
        CompileOptions javaOptions = spec.getCompileOptions();
        GroovyCompileOptions groovyOptions = spec.getGroovyCompileOptions();
        Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.createForJointCompilation(javaOptions);
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler);
        CompilerDaemonFactory daemonFactory;
        if (groovyOptions.isFork()) {
            daemonFactory = compilerDaemonFactory;
        } else {
            daemonFactory = inProcessCompilerDaemonFactory;
        }
        groovyCompiler = new DaemonGroovyCompiler(project.getRootProject().getProjectDir(), groovyCompiler, project.getServices().get(ClassPathRegistry.class), daemonFactory);
        return new NormalizingGroovyCompiler(groovyCompiler);
    }
}
