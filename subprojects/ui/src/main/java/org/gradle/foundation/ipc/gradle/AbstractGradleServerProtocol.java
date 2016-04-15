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
package org.gradle.foundation.ipc.gradle;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.foundation.CommandLineAssistant;
import org.gradle.foundation.ipc.basic.ClientProcess;
import org.gradle.foundation.ipc.basic.ExecutionInfo;
import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.api.logging.configuration.ShowStacktrace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * This defines the basic behavior of all gradle protocols for interprocess communication. It manages handshaking, detecting if the client executed prematurely, as well as executing alternate external
 * processes. All you need to do is extend this, implement the abstract functions, and make sure you call setHasReceivedBuildCompleteNotification() when whatever you were doing is complete (so we know
 * any exiting is not premature).
 */
public abstract class AbstractGradleServerProtocol implements ProcessLauncherServer.Protocol {
    private static final String INIT_SCRIPT_EXTENSION = ".gradle";

    private final Logger logger = Logging.getLogger(AbstractGradleServerProtocol.class);
    private final TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();

    protected ProcessLauncherServer server;
    private boolean continueConnection;
    private boolean waitingOnHandshakeCompletion;
    private boolean hasCompletedConnection;

    private boolean hasReceivedBuildCompleteNotification;

    private File currentDirectory;
    private File gradleHomeDirectory;

    private File customGradleExecutor;
    private String commandLine;
    private LogLevel logLevel;

    //all of this is just so we can get gradle to kill itself when we cancel
    private int killGradleServerPort;
    private KillGradleClientProtocol killGradleClientProcotol;
    private ClientProcess killGradleClient;

    protected MessageObject lastMessageReceived; //just for debugging purposes

    /**
     * @return true if we should keep the connection alive. False if we should stop communicaiton.
     */
    public boolean continueConnection() {
        return continueConnection;
    }

    private ShowStacktrace stackTraceLevel;

    public AbstractGradleServerProtocol(File currentDirectory, File gradleHomeDirectory, File customGradleExecutor, String fullCommandLine, LogLevel logLevel,
                                        ShowStacktrace stackTraceLevel) {
        this.currentDirectory = currentDirectory;
        this.gradleHomeDirectory = gradleHomeDirectory;
        this.customGradleExecutor = customGradleExecutor;
        this.commandLine = fullCommandLine;
        this.logLevel = logLevel;
        this.stackTraceLevel = stackTraceLevel;
    }

    /**
     * Notification that the connection was accepted by the client.
     */
    public void connectionAccepted() {
        //let's make sure we're talking to the right client with some tiny handshaking.
        server.sendMessage(ProtocolConstants.HANDSHAKE_TYPE, ProtocolConstants.HANDSHAKE_SERVER);
        continueConnection = true;
        waitingOnHandshakeCompletion = true;
    }

    /**
     * Gives your protocol a chance to store this server so it can access its functions.
     */
    public void initialize(ProcessLauncherServer server) {
        this.server = server;
    }

    /**
     * Call this to stop communication
     */
    protected void closeConnection() {
        this.continueConnection = false;
    }

    /**
     * Notification that a message has been received. If we just connected, we'll do a quick handshake to verify the client, then we just pass the rest on our output panel.
     *
     * @param message the message that was received.
     */
    public void messageReceived(MessageObject message) {
        lastMessageReceived = message;
        if (waitingOnHandshakeCompletion) {
            //are we still handshaking?
            if (ProtocolConstants.HANDSHAKE_CLIENT.equalsIgnoreCase(message.getMessage())) {
                waitingOnHandshakeCompletion = false;  //we've received what we expected
                hasCompletedConnection = true;         //and we're now connected
                if (message.getData() != null) {
                    killGradleServerPort = (Integer) message.getData();
                    killGradleClientProcotol = new KillGradleClientProtocol();
                    killGradleClient = new ClientProcess(killGradleClientProcotol);
                    killGradleClient.start(killGradleServerPort);
                    handShakeCompleted();
                } else {
                    addStatus("Invalid handshaking. Missing port number. Stopping connection");
                    server.sendMessage("?", "Invalid client handshake protocol!");
                    closeConnection();
                }
            } else {
                addStatus("Invalid handshaking. Stopping connection");
                server.sendMessage("?", "Invalid client handshake protocol!");
                closeConnection();
            }
        } else {
            //otherwise, its just a normal message, the protocol should handle it.
            try {
                handleMessageReceived(message);
            } catch (Throwable e) {
                logger.error("Problem while handing message :\n" + message, e);
            }
        }
    }

