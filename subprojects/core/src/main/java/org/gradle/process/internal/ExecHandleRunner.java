/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal;

import net.rubygrapefruit.platform.ProcessLauncher;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;

import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExecHandleRunner implements Runnable {
    private static final Logger LOGGER = Logging.getLogger(ExecHandleRunner.class);

    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final Lock lock = new ReentrantLock();
    private final ProcessLauncher processLauncher;
    private final Executor executor;

    private Process process;
    private boolean aborted;
    private final StreamsHandler streamsHandler;
    private BuildOperationRef associatedBuildOperation;

    public ExecHandleRunner(
        DefaultExecHandle execHandle, StreamsHandler streamsHandler, ProcessLauncher processLauncher, Executor executor,
        BuildOperationRef associatedBuildOperation
    ) {
        if (execHandle == null) {
            throw new IllegalArgumentException("execHandle == null!");
        }
        this.execHandle = execHandle;
        this.streamsHandler = streamsHandler;
        this.processLauncher = processLauncher;
        this.executor = executor;
        this.associatedBuildOperation = associatedBuildOperation;
        this.processBuilderFactory = new ProcessBuilderFactory();
    }

    public void abortProcess() {
        lock.lock();
        try {
            if (aborted) {
                return;
            }
            aborted = true;
            if (process != null) {
                streamsHandler.disconnect();
                LOGGER.debug("Abort requested. Destroying process: {}.", execHandle.getDisplayName());
                process.destroy();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        BuildOperationRef original = CurrentBuildOperationRef.instance().get();
        CurrentBuildOperationRef.instance().set(this.associatedBuildOperation);
        try {
            startProcess();

            execHandle.started();

            LOGGER.debug("waiting until streams are handled...");
            streamsHandler.start();

            if (execHandle.isDaemon()) {
                streamsHandler.stop();
                detached();
            } else {
                if (execHandle.isPersistent()) {
                    // Drop the reference to the build operation so that it can be garbage collected
                    // We don't want to retain this information when it won't be relevant anymore
                    CurrentBuildOperationRef.instance().clear();
                    this.associatedBuildOperation = null;
                }
                int exitValue = process.waitFor();
                streamsHandler.stop();
                completed(exitValue);
            }
        } catch (Throwable t) {
            execHandle.failed(t);
        } finally {
            CurrentBuildOperationRef.instance().set(original);
        }
    }

    /**
     * Remove any context associated with tracking the startup of this process.
     */
    public void removeStartupContext() {
        streamsHandler.removeStartupContext();
    }

    private void startProcess() {
        lock.lock();
        try {
            if (aborted) {
                throw new IllegalStateException("Process has already been aborted");
            }
            ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
            Process process = processLauncher.start(processBuilder);
            streamsHandler.connectStreams(process, execHandle.getDisplayName(), executor);
            this.process = process;
        } finally {
            lock.unlock();
        }
    }

    private void completed(int exitValue) {
        if (aborted) {
            execHandle.aborted(exitValue);
        } else {
            execHandle.finished(exitValue);
        }
    }

    private void detached() {
        execHandle.detached();
    }
}
