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
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;

import com.google.common.io.CharStreams;
import org.gradle.internal.os.OperatingSystem;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

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
    private volatile BuildOperationRef associatedBuildOperation;

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

    public void sendSignal(int signal) {
        if (OperatingSystem.current().isWindows()) {
            throw new UnsupportedOperationException("Sending signals is not supported on Windows");
        }
        lock.lock();
        try {
            if (process == null) {
                throw new IllegalStateException("Cannot send signal " + signal + ": the process has not started yet");
            }
            try {
                long pid = getProcessId(process);
                String[] command = {"kill", "-" + signal, String.valueOf(pid)};
                Process kill = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
                int exitCode = kill.waitFor();
                if (exitCode != 0) {
                    String output = CharStreams.toString(new InputStreamReader(kill.getInputStream(), UTF_8)).trim();
                    String message = StringUtils.join(command, " ") + " failed with exit code " + exitCode;
                    throw new RuntimeException(message + (output.isEmpty() ? "" : output));
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to send signal " + signal + " to process", e);
            }
        } finally {
            lock.unlock();
        }
    }

    private static long getProcessId(Process process) throws Exception {
        try {
            // Java 9+: Process.pid()
            return (Long) Process.class.getMethod("pid").invoke(process);
        } catch (NoSuchMethodException e) {
            // Java 8 fallback: UNIXProcess exposes a private 'pid' int field
            java.lang.reflect.Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return ((Number) pidField.get(process)).longValue();
        }
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
                destroyProcessTree();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Destroys the process of this runner and its known (grand)children.
     * Falls back to only destroying the main process if the code runs on Java 8 or lower, which is the Gradle 8 or lower behavior.
     */
    private void destroyProcessTree() {
        if (JavaVersion.current().isJava9Compatible()) {
            destroyDescendants();
        }
        process.destroy();
    }

    private void destroyDescendants() {
        try {
            @SuppressWarnings("unchecked")
            Stream<Object> descendants = (Stream<Object>) Process.class.getMethod("descendants").invoke(process);
            Method destroyMethod = Class.forName("java.lang.ProcessHandle").getMethod("destroy");
            Iterator<Object> it = descendants.iterator();
            while (it.hasNext()) {
                destroyMethod.invoke(it.next());
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to destroy descendants of process: " + execHandle.getDisplayName(), e);
        }
    }

    @Override
    public void run() {
        // Split the `with` operation so that the `associatedBuildOperation` can be discarded when we wait in `process.waitFor()`
        try {
            CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, () -> {
                startProcess();

                execHandle.started();

                LOGGER.debug("waiting until streams are handled...");
                streamsHandler.start();
            });

            if (execHandle.isDaemon()) {
                CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, () -> {
                    streamsHandler.stop();
                    detached();
                });
            } else {
                int exitValue = process.waitFor();
                CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, () -> {
                    streamsHandler.stop();
                    completed(exitValue);
                });
            }
        } catch (Throwable t) {
            CurrentBuildOperationRef.instance().with(this.associatedBuildOperation, () -> {
                execHandle.failed(t);
            });
        }
    }

    /**
     * Remove any context associated with tracking the startup of this process.
     */
    public void removeStartupContext() {
        this.associatedBuildOperation = null;
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
