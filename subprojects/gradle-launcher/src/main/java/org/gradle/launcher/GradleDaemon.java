package org.gradle.launcher;

import org.fusesource.jansi.AnsiConsole;
import org.gradle.*;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.CommandLine2StartParameterConverter;
import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.gradle.logging.internal.TerminalDetector;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.Jvm;

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

    public static void main(String[] args) {
        try {
            run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        // Force logging to be initialised
        GradleLauncher.createStartParameter();

        ServerSocket serverSocket = new ServerSocket(PORT);
        while (true) {
            LOGGER.lifecycle("Daemon running");
            Socket socket = serverSocket.accept();
            try {
                Clock clock = new Clock();
                PrintStream stdout = new PrintStream(socket.getOutputStream(), true);
                try {
                    ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                    Command command = (Command) ois.readObject();
                    if (command instanceof Stop) {
                        LOGGER.lifecycle("Daemon stopping");
                        return;
                    }
                    PrintStream origOut = System.out;
                    PrintStream origErr = System.err;
                    System.setOut(stdout);
                    System.setErr(stdout);
                    try {
                        build((BuildArgs) command, clock);
                    } finally {
                        System.setOut(origOut);
                        System.setErr(origErr);
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace(stdout);
                }
                stdout.flush();
            } finally {
                socket.close();
            }
        }
    }

    public static void clientMain(File currentDir, String[] args) {
        try {
            TerminalDetector detector = new TerminalDetector();
            System.setOut(AnsiConsole.out());
            System.setErr(AnsiConsole.err());
            runClient(new BuildArgs(currentDir, args, detector.isSatisfiedBy(FileDescriptor.out), detector.isSatisfiedBy(
                    FileDescriptor.err)));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void build(BuildArgs buildArgs, Clock clock) {
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(buildArgs.currentDir);
        startParameter.setStdoutTerminal(buildArgs.stdoutIsTerminal);
        startParameter.setStderrTerminal(buildArgs.stderrIsTerminal);
        CommandLine2StartParameterConverter converter = new DefaultCommandLine2StartParameterConverter();
        converter.convert(buildArgs.args, startParameter);

        BuildListener resultLogger = new BuildLogger(LOGGER, clock, startParameter);
        try {
            GradleLauncher launcher = GradleLauncher.newInstance(startParameter);
            launcher.useLogger(resultLogger);
            launcher.run();
        } catch (Throwable e) {
            resultLogger.buildFinished(new BuildResult(null, e));
        }
    }

    public static void build(File file, String[] args) {
        GradleLauncher.createStartParameter();
        TerminalDetector detector = new TerminalDetector();
        System.setOut(new PrintStream(AnsiConsole.wrapOutputStream(new FileOutputStream(FileDescriptor.out)), true));
        System.setErr(new PrintStream(AnsiConsole.wrapOutputStream(new FileOutputStream(FileDescriptor.err)), true));
        build(new BuildArgs(file, args, detector.isSatisfiedBy(FileDescriptor.out), detector.isSatisfiedBy(
                FileDescriptor.err)), new Clock());
    }

    public static void stop() {
        try {
            maybeStop();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void maybeStop() throws IOException {
        Socket socket = maybeConnect();
        if (socket == null) {
            return;
        }
        run(new Stop(), socket);
    }

    private static void runClient(BuildArgs args) throws Exception {
        Socket socket = connect();
        run(args, socket);
    }

    private static void run(Command command, Socket socket) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        oos.writeObject(command);
        oos.flush();

        byte[] buffer = new byte[1024];
        InputStream instr = socket.getInputStream();
        while (true) {
            int nread = instr.read(buffer);
            if (nread < 0) {
                break;
            }
            System.out.write(buffer, 0, nread);
            System.out.flush();
        }
        System.out.flush();
        socket.close();
    }

    private static Socket connect() throws Exception {
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

    private static Socket maybeConnect() throws IOException {
        try {
            return new Socket(InetAddress.getByName(null), 12345);
        } catch (ConnectException e) {
            // Ignore
            return null;
        }
    }

    private static class Command implements Serializable {
    }

    private static class Stop extends Command {
    }

    private static class BuildArgs extends Command {
        private final String[] args;
        private final File currentDir;
        private final boolean stdoutIsTerminal;
        private final boolean stderrIsTerminal;

        public BuildArgs(File currentDir, String[] args, boolean stdoutIsTerminal, boolean stderrIsTerminal) {
            this.currentDir = currentDir;
            this.args = args;
            this.stdoutIsTerminal = stdoutIsTerminal;
            this.stderrIsTerminal = stderrIsTerminal;
        }
    }
}
