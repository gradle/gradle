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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.DefaultWorkResult;
import org.gradle.workers.internal.KeepAliveMode;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;

public abstract class AbstractWorkerCompiler<T extends CompileSpec> implements Compiler<T> {
    private final File executionWorkingDir;
    private final Compiler<T> delegate;
    private final WorkerExecutor workerExecutor;
    private final IsolationMode isolationMode;

    public AbstractWorkerCompiler(File executionWorkingDir, Compiler<T> delegate, WorkerExecutor workerExecutor, IsolationMode isolationMode) {
        Preconditions.checkArgument(delegate instanceof Serializable, "Delegate compiler must be Serializable");
        this.executionWorkingDir = executionWorkingDir;
        this.delegate = delegate;
        this.workerExecutor = workerExecutor;
        this.isolationMode = isolationMode;
    }

    protected abstract void applyWorkerConfiguration(T spec, WorkerConfigurationInternal config);

    @Override
    public WorkResult execute(final T spec) {
        workerExecutor.submit(CompilerWorkerRunnable.class, new Action<WorkerConfiguration>() {
            @Override
            public void execute(WorkerConfiguration config) {
                config.setIsolationMode(isolationMode);
                config.setParams(delegate, spec);
                if (isolationMode == IsolationMode.PROCESS) {
                    config.getForkOptions().setWorkingDir(executionWorkingDir);
                }
                WorkerConfigurationInternal configInternal = (WorkerConfigurationInternal) config;
                configInternal.setKeepAliveMode(KeepAliveMode.SESSION); // TODO:pm See if some could use DAEMON
                applyWorkerConfiguration(spec, configInternal);
            }
        });
        try {
            workerExecutor.await();
            return new DefaultWorkResult(true, null);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    @VisibleForTesting
    public Compiler<T> getDelegate() {
        return delegate;
    }

    @VisibleForTesting
    public IsolationMode getIsolationMode() {
        return isolationMode;
    }

    private static class CompilerWorkerRunnable<T extends CompileSpec> implements Runnable {
        private final Compiler<T> compiler;
        private final T compileSpec;

        @Inject
        public CompilerWorkerRunnable(Compiler<T> compiler, T compileSpec) {
            this.compiler = compiler;
            this.compileSpec = compileSpec;
        }

        @Override
        public void run() {
            compiler.execute(compileSpec);
        }
    }
}
