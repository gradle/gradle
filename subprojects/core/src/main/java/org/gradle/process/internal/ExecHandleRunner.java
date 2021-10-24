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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

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

    public ExecHandleRunner(DefaultExecHandle execHandle, StreamsHandler streamsHandler, ProcessLauncher processLauncher, Executor executor) {
        this.processLauncher = processLauncher;
        this.executor = executor;
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
            if (aborted) {
                return;
            }
            aborted = true;
            if (process != null) {
                streamsHandler.disconnect();
                LOGGER.info("Abort requested. Destroying process: {}.", execHandle.getDisplayName());
                if (!OperatingSystem.current().isWindows()) {
                    destroyProcessTree();
                } else {
                    Long pid = getProcessPid();
                    if (pid == null) {
                        LOGGER.info("Aborting {}", execHandle.getDisplayName());
                        process.destroy();
                    } else {
                        // taskkill requires pid
                        destroyWindowsProcessTree(pid);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private @Nullable Long getProcessPid() {
        long pid;
        try {
            // Java 9+
            Method pidMethod = Process.class.getMethod("pid");
            pid = (Long) pidMethod.invoke(process);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LOGGER.debug("Process#pid() is not available, so will try deduce process id from RuntimeMXBean.name", e);
            String runtimeMXBeanName = ManagementFactory.getRuntimeMXBean().getName();
            try {
                pid = Long.parseLong(StringUtils.substringBefore(runtimeMXBeanName, "@"));
            } catch (NumberFormatException nfe) {
                LOGGER.info("Native-platform process: failed to parse PID from Runtime MX bean name: {} " +
                    " (expecting pid@.. format) when terminating {}", runtimeMXBeanName, execHandle.getDisplayName());
                return null;
            }
        }
        return pid;
    }

    /**
     * By default, {@link Process#destroy()} does not terminate children processes in Windows,
     * so it causes non-terminated processes when user cancels the build (e.g. via Ctrl+C).
     * {@code taskkill} utility allows to terminate all the processes in the tree.
     */
    private void destroyWindowsProcessTree(long pid) {
        // TODO: is this ok? Should it use ExecActionFactory somehow?
        ProcessBuilder taskkillProcessBuilder = new ProcessBuilder("cmd", "/K", "taskkill", "/PID", Long.toString(pid), "/T", "/F");
        Process taskkill = processLauncher.start(taskkillProcessBuilder);
        streamsHandler.connectStreams(taskkill, "terminate " + execHandle.getDisplayName() + " via taskkill " + pid, executor);
        try {
            // taskkill should be fast, however, do not allow it to hang Gradle
            taskkill.waitFor(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.info("taskkill took more than 15 seconds to terminate {}", execHandle.getDisplayName());
            process.destroy();
        }
    }

    /**
     * By default, {@link Process#destroy()} does not terminate children processes in Windows,
     * so it causes non-terminated processes when user cancels the build (e.g. via Ctrl+C).
     * The method destroys all descendants first, and then destroys the process itself.
     */
    @SuppressWarnings("unchecked")
    private void destroyProcessTree() {
        // Destroy descendants when running with Java 9+
        // TODO: remove reflection when code is Java 9+
        @SuppressWarnings("rawtypes")
        Stream/*<ProcessHandle>*/ descendantsProcesses = Stream.empty();

        try {
            Method descendantsMethod = Process.class.getMethod("descendants");
            //noinspection rawtypes
            descendantsProcesses = (Stream) descendantsMethod.invoke(process);
        } catch (NoSuchMethodException ignore) {
            LOGGER.info("Can't call terminate process tree for {} since Process#descendants method does not exist. Running with Java 8?", execHandle.getDisplayName());
        } catch (InvocationTargetException e) {
            LOGGER.info("Can't query descendants process tree for {}", execHandle.getDisplayName(), e.getCause());
        } catch (IllegalAccessException e) {
            LOGGER.info("Can't query descendants process tree for {}", execHandle.getDisplayName(), e);
        }

        //noinspection unchecked
        descendantsProcesses.forEach(processHandle -> { // ProcessHandle is Java 9+
            Class<?> processHandleClass = processHandle.getClass();
            Long childPid;
            try {
                Method pidMethod = processHandleClass.getMethod("pid");
                childPid = (Long) pidMethod.invoke(processHandle);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
                return;
            }

            try {
                Method destroyMethod = processHandleClass.getMethod("destroy");
                LOGGER.info("Destroying process {}", childPid);
                destroyMethod.invoke(processHandle);
            } catch (NoSuchMethodException ignore) {
                // can't happen
            } catch (InvocationTargetException e) {
                LOGGER.info("Error while destroying process {}", childPid, e.getCause());
            } catch (IllegalAccessException e) {
                LOGGER.info("Error while destroying process {}", childPid, e);
            }
        });

        LOGGER.info("Aborting {}", execHandle.getDisplayName());
        process.destroy();
    }

    @Override
    public void run() {
        try {
            startProcess();

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