    /**
     * This provides you with a chance to do something when the handshaking is complete
     */
    protected void handShakeCompleted() {

    }

    /**
     * Notification that a message was received that we didn't process. Implement this to handle the specifics of your protocol. Basically, the base class handles the handshake. The rest of the
     * conversation is up to you.
     *
     * @param message the message we received.
     * @return true if we handled the message, false if not. If we don't know it, we won't return an acknowlegement.
     */
    protected abstract boolean handleMessageReceived(MessageObject message);

    /**
     * Call this to mark the build as completed (whether successfully or not). This is used to determine if the client has exited prematurely which indicates a problem.
     */
    public void setHasReceivedBuildCompleteNotification() {
        this.hasReceivedBuildCompleteNotification = true;
    }

    /**
     * Notification of any status that might be helpful to the user.
     *
     * @param status a status message
     */
    protected abstract void addStatus(String status);

    public class MyExecutionInfo implements ExecutionInfo {
        public String[] commandLineArguments;
        public File workingDirectory;
        public HashMap<String, String> environmentVariables = new HashMap<String, String>();
        public File initStriptPath;

        public String[] getCommandLineArguments() {
            return commandLineArguments;
        }

        public File getWorkingDirectory() {
            return workingDirectory;
        }

        public HashMap<String, String> getEnvironmentVariables() {
            return environmentVariables;
        }

        public void setCommandLineArguments(String[] commandLineArguments) {
            this.commandLineArguments = commandLineArguments;
        }

        public void setWorkingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public void addEnvironmentVariable(String name, String value) {
            this.environmentVariables.put(name, value);
        }

        public void processExecutionComplete() {
            if (initStriptPath != null) {
                initStriptPath.delete();
            }
        }
    }

    /**
     * Fill in the ExecutionInfo object with information needed to execute the other process.
     *
     * @param serverPort the port the server is listening on. The client should send messages here
     * @return an executionInfo object containing information about what we execute.
     */
    public ExecutionInfo getExecutionInfo(int serverPort) {
        MyExecutionInfo executionInfo = new MyExecutionInfo();

        //set some environment variables that need to be passed to the script.
        executionInfo.addEnvironmentVariable("JAVA_HOME", Jvm.current().getJavaHome().getAbsolutePath());

        executionInfo.setWorkingDirectory(currentDirectory);

        List<String> executionCommandLine = new ArrayList<String>();

        //put the file to execute on the command line
        File gradleExecutableFile = getGradleExecutableFile();
        if (gradleExecutableFile == null) {
            throw new RuntimeException("Gradle executable not specified");
        }
        if (!gradleExecutableFile.exists()) {
            throw new RuntimeException("Missing gradle executable. Expected it at: " + gradleExecutableFile);
        }
        executionCommandLine.add(gradleExecutableFile.getAbsolutePath());

        //add the port number we're listenening on
        executionCommandLine.add("-D" + ProtocolConstants.PORT_NUMBER_SYSTEM_PROPERTY + "=" + Integer.toString(serverPort));

        CommandLineAssistant commandLineAssistant = new CommandLineAssistant();

        //add whatever the user ran
        String[] individualCommandLineArguments = CommandLineAssistant.breakUpCommandLine(commandLine);
        executionCommandLine.addAll(Arrays.asList(individualCommandLineArguments));

        File initStriptPath = getInitScriptFile();
        if (initStriptPath != null) {
            executionCommandLine.add("-" + DefaultCommandLineConverter.INIT_SCRIPT);
            executionCommandLine.add(initStriptPath.getAbsolutePath());
            executionInfo.initStriptPath = initStriptPath;
        }

        //add the log level if its not present
        if (!commandLineAssistant.hasLogLevelDefined(individualCommandLineArguments)) {
            String logLevelText = commandLineAssistant.getLoggingCommandLineConverter().getLogLevelCommandLine(logLevel);
            if (logLevelText != null && !"".equals(logLevelText)) {
                executionCommandLine.add('-' + logLevelText);
            }
        }

        //add the stack trace level if its not present
        if (!commandLineAssistant.hasShowStacktraceDefined(individualCommandLineArguments)) {
            String stackTraceLevelText = commandLineAssistant.getLoggingCommandLineConverter().getShowStacktraceCommandLine(stackTraceLevel);
            if (stackTraceLevelText != null) {
                executionCommandLine.add('-' + stackTraceLevelText);
            }
        }

        executionInfo.setCommandLineArguments(executionCommandLine.toArray(new String[0]));
        return executionInfo;
    }

