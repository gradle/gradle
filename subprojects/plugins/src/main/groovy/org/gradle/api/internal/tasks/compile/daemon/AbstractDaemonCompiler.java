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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;

public abstract class AbstractDaemonCompiler<T extends CompileSpec> implements Compiler<T> {
    private final Compiler<T> delegate;
    private final CompilerDaemonFactory compilerDaemonFactory;
    private final File daemonWorkingDir;

    public AbstractDaemonCompiler(File daemonWorkingDir, Compiler<T> delegate, CompilerDaemonFactory compilerDaemonFactory) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.delegate = delegate;
        this.compilerDaemonFactory = compilerDaemonFactory;
    }

    public Compiler<T> getDelegate() {
        return delegate;
    }

    public WorkResult execute(T spec) {
        DaemonForkOptions daemonForkOptions = toDaemonOptions(spec);
        CompilerDaemon daemon = compilerDaemonFactory.getDaemon(daemonWorkingDir, daemonForkOptions);
        CompileResult result = daemon.execute(delegate, spec);
        if (result.isSuccess()) {
            return result;
        }
        throw UncheckedException.throwAsUncheckedException(result.getException());
    }

    protected abstract DaemonForkOptions toDaemonOptions(T spec);
}
