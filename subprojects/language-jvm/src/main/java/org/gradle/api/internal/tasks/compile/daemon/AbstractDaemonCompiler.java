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
import org.gradle.process.internal.daemon.DaemonForkOptions;
import org.gradle.process.internal.daemon.WorkSpec;
import org.gradle.process.internal.daemon.WorkerDaemonAction;
import org.gradle.process.internal.daemon.WorkerDaemonResult;
import org.gradle.process.internal.daemon.WorkerDaemon;
import org.gradle.process.internal.daemon.WorkerDaemonFactory;
import org.gradle.process.internal.daemon.WorkerDaemonServer;

import java.io.File;

public abstract class AbstractDaemonCompiler<T extends CompileSpec> implements Compiler<T> {
    private final Compiler<T> delegate;
    private final WorkerDaemonFactory compilerDaemonFactory;
    private final File daemonWorkingDir;

    public AbstractDaemonCompiler(File daemonWorkingDir, Compiler<T> delegate, WorkerDaemonFactory compilerDaemonFactory) {
        this.daemonWorkingDir = daemonWorkingDir;
        this.delegate = delegate;
        this.compilerDaemonFactory = compilerDaemonFactory;
    }

    public Compiler<T> getDelegate() {
        return delegate;
    }

    @Override
    public WorkResult execute(T spec) {
        DaemonForkOptions daemonForkOptions = toDaemonOptions(spec);
        WorkerDaemon daemon = compilerDaemonFactory.getDaemon(CompilerDaemonServer.class, daemonWorkingDir, daemonForkOptions);
        WorkerDaemonResult result = daemon.execute(adapter(delegate), spec);
        if (result.isSuccess()) {
            return result;
        }
        throw UncheckedException.throwAsUncheckedException(result.getException());
    }

    private CompilerWorkerAdapter<T> adapter(Compiler<T> compiler) {
        return new CompilerWorkerAdapter<T>(compiler);
    }

    protected abstract DaemonForkOptions toDaemonOptions(T spec);

    private static class CompilerWorkerAdapter<T extends CompileSpec> implements WorkerDaemonAction<T> {
        private final Compiler<T> compiler;

        CompilerWorkerAdapter(Compiler<T> compiler) {
            this.compiler = compiler;
        }

        @Override
        public WorkerDaemonResult execute(T spec) {
            return new WorkerDaemonResult(compiler.execute(spec).getDidWork(), null);
        }

        @Override
        public String getDescription() {
            return compiler.getClass().getName();
        }
    }

    // TODO Come up with a better way to set up the worker implementation classpath
    // This is a hack to get the appropriate classpath on the worker implementation classpath for compiler daemons.
    // The classpath is derived from the implementation class and when this is WorkerDaemonServer, we get the classpath
    // from the classloader in the Gradle Core API classloader scope (which only contains certain jars).  Using this
    // class causes the classpath to be inferred from the Gradle API scope classloader instead so that we get the necessary
    // jars for a compiler daemon.
    public static class CompilerDaemonServer extends WorkerDaemonServer {
        @Override
        public <T extends WorkSpec> WorkerDaemonResult execute(WorkerDaemonAction<T> action, T spec) {
            return super.execute(action, spec);
        }
    }
}
