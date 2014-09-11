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

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;

public class DefaultJavaCompilerFactory implements JavaCompilerFactory {
    private final File daemonWorkingDir;
    private final CompilerDaemonFactory compilerDaemonFactory;

    public DefaultJavaCompilerFactory(File daemonWorkingDir, CompilerDaemonFactory compilerDaemonFactory) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.compilerDaemonFactory = compilerDaemonFactory;
    }

    public Compiler<JavaCompileSpec> createForJointCompilation(CompileOptions options) {
        return createTargetCompiler(options, true);
    }

    public Compiler<JavaCompileSpec> create(CompileOptions options) {
        Compiler<JavaCompileSpec> result = createTargetCompiler(options, false);
        return new NormalizingJavaCompiler(result);
    }

    private Compiler<JavaCompileSpec> createTargetCompiler(CompileOptions options, boolean jointCompilation) {
        if (options.isFork() && options.getForkOptions().getExecutable() != null) {
            return new CommandLineJavaCompiler();
        }

        Compiler<JavaCompileSpec> compiler = new JdkJavaCompiler();
        if (options.isFork() && !jointCompilation) {
            return new DaemonJavaCompiler(daemonWorkingDir, compiler, compilerDaemonFactory);
        }

        return compiler;
    }
}
