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
package org.gradle.launcher;

import org.gradle.*;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.messaging.remote.internal.Message;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;
import org.gradle.util.UncheckedException;

import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GradleDaemon {
    public static final int PORT = 12345;
    private static final Logger LOGGER = Logging.getLogger(Main.class);
    private final ServiceRegistry loggingServices;
    private final GradleLauncherFactory launcherFactory;

    public static void main(String[] args) {
        try {
            new GradleDaemon(new LoggingServiceRegistry()).run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    public GradleDaemon(ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
        launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (true) {
                LOGGER.lifecycle("Waiting for request");
                Socket socket = serverSocket.accept();
                try {
                    OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
                    boolean finished = doRun(socket, outputStream);
                    Message.send(new Stop(), outputStream);
                    outputStream.flush();
                    if (finished) {
                        break;
                    }
                } finally {
                    socket.close();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean doRun(Socket socket, final OutputStream oos) {
        try {
            Clock clock = new Clock();
            final InputStream ois = new BufferedInputStream(socket.getInputStream());
            Command command = (Command) Message.receive(ois, getClass().getClassLoader());
            LOGGER.info("Executing {}", command);
            if (command instanceof Stop) {
                LOGGER.lifecycle("Stopping");
                return true;
            }
            LoggingOutputInternal loggingOutput = loggingServices.get(LoggingOutputInternal.class);
            OutputEventListener listener = new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    try {
                        Message.send(event, oos);
                    } catch (IOException e) {
                        throw UncheckedException.asUncheckedException(e);
                    }
                }
            };
            loggingOutput.addOutputEventListener(listener);
            try {
                build((Build) command, clock);
            } finally {
                loggingOutput.removeOutputEventListener(listener);
            }
        } catch (Throwable throwable) {
            LOGGER.error("Could not execute build.", throwable);
        }
        return false;
    }

    public void clientMain(File currentDir, ParsedCommandLine args) {
        try {
            Socket socket = connect(args);
            run(new Build(currentDir, args), socket);
        } catch (Throwable t) {
            throw UncheckedException.asUncheckedException(t);
        }
    }

    private void build(Build build, Clock clock) {
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(build.currentDir);
        converter.convert(build.args, startParameter);
        LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
        loggingManager.setLevel(startParameter.getLogLevel());
        loggingManager.start();

        BuildListener resultLogger = new BuildLogger(LOGGER, loggingServices.get(StyledTextOutputFactory.class), clock, startParameter);
        try {
            GradleLauncher launcher = launcherFactory.newInstance(startParameter);
            launcher.useLogger(resultLogger);
            launcher.run();
        } catch (Throwable e) {
            resultLogger.buildFinished(new BuildResult(null, e));
        }

        loggingManager.stop();
    }

    public void stop() {
        try {
            maybeStop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void maybeStop() throws Exception {
        Socket socket = maybeConnect();
        if (socket == null) {
            LOGGER.info("Gradle daemon is not running.");
            return;
        }
        run(new Stop(), socket);
        LOGGER.info("Gradle daemon stopped.");
    }

    private void run(Command command, Socket socket) throws Exception {
        try {
            OutputStream oos = new BufferedOutputStream(socket.getOutputStream());
            Message.send(command, oos);
            oos.flush();

            OutputEventListener outputEventListener = loggingServices.get(OutputEventListener.class);
            InputStream ois = new BufferedInputStream(socket.getInputStream());
            while (true) {
                Object object = Message.receive(ois, getClass().getClassLoader());
                if (object instanceof Stop) {
                    break;
                }
                OutputEvent outputEvent = (OutputEvent) object;
                outputEventListener.onOutput(outputEvent);
            }
        } finally {
            socket.close();
        }
    }

    private Socket connect(ParsedCommandLine args) throws Exception {
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        StartParameter startParameter = converter.convert(args);
        File userHomeDir = startParameter.getGradleUserHomeDir();

        Socket socket = maybeConnect();
        if (socket != null) {
            return socket;
        }

        LOGGER.lifecycle("Starting Gradle daemon");
        List<String> daemonArgs = new ArrayList<String>();
        daemonArgs.add(Jvm.current().getJavaExecutable().getAbsolutePath());
        daemonArgs.add("-Xmx1024m");
        daemonArgs.add("-XX:MaxPermSize=256m");
        daemonArgs.add("-cp");
        daemonArgs.add(GUtil.join(new DefaultClassPathRegistry().getClassPathFiles("GRADLE_RUNTIME"),
                File.pathSeparator));
        daemonArgs.add(GradleDaemon.class.getName());
        ProcessBuilder builder = new ProcessBuilder(daemonArgs);
        builder.directory(userHomeDir);
        Process process = builder.start();
        process.getOutputStream().close();
        process.getErrorStream().close();
        process.getInputStream().close();
        Date expiry = new Date(System.currentTimeMillis() + 30000L);
        do {
            socket = maybeConnect();
            if (socket != null) {
                return socket;
            }
            Thread.sleep(500L);
        } while (System.currentTimeMillis() < expiry.getTime());

        throw new RuntimeException("Timeout waiting to connect to Gradle daemon.");
    }

    private Socket maybeConnect() throws IOException {
        try {
            return new Socket(InetAddress.getByName(null), 12345);
        } catch (ConnectException e) {
            // Ignore
            return null;
        }
    }

    private static class Command implements Serializable {
        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    private static class Stop extends Command {
    }

    private static class Build extends Command {
        private final ParsedCommandLine args;
        private final File currentDir;

        public Build(File currentDir, ParsedCommandLine args) {
            this.currentDir = currentDir;
            this.args = args;
        }
    }
}
