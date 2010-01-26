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

package org.gradle.util.exec;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleRunner implements Runnable {

    private final ProcessBuilderFactory processBuilderFactory;
    private final DefaultExecHandle execHandle;
    private final AtomicBoolean keepWaiting;
    private final Executor threadPool;

    public ExecHandleRunner(DefaultExecHandle execHandle, ExecutorService threadPool) {
        if (execHandle == null) {
            throw new IllegalArgumentException("execHandle == null!");
        }
        this.processBuilderFactory = new ProcessBuilderFactory();
        this.execHandle = execHandle;
        this.keepWaiting = new AtomicBoolean(true);
        this.threadPool = threadPool;
    }

    public void stopWaiting() {
        keepWaiting.set(false);
    }

    public void run() {
        ProcessBuilder processBuilder = processBuilderFactory.createProcessBuilder(execHandle);
        long keepWaitingTimeout = execHandle.getKeepWaitingTimeout();

        try {
            Process process = processBuilder.start();

            ExecOutputHandleRunner standardOutputRunner = new ExecOutputHandleRunner("read process standard output",
                    process.getInputStream(), execHandle.getStandardOutput());
            ExecOutputHandleRunner errorOutputRunner = new ExecOutputHandleRunner("read process error output",
                    process.getErrorStream(), execHandle.getErrorOutput());
            ExecOutputHandleRunner standardInputRunner = new ExecOutputHandleRunner("write process standard input",
                    execHandle.getStandardInput(), process.getOutputStream());

            threadPool.execute(standardInputRunner);
            threadPool.execute(standardOutputRunner);
            threadPool.execute(errorOutputRunner);

            // signal started after all threads are started otherwise RejectedExecutionException may be thrown
            // by the ExecutorService because shutdown may already be called on it
            // especially when the startAndWaitForFinish method is used on the ExecHandle.
            execHandle.started();

            int exitCode = -1;
            boolean processFinishedNormally = false;
            while (keepWaiting.get() && !processFinishedNormally) {
                try {
                    exitCode = process.exitValue();
                    processFinishedNormally = true;
                } catch (IllegalThreadStateException e) {
                    // ignore
                }
                try {
                    Thread.sleep(keepWaitingTimeout);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            if (!keepWaiting.get()) {
                process.destroy();
                execHandle.aborted();
            } else {
                execHandle.finished(exitCode);
            }
        } catch (Throwable t) {
            execHandle.failed(t);
        }
    }
}
