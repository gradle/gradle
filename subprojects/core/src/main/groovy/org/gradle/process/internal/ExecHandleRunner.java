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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.process.internal.streams.StreamsHandler;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExecHandleRunner implements Runnable {
    private static final Logger LOGGER = Logging.getLogger(ExecHandleRunner.class);

    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final Lock lock = new ReentrantLock();
    private final ProcessLauncher processLauncher;
    private final ExecutorFactory executorFactory;

    private Process process;
    private boolean aborted;
    private final StreamsHandler streamsHandler;

    public ExecHandleRunner(DefaultExecHandle execHandle, StreamsHandler streamsHandler, ProcessLauncher processLauncher, ExecutorFactory executorFactory) {
        this.processLauncher = processLauncher;
        this.executorFactory = executorFactory;
        if (execHandle == null) {
            throw new IllegalArgumentException("execHandle == null!");
        }
        this.streamsHandler = streamsHandler;
        this.processBuilderFactory = new ProcessBuilderFactory();
        this.execHandle = execHandle;
    }

    public void abortProcess() {
        lock.lock();
        try {
            aborted = true;
            if (process != null) {
                LOGGER.debug("Abort requested. Destroying process: {}.", execHandle.getDisplayName());
                process.destroy();
            }
        } finally {
            lock.unlock();
        }
    }

    public void run() {
        try {
            ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
            Process process = processLauncher.start(processBuilder);
            streamsHandler.connectStreams(process, execHandle.getDisplayName(), executorFactory);
            setProcess(process);

            execHandle.started();

            LOGGER.debug("waiting until streams are handled...");
            streamsHandler.start();

            if (execHandle.isDaemon()) {
                streamsHandler.stop();
                detached();
            } else {
                int exitValue = process.waitFor();
                streamsHandler.stop();
                completed(exitValue);
            }
        } catch (Throwable t) {
            execHandle.failed(t);
        }
    }

    private void setProcess(Process process) {
        lock.lock();
        try {
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
