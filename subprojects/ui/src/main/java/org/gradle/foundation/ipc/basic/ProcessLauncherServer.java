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

package org.gradle.foundation.ipc.basic;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.foundation.common.ObserverLord;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;

import java.io.ByteArrayOutputStream;

/**
 * This launches an application as a separate process then listens for messages from it. You implement the Protocol interface to handle the specifics of the communications. To use this, instantiate
 * it, then call start. When the communications are finished, call requestShutdown(). Your server's protocol can call sendMessage once communication is started to respond to client's messages.
 */
public class ProcessLauncherServer extends Server<ProcessLauncherServer.Protocol, ProcessLauncherServer.ServerObserver> {
    private volatile ExecHandle externalProcess;

    private static final Logger LOGGER = Logging.getLogger(ProcessLauncherServer.class);

    /**
     * Implement this to define the behavior of the communication on the server side.
     */
    public interface Protocol extends Server.Protocol<ProcessLauncherServer> {
        public void aboutToKillProcess();

        /**
         * Fill in the ExecutionInfo object with information needed to execute the other process.
         *
         * @param serverPort the port the server is listening on. The client should send messages here
         * @return an executionInfo object containing information about what we execute.
         */
        public ExecutionInfo getExecutionInfo(int serverPort);

        /**
         * Notification that the client has shutdown. Note: this can occur before communications has ever started. You SHOULD get this notification before receiving serverExited, even if the client
         * fails to launch or locks up.
         *
         * @param result the return code of the client application
         * @param output the standard error and standard output of the client application
         */
        public void clientExited(int result, String output);
    }

    public interface ServerObserver extends Server.ServerObserver {
        /**
         * Notification that the client has shutdown. Note: this can occur before communications has ever started. You SHOULD get this notification before receiving serverExited, even if the client
         * fails to launch or locks up.
         *
         * @param result the return code of the client application
         * @param output the standard error and standard output of the client application
         */
        public void clientExited(int result, String output);
    }

    public ProcessLauncherServer(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected void communicationsStarted() {
        launchExternalProcess();
    }

    /**
     * This launches an external process in a thread and waits for it to exit.
     */
    private void launchExternalProcess() {
        Thread thread = new Thread(new Runnable() {
            public void run() {

                ExecutionInfo executionInfo = null;
                ExecHandle execHandle = null;
                ByteArrayOutputStream output = null;
                try {

                    executionInfo = protocol.getExecutionInfo(getPort());

                    ExecHandleBuilder builder = new ExecHandleBuilder();
                    builder.workingDir(executionInfo.getWorkingDirectory());
                    builder.commandLine((Object[]) executionInfo.getCommandLineArguments());
                    builder.environment(executionInfo.getEnvironmentVariables());
                    output = new ByteArrayOutputStream();
                    builder.setStandardOutput(output);
                    builder.setErrorOutput(output);
                    execHandle = builder.build();
                    setExternalProcess(execHandle);

                    execHandle.start();
                } catch (Throwable e) {
                    LOGGER.error("Starting external process", e);
                    notifyClientExited(-1, e.getMessage());
                    setExternalProcess(null);
                    return;
                }

                ExecResult result = execHandle.waitForFinish();
                LOGGER.debug("External process completed with exit code {}", result.getExitValue());

                setExternalProcess(null);   //clear our external process member variable (we're using our local variable below). This is so we know the process has already stopped.

                executionInfo.processExecutionComplete();
                notifyClientExited(result.getExitValue(), output.toString());
            }
        });

        thread.start();
    }

    public void stop() {
        super.stop();
        killProcess(); //if the process is still running, shut it down
    }

    public void setExternalProcess(ExecHandle externalProcess) {
        this.externalProcess = externalProcess;
    }

    /**
     * Call this to violently kill the external process. This is NOT a good way to stop it. It is preferable to ask the thread to stop. However, gradle has no way to do that, so we'll be killing it.
     */
    public synchronized void killProcess() {
        if (externalProcess != null) {
            requestShutdown();
            protocol.aboutToKillProcess();
            externalProcess.abort();
            setExternalProcess(null);
            notifyClientExited(-1, "Process Canceled");
        }
    }

    private void notifyClientExited(final int result, final String output) {
        protocol.clientExited(result, output);

        observerLord.notifyObservers(new ObserverLord.ObserverNotification<ServerObserver>() {
            public void notify(ServerObserver observer) {
                observer.clientExited(result, output);
            }
        });
    }
}