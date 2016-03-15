/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.gosu;

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.tasks.gosu.GosuForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Arrays;

public class DaemonGosuCompiler<T extends GosuCompileSpec> extends AbstractDaemonCompiler<T> {
    private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("gw.lang.gosuc", "com.sun.tools.javac");
    private final Iterable<File> gosuCompilerBootstrap;

    public DaemonGosuCompiler(File daemonWorkingDir, Compiler<T> delegate, CompilerDaemonFactory daemonFactory, Iterable<File> gosuCompilerBootstrap) {
        super(daemonWorkingDir, delegate, daemonFactory);
        this.gosuCompilerBootstrap = gosuCompilerBootstrap;
    }

    @Override
    protected DaemonForkOptions toDaemonOptions(T spec) {
        return createGosuForkOptions(spec);
    }

    private DaemonForkOptions createGosuForkOptions(T spec) {
        GosuForkOptions options = spec.getGosuCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(),
            options.getJvmArgs(), gosuCompilerBootstrap, SHARED_PACKAGES);
    }
}
