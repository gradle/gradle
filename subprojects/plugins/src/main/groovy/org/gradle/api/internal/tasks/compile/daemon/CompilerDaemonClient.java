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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.WorkerProcess;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

class CompilerDaemonClient implements CompilerDaemon, CompilerDaemonClientProtocol, Stoppable {
    private final DaemonForkOptions forkOptions;
    private final WorkerProcess workerProcess;
    private final CompilerDaemonServerProtocol server;
    private final BlockingQueue<CompileResult> compileResults = new SynchronousQueue<CompileResult>();

    public CompilerDaemonClient(DaemonForkOptions forkOptions, WorkerProcess workerProcess, CompilerDaemonServerProtocol server) {
        this.forkOptions = forkOptions;
        this.workerProcess = workerProcess;
        this.server = server;
    }

    public <T extends CompileSpec> CompileResult execute(Compiler<T> compiler, T spec) {
        // currently we just allow a single compilation thread at a time (per compiler daemon)
        // one problem to solve when allowing multiple threads is how to deal with memory requirements specified by compile tasks
        try {
            server.execute(compiler, spec);
            return compileResults.take();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean isCompatibleWith(DaemonForkOptions required) {
        return forkOptions.isCompatibleWith(required);
    }

    public void stop() {
        server.stop();
        workerProcess.waitForStop();
    }

    public void executed(CompileResult result) {
        try {
            compileResults.put(result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
