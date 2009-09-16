/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.gradleplugin.foundation.request;

import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.queue.ExecutionQueue;

/**
  This represents a basic reques to gradle that is executed in a separate process
 using the ProcessLauncherServer. This stores the command line to execute and
 has the ability to cancel itself by either removing it from the queue if it
 hasn't started yet, or killing the external process.
    @author mhunsicker
*/
public abstract class AbstractRequest implements Request {
    private String fullCommandLine;
    private ExecutionQueue executionQueue;
    private ProcessLauncherServer server;

    public AbstractRequest(String fullCommandLine, ExecutionQueue executionQueue) {
        this.fullCommandLine = fullCommandLine;
        this.executionQueue = executionQueue;
    }

    public String getFullCommandLine() {
        return fullCommandLine;
    }

    /**
     * Cancels this request.
     *
     * @return true if you can cancel or it or if it has already ran. This return
     *         code is mainly meant to prevent you from
     */
    public synchronized boolean cancel() {
        if (this.server != null)
            server.killProcess();

        executionQueue.removeRequestFromQueue(this);
        return true;
    }

    public synchronized void setProcessLauncherServer(ProcessLauncherServer server) {
        this.server = server;
    }
}