    /**
     * @return the file that should be used to execute gradle. If we've been given a custom file, we use that, otherwise, we use the batch or shell script inside the gradle home's bin directory.
     */
    protected File getGradleExecutableFile() {
        if (customGradleExecutor != null) {
            return customGradleExecutor;
        }

        return new File(gradleHomeDirectory, "bin" + File.separator + getDefaultGradleExecutableName());
    }

    /**
     * This determines what we're going to execute. Its different based on the OS.
     *
     * @return whatever we're going to execute.
     */
    private String getDefaultGradleExecutableName() {
        return OperatingSystem.current().getScriptName("gradle");
    }

    /**
     * Notification that the client has stopped all communications.
     */
    public void clientCommunicationStopped() {
        //we don't really care
    }

    /**
     * Notification that the client has shutdown. Note: this can occur before communications has ever started. You SHOULD get this notification before receiving serverExited, even if the client fails
     * to launch or locks up.
     *
     * @param returnCode the return code of the client application
     * @param output the standard error and standard output of the client application
     */
    public void clientExited(int returnCode, String output) {
        server.requestShutdown();

        boolean wasPremature = false;
        String message;

        if (!hasCompletedConnection) {
            //if we never connected, report it
            message = "Failed to connect to gradle process for command '" + commandLine + "'\n" + output;
            wasPremature = true;
        } else if (!hasReceivedBuildCompleteNotification) {
            //this may happen if the client doesn't execute properly or it was killed/canceled. This is just so we don't lose our output (which may yeild clues to the problem).
            message = output;
            wasPremature = true;
        } else {
            message = output;
        }

        reportClientExit(wasPremature, returnCode, message);
    }

    /**
     * This is called if the client exits prematurely. That is, we never connected to it or it didn't finish. This can happen because of setup issues or errors that occur in gradle.
     *
     * @param returnCode the return code of the application
     */
    protected abstract void reportClientExit(boolean wasPremature, int returnCode, String output);

    /**
     * This is called before we execute a command. Here, return an init script for this protocol. An init script is a gradle script that gets run before the other scripts are processed. This is useful
     * here for initiating the gradle client that talks to the server.
     *
     * @return The path to an init script. Null if you have no init script.
     */
    public abstract File getInitScriptFile();

    /**
     * If you do have an init script that's a resource, this will extract it based on the name and write it to a temporary file and delete it on exit.
     *
     * @param resourceClass the class associated with the resource
     * @param resourceName the name (minus extension or '.') of the resource
     */
    protected File extractInitScriptFile(Class resourceClass, String resourceName) {
        File file = null;
        try {
            file = temporaryFileProvider.createTemporaryFile(resourceName, INIT_SCRIPT_EXTENSION);
        } catch (UncheckedIOException e) {
            logger.error("Creating init script file temp file", e);
            return null;
        }
        file.deleteOnExit();

        if (extractResourceAsFile(resourceClass, resourceName + INIT_SCRIPT_EXTENSION, file)) {
            return file;
        }

        logger.error("Internal error! Failed to extract init script for executing commands!");

        return null;
    }

    /**
     * This extracts the given class' resource to the specified file if it doesn't already exist.
     *
     * @param resourceClass the class associated with the resource
     * @param name the resource's name
     * @param file where to put the resource
     * @return true if successful, false if not.
     */
    public boolean extractResourceAsFile(Class resourceClass, String name, File file) {
        InputStream stream = resourceClass.getResourceAsStream(name);
        if (stream == null) {
            return false;
        }

        byte[] bytes = new byte[0];
        try {
            bytes = IOUtils.toByteArray(stream);
        } catch (IOException e) {
            logger.error("Extracting resource as file", e);
            return false;
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            try {
                IOUtils.write(bytes, fileOutputStream);
            } finally {
                fileOutputStream.close();
            }
            return true;
        } catch (IOException e) {
            logger.error("Extracting resource as file (writing bytes)", e);
            return false;
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    protected File getGradleHomeDirectory() {
        return gradleHomeDirectory;
    }

    /**
     * Notification that a read failure occurred. This really only exists for debugging purposes when things go wrong.
     */
    public void readFailureOccurred() {
        logger.debug("Last message received: " + lastMessageReceived);
    }

    public void aboutToKillProcess() {
        killGradle();
    }

    public void killGradle() {
        if (killGradleClientProcotol != null) {
            killGradleClientProcotol.sendKillMessage();
        }
    }
}
