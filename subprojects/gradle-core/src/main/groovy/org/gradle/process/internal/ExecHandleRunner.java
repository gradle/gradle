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

import org.gradle.util.AutoFlushOutputStream;

import java.util.concurrent.Executor;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleRunner implements Runnable {
    private static final Object START_LOCK = new Object();
    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final Executor threadPool;
    private final Object lock;
    private Process process;
    private boolean aborted;

    public ExecHandleRunner(DefaultExecHandle execHandle, Executor threadPool) {
        if (execHandle == null) {
            throw new IllegalArgumentException("execHandle == null!");
        }
        this.processBuilderFactory = new ProcessBuilderFactory();
        this.execHandle = execHandle;
        this.lock = new Object();
        this.threadPool = threadPool;
    }

    public void stopWaiting() {
        synchronized (lock) {
            aborted = true;
            if (process != null) {
                process.destroy();
            }
        }
    }

    public void run() {
        ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        int exitCode;
        try {
            ExecOutputHandleRunner standardOutputRunner;
            ExecOutputHandleRunner errorOutputRunner;
            ExecOutputHandleRunner standardInputRunner;
            Process process;

            // This big fat static lock is here for windows. When starting multiple processes concurrently, the stdout
            // and stderr streams for some of the processes get stuck
            synchronized (START_LOCK) {
                process = processBuilder.start();

                standardOutputRunner = new ExecOutputHandleRunner("read process standard output",
                        process.getInputStream(), execHandle.getStandardOutput());
                errorOutputRunner = new ExecOutputHandleRunner("read process error output", process.getErrorStream(),
                        execHandle.getErrorOutput());
                standardInputRunner = new ExecOutputHandleRunner("write process standard input",
                        execHandle.getStandardInput(), new AutoFlushOutputStream(process.getOutputStream()));
            }
            synchronized (lock) {
                this.process = process;
            }

            threadPool.execute(standardInputRunner);
            threadPool.execute(standardOutputRunner);
            threadPool.execute(errorOutputRunner);

            execHandle.started();

            exitCode = process.waitFor();
        } catch (Throwable t) {
            execHandle.failed(t);
            return;
        }

        if (aborted) {
            execHandle.aborted(exitCode);
        } else {
            execHandle.finished(exitCode);
        }
    }
}
