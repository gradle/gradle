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

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Collections;

public class DaemonJavaCompiler extends AbstractDaemonCompiler<JavaCompileSpec> {
    public DaemonJavaCompiler(File daemonWorkingDir, Compiler<JavaCompileSpec> delegate, CompilerDaemonFactory compilerDaemonFactory) {
        super(daemonWorkingDir, delegate, compilerDaemonFactory);
    }

    @Override
    protected DaemonForkOptions toDaemonOptions(JavaCompileSpec spec) {
        ForkOptions forkOptions = spec.getCompileOptions().getForkOptions();
        return new DaemonForkOptions(
                forkOptions.getMemoryInitialSize(), forkOptions.getMemoryMaximumSize(), forkOptions.getJvmArgs(),
                Collections.<File>emptyList(), Collections.singleton("com.sun.tools.javac"));
    }
}
