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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.workers.internal.WorkerDaemonFactory;
import org.gradle.internal.Factory;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

import javax.tools.JavaCompiler;
import java.io.File;

public class DefaultJavaCompilerFactory implements JavaCompilerFactory {
    private final File daemonWorkingDir;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory;
    private final FileResolver fileResolver;

    public DefaultJavaCompilerFactory(File daemonWorkingDir, WorkerDaemonFactory workerDaemonFactory, Factory<JavaCompiler> javaHomeBasedJavaCompilerFactory, FileResolver fileResolver) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.workerDaemonFactory = workerDaemonFactory;
        this.javaHomeBasedJavaCompilerFactory = javaHomeBasedJavaCompilerFactory;
        this.fileResolver = fileResolver;
    }

    @Override
    public Compiler<JavaCompileSpec> createForJointCompilation(Class<? extends CompileSpec> type) {
        return createTargetCompiler(type, true);
    }

    @Override
    public Compiler<JavaCompileSpec> create(Class<? extends CompileSpec> type) {
        Compiler<JavaCompileSpec> result = createTargetCompiler(type, false);
        return new NormalizingJavaCompiler(result);
    }

    private Compiler<JavaCompileSpec> createTargetCompiler(Class<? extends CompileSpec> type, boolean jointCompilation) {
        if (!JavaCompileSpec.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(String.format("Cannot create a compiler for a spec with type %s", type.getSimpleName()));
        }

        if (CommandLineJavaCompileSpec.class.isAssignableFrom(type)) {
            return new CommandLineJavaCompiler();
        }

        Compiler<JavaCompileSpec> compiler = new JdkJavaCompiler(javaHomeBasedJavaCompilerFactory);
        if (ForkingJavaCompileSpec.class.isAssignableFrom(type) && !jointCompilation) {
            return new DaemonJavaCompiler(daemonWorkingDir, compiler, workerDaemonFactory, fileResolver);
        }

        return compiler;
    }
}
