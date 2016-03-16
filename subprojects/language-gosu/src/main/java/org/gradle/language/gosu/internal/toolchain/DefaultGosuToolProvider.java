/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.internal.toolchain;

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.gosu.DaemonGosuCompiler;
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec;
import org.gradle.api.internal.tasks.gosu.NormalizingGosuCompiler;
import org.gradle.api.internal.tasks.gosu.ForkingGosuCompiler;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.platform.base.internal.toolchain.ToolProvider;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.Set;

public class DefaultGosuToolProvider implements ToolProvider {
    private final File gradleUserHomeDir;
    private final File rootProjectDir;
    private final CompilerDaemonManager compilerDaemonManager;
    private final Set<File> resolvedGosuClasspath;
    private final Set<File> resolvedGosuCompilerBootstrap;

    public DefaultGosuToolProvider(File gradleUserHomeDir, File rootProjectDir, CompilerDaemonManager compilerDaemonManager, Set<File> resolvedGosuClasspath, Set<File> resolvedGosuCompilerBootstrap) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.rootProjectDir = rootProjectDir;
        this.compilerDaemonManager = compilerDaemonManager;
        this.resolvedGosuClasspath = resolvedGosuClasspath;
        this.resolvedGosuCompilerBootstrap = resolvedGosuCompilerBootstrap;
    }

    @SuppressWarnings("unchecked")
    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (GosuCompileSpec.class.isAssignableFrom(spec)) {
            Compiler<GosuCompileSpec> gosuCompiler = new ForkingGosuCompiler(resolvedGosuClasspath, gradleUserHomeDir);
            return (Compiler<T>) new NormalizingGosuCompiler(new DaemonGosuCompiler<GosuCompileSpec>(rootProjectDir, gosuCompiler, compilerDaemonManager, resolvedGosuCompilerBootstrap));
        }
        throw new IllegalArgumentException(String.format("Cannot create Compiler for unsupported CompileSpec type '%s'", spec.getSimpleName()));
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {

    }
}
