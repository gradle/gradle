/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.runtime.jvm.internal.toolchain.JavaToolChainInternal;

public class DefaultJavaToolChain implements JavaToolChainInternal {
    private final GradleInternal gradle;
    private final CompilerDaemonFactory compilerDaemonFactory;

    public DefaultJavaToolChain(GradleInternal gradle, CompilerDaemonFactory compilerDaemonFactory) {
        this.gradle = gradle;
        this.compilerDaemonFactory = compilerDaemonFactory;
    }

    public <T extends CompileSpec> Compiler<T> newCompiler(Class<T> specType) {
        if (!specType.equals(JavaCompileSpec.class)) {
            throw new IllegalArgumentException(String.format("Don't know how to compile using spec of type %s.", specType.getSimpleName()));
        }

        JavaCompilerFactory defaultCompilerFactory = new DefaultJavaCompilerFactory(gradle.getRootProject().getProjectDir(), compilerDaemonFactory);
        return (Compiler) new DelegatingJavaCompiler(defaultCompilerFactory);
    }
}
