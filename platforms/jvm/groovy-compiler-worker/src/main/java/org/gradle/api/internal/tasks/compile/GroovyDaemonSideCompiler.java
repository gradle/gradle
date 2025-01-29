/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.language.base.internal.compile.Compiler;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Groovy compiler class loaded on the daemon-side, which actually performs compilation.
 */
public class GroovyDaemonSideCompiler implements Compiler<GroovyJavaJointCompileSpec> {

    private final PathToFileResolver fileResolver;
    private final List<File> javaCompilerPlugins;
    private final InternalProblems problemsService;

    @Inject
    public GroovyDaemonSideCompiler(List<File> javaCompilerPlugins, PathToFileResolver fileResolver, InternalProblems problemsService) {
        this.fileResolver = fileResolver;
        this.javaCompilerPlugins = javaCompilerPlugins;
        this.problemsService = problemsService;
    }

    @Override
    public WorkResult execute(GroovyJavaJointCompileSpec spec) {
        Compiler<JavaCompileSpec> javaCompiler = new JdkJavaCompiler(new JavaHomeBasedJavaCompilerFactory(javaCompilerPlugins), problemsService);
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler, fileResolver);
        return groovyCompiler.execute(spec);
    }

}
