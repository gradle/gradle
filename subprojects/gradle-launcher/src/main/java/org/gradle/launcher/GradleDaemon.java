package org.gradle.launcher;

import org.gradle.*;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.LoggingOutputInternal;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
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
            new GradleDaemon().run(args);
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    public GradleDaemon() {
        loggingServices = new LoggingServiceRegistry();
        launcherFactory = new DefaultGradleLauncherFactory(loggingServices);
    }

    public void run(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(PORT);
        while (true) {
            LOGGER.lifecycle("Waiting for request");
            Socket socket = serverSocket.accept();
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                boolean finished = doRun(socket, oos);
                oos.writeObject(new Stop());
                oos.flush();
                if (finished) {
                    break;
                }
            } finally {
                socket.close();
            }
        }
    }

    private boolean doRun(Socket socket, final ObjectOutputStream oos) {
        try {
            Clock clock = new Clock();
            final ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
            Command command = (Command) ois.readObject();
            LOGGER.info("Executing {}", command);
            if (command instanceof Stop) {
                LOGGER.lifecycle("Stopping");
                return true;
            }
            LoggingOutputInternal loggingOutput = loggingServices.get(LoggingOutputInternal.class);
            OutputEventListener listener = new OutputEventListener() {
                public void onOutput(OutputEvent event) {
                    try {
                        oos.writeObject(event);
                        oos.flush();
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

    public void clientMain(File currentDir, String[] args) {
        try {
            Socket socket = connect();
            run(new Build(currentDir, args), socket);
        } catch (Throwable t) {
            throw UncheckedException.asUncheckedException(t);
        }
    }

    public void build(File file, String[] args) {
        build(new Build(file, args), new Clock());
    }

    private void build(Build build, Clock clock) {
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(build.currentDir);
        CommandLine2StartParameterConverter converter = new DefaultCommandLine2StartParameterConverter();
        converter.convert(build.args, startParameter);

        BuildListener resultLogger = new BuildLogger(LOGGER, clock, startParameter);
        try {
            GradleLauncher launcher = launcherFactory.newInstance(startParameter);
            launcher.useLogger(resultLogger);
            launcher.run();
        } catch (Throwable e) {
            resultLogger.buildFinished(new BuildResult(null, e));
        }
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
            return;
        }
        run(new Stop(), socket);
    }

    private void run(Command command, Socket socket) throws Exception {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            oos.writeObject(command);
            oos.flush();

            OutputEventListener outputEventListener = loggingServices.get(OutputEventListener.class);
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
            while (true) {
                Object object = ois.readObject();
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

    private Socket connect() throws Exception {
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
        private final String[] args;
        private final File currentDir;

        public Build(File currentDir, String[] args) {
            this.currentDir = currentDir;
            this.args = args;
        }
    }
}
