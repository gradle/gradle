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
package org.gradle.gradleplugin.foundation.runner;

import org.gradle.api.logging.LogLevel;
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.logging.ShowStacktrace;

import java.io.File;

/**
 * This executes a command line in an external process.
 */
public class GradleRunner {
    private File currentDirectory;
    private File gradleHomeDirectory;
    private File customGradleExecutor;
    private ProcessLauncherServer server;

    public GradleRunner(File currentDirectory, File gradleHomeDirectory, File customGradleExecutor) {
        this.currentDirectory = currentDirectory;
        this.gradleHomeDirectory = gradleHomeDirectory;
        this.customGradleExecutor = customGradleExecutor;
    }

    public synchronized void executeCommand(String commandLine, LogLevel logLevel, ShowStacktrace stackTraceLevel,
                                            ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction) {
        //the protocol manages the command line and messaging observers
        ExecuteGradleCommandServerProtocol serverProtocol = new ExecuteGradleCommandServerProtocol(currentDirectory, gradleHomeDirectory, customGradleExecutor, commandLine, logLevel, stackTraceLevel,
                executionInteraction);

        //the server kicks off gradle as an external process and manages the communication with said process
        server = new ProcessLauncherServer(serverProtocol);
        server.addServerObserver(new ProcessLauncherServer.ServerObserver() {
            public void clientExited(int result, String output) {
            }

            public void serverExited() {
                clearServer();
            }
        }, false);

        executionInteraction.reportExecutionStarted();  //go ahead and fire off that the execution has started. Normally, this is done by the request, but we don't have a request in this case.
        server.start();
    }

    /**
     * Call this to stop the gradle process.
     */
    public synchronized void killProcess() {
        if (server != null) {
            server.killProcess();
        }
    }

    private synchronized void clearServer() {
        server = null;
    }
}
