/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.jvm.toolchain.JavaToolChain;
import org.gradle.jvm.toolchain.internal.JavaCompilerFactory;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;

public class JavaToolChainFactory {
    private final JavaCompilerFactory javaCompilerFactory;
    private final ExecActionFactory execActionFactory;
    private final JvmVersionDetector jvmVersionDetector;

    public JavaToolChainFactory(JavaCompilerFactory javaCompilerFactory, ExecActionFactory execActionFactory, JvmVersionDetector jvmVersionDetector)  {
        this.javaCompilerFactory = javaCompilerFactory;
        this.execActionFactory = execActionFactory;
        this.jvmVersionDetector = jvmVersionDetector;
    }

    public JavaToolChain forCompileOptions(CompileOptions compileOptions) {
        if (compileOptions.isFork()) {
            ForkOptions forkOptions = compileOptions.getForkOptions();
            File javaHome = forkOptions.getJavaHome();
            if (javaHome != null) {
                return new JavaHomeBasedJavaToolChain(javaHome, javaCompilerFactory, execActionFactory, jvmVersionDetector);
            }
        }
        return new CurrentJvmJavaToolChain(javaCompilerFactory, execActionFactory);
    }
}
