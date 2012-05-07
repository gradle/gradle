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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.streams.StreamsHandler;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleRunner {
    private static final Object START_LOCK = new Object();
    private static final Logger LOGGER = Logging.getLogger(ExecHandleRunner.class);

    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final Lock lock = new ReentrantLock();

    private Process process;
    private boolean aborted;
    private final StreamsHandler streamsHandler;

    public ExecHandleRunner(DefaultExecHandle execHandle, StreamsHandler streamsHandler) {
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
                process.destroy();
            }
        } finally {
            lock.unlock();
        }
    }

    public void start() {
        ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        try {
            Process process;

            // This big fat static lock is here for windows. When starting multiple processes concurrently, the stdout
            // and stderr streams for some of the processes get stuck
            synchronized (START_LOCK) {
                process = processBuilder.start();
                streamsHandler.connectStreams(process, execHandle.getDisplayName());
            }
            setProcess(process);

            streamsHandler.start();
            execHandle.started();
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

    public void waitUntilStreamsHandled() {
        Runnable waitFor = new Runnable() {
            public void run() {
                Integer exitValue;
                try {
                    LOGGER.debug("waiting until streams are handled...");
                    streamsHandler.stop();
                    exitValue = exitValue();
                } catch (Throwable t) {
                    execHandle.failed(t);
                    return;
                }
                if (exitValue != null) {
                    LOGGER.debug("Completed waiting for streams handling. The process already exited with: " + exitValue);
                    completed(exitValue);
                } else {
                    LOGGER.debug("Completed waiting for streams handling. The process still runs.");
                    execHandle.detached();
                }
            }
        };
        new TimeKeepingExecuter().execute(waitFor, abortOnTimeout(), execHandle.getTimeout(), "Handling streams for: " + execHandle.getDisplayName());
    }

    public void waitForFinish() {
        Runnable waitFor = new Runnable() {
            public void run() {
                int exitCode;
                try {
                    LOGGER.debug("waiting until completed {}", execHandle.getDisplayName());
                    exitCode = process.waitFor();
                    LOGGER.debug("waiting until streams are handled...");
                    streamsHandler.stop();
                } catch (Throwable t) {
                    execHandle.failed(t);
                    return;
                }
                completed(exitCode);
            }
        };
        new TimeKeepingExecuter().execute(waitFor, abortOnTimeout(), execHandle.getTimeout(), "Running: " + execHandle.getDisplayName());
    }

    private Integer exitValue() {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            //the process is still running.
            return null;
        }
    }

    private void completed(int exitValue) {
        if (aborted) {
            execHandle.aborted(exitValue);
        } else {
            execHandle.finished(exitValue);
        }
    }

    private Runnable abortOnTimeout() {
        return new Runnable() {
            public void run() {
                abortProcess();
            }
        };
    }
}
