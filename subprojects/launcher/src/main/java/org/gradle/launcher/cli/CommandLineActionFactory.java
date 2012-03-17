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
package org.gradle.launcher.cli;

import org.gradle.BuildExceptionReporter;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonMain;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientServices;
import org.gradle.launcher.daemon.client.SingleUseDaemonClientServices;
import org.gradle.launcher.daemon.client.StopDaemonClientServices;
import org.gradle.launcher.daemon.configuration.CurrentProcess;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.ForegroundDaemonConfiguration;
import org.gradle.launcher.exec.ExceptionReportingAction;
import org.gradle.launcher.exec.ExecutionListener;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

/**
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class CommandLineActionFactory {
    private static final String HELP = "h";
    private static final String GUI = "gui";
    private static final String VERSION = "v";
    private static final String FOREGROUND = "foreground";
    private static final String DAEMON = "daemon";
    private static final String NO_DAEMON = "no-daemon";
    private static final String STOP = "stop";

    /**
     * <p>Converts the given command-line arguments to a {@code Runnable} action which performs the action requested by the
     * command-line args. Does not have any side-effects. Each action will call the supplied {@link
     * org.gradle.launcher.exec.ExecutionListener} once it has completed.</p>
     *
     * <p>Implementation note: attempt to defer as much as possible until action execution time.</p>
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    public Action<ExecutionListener> convert(List<String> args) {
        CommandLineParser parser = new CommandLineParser();

        CommandLineConverter<StartParameter> startParameterConverter = createStartParameterConverter();
        startParameterConverter.configure(parser);

        parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
        parser.option(VERSION, "version").hasDescription("Print version info.");
        parser.option(GUI).hasDescription("Launches the Gradle GUI.");
        parser.option(FOREGROUND).hasDescription("Starts the Gradle daemon in the foreground.").experimental();
        parser.option(DAEMON).hasDescription("Uses the Gradle daemon to run the build. Starts the daemon if not running.");
        parser.option(NO_DAEMON).hasDescription("Do not use the Gradle daemon to run the build.");
        parser.option(STOP).hasDescription("Stops the Gradle daemon if it is running.");

        LoggingConfiguration loggingConfiguration = new LoggingConfiguration();
        ServiceRegistry loggingServices = createLoggingServices();

        Action<ExecutionListener> action;
        try {
            ParsedCommandLine commandLine = parser.parse(args);
            @SuppressWarnings("unchecked")
            CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = (CommandLineConverter<LoggingConfiguration>)loggingServices.get(CommandLineConverter.class);
            loggingConfigurationConverter.convert(commandLine, loggingConfiguration);
            action = createAction(parser, commandLine, startParameterConverter, loggingServices);
        } catch (CommandLineArgumentException e) {
            action = new CommandLineParseFailureAction(parser, e);
        }

        return new WithLoggingAction(loggingConfiguration, loggingServices,
                new ExceptionReportingAction(action,
                        new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData())));
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }

    public CommandLineConverter<StartParameter> createStartParameterConverter() {
        return new DefaultCommandLineConverter();
    }

    public ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
    }

    private Action<ExecutionListener> createAction(CommandLineParser parser, final ParsedCommandLine commandLine, CommandLineConverter<StartParameter> startParameterConverter, final ServiceRegistry loggingServices) {
        if (commandLine.hasOption(HELP)) {
            return new ActionAdapter(new ShowUsageAction(parser));
        }
        if (commandLine.hasOption(VERSION)) {
            return new ActionAdapter(new ShowVersionAction());
        }
        if (commandLine.hasOption(GUI)) {
            return new ActionAdapter(new ShowGuiAction());
        }

        final StartParameter startParameter = new StartParameter();
        startParameterConverter.convert(commandLine, startParameter);
        DaemonParameters daemonParameters = constructDaemonParameters(startParameter);
        if (commandLine.hasOption(FOREGROUND)) {
            ForegroundDaemonConfiguration conf = new ForegroundDaemonConfiguration(
                    daemonParameters.getUid(), daemonParameters.getBaseDir(), daemonParameters.getIdleTimeout());
            return new ActionAdapter(new ForegroundDaemonMain(conf));
        }

        if (commandLine.hasOption(STOP)) {
            return stopAllDaemons(daemonParameters, loggingServices);
        }
        if (useDaemon(commandLine, daemonParameters)) {
            return runBuildWithDaemon(commandLine, daemonParameters, loggingServices);
        }
        if (canUseCurrentProcess(daemonParameters)) {
            return runBuildInProcess(loggingServices, startParameter);
        }
        return runBuildInSingleUseDaemon(commandLine, daemonParameters, loggingServices);
    }

    private DaemonParameters constructDaemonParameters(StartParameter startParameter) {
        Map<String, String> mergedSystemProperties = startParameter.getMergedSystemProperties();
        DaemonParameters daemonParameters = new DaemonParameters();
        daemonParameters.configureFromBuildDir(startParameter.getCurrentDir(), startParameter.isSearchUpwards());
        daemonParameters.configureFromGradleUserHome(startParameter.getGradleUserHomeDir());
        daemonParameters.configureFromSystemProperties(mergedSystemProperties);
        return daemonParameters;
    }

    private Action<ExecutionListener> stopAllDaemons(DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        DaemonClientServices clientServices = new StopDaemonClientServices(loggingServices, daemonParameters, System.in);
        DaemonClient stopClient = clientServices.get(DaemonClient.class);
        return new ActionAdapter(new StopDaemonAction(stopClient));
    }

    private boolean useDaemon(ParsedCommandLine commandLine, DaemonParameters daemonParameters) {
        boolean useDaemon = daemonParameters.isEnabled();
        useDaemon = useDaemon || commandLine.hasOption(DAEMON);
        useDaemon = useDaemon && !commandLine.hasOption(NO_DAEMON);
        return useDaemon;
    }

    private Action<ExecutionListener> runBuildWithDaemon(ParsedCommandLine commandLine, DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        // Create a client that will match based on the daemon startup parameters.
        DaemonClientServices clientServices = new DaemonClientServices(loggingServices, daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return daemonBuildAction(commandLine, daemonParameters, client);
    }

    private boolean canUseCurrentProcess(DaemonParameters requiredBuildParameters) {
        CurrentProcess currentProcess = new CurrentProcess();
        return currentProcess.configureForBuild(requiredBuildParameters);
    }

    private Action<ExecutionListener> runBuildInProcess(ServiceRegistry loggingServices, StartParameter startParameter) {
        return new RunBuildAction(startParameter, loggingServices, new DefaultBuildRequestMetaData(clientMetaData(), getBuildStartTime()));
    }

    private Action<ExecutionListener> runBuildInSingleUseDaemon(ParsedCommandLine commandLine, DaemonParameters daemonParameters, ServiceRegistry loggingServices) {
        //(SF) this is a workaround until this story is completed. I'm hardcoding setting the idle timeout to be max X mins.
        //this way we avoid potential runaway daemons that steal resources on linux and break builds on windows.
        //We might leave that in if we decide it's a good idea for an extra safety net.
        int maxTimeout = 2 * 60 * 1000;
        if (daemonParameters.getIdleTimeout() > maxTimeout) {
            daemonParameters.setIdleTimeout(maxTimeout);
        }
        //end of workaround.

        // Create a client that will not match any existing daemons, so it will always startup a new one
        DaemonClientServices clientServices = new SingleUseDaemonClientServices(loggingServices, daemonParameters, System.in);
        DaemonClient client = clientServices.get(DaemonClient.class);
        return daemonBuildAction(commandLine, daemonParameters, client);
    }

    private Action<ExecutionListener> daemonBuildAction(ParsedCommandLine commandLine, DaemonParameters daemonParameters, DaemonClient client) {
        return new ActionAdapter(
                new DaemonBuildAction(client, commandLine, getWorkingDir(), clientMetaData(), getBuildStartTime(), daemonParameters.getEffectiveSystemProperties(), System.getenv()));
    }

    private long getBuildStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    private File getWorkingDir() {
        return new File(System.getProperty("user.dir"));
    }

    private static void showUsage(PrintStream out, CommandLineParser parser) {
        out.println();
        out.print("USAGE: ");
        clientMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parser.printUsage(out);
        out.println();
    }

    private static class CommandLineParseFailureAction implements Action<ExecutionListener> {
        private final Exception e;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception e) {
            this.parser = parser;
            this.e = e;
        }

        public void execute(ExecutionListener executionListener) {
            System.err.println();
            System.err.println(e.getMessage());
            showUsage(System.err, parser);
            executionListener.onFailure(e);
        }
    }

    private static class ShowUsageAction implements Runnable {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        public void run() {
            showUsage(System.out, parser);
        }
    }

    private static class ShowVersionAction implements Runnable {
        public void run() {
            System.out.println(GradleVersion.current().prettyPrint());
        }
    }

    static class ShowGuiAction implements Runnable {
        public void run() {
            BlockingApplication.launchAndBlock();
        }
    }

    static class ActionAdapter implements Action<ExecutionListener> {
        private final Runnable action;

        ActionAdapter(Runnable action) {
            this.action = action;
        }

        public void execute(ExecutionListener executionListener) {
            action.run();
        }

        public String toString() {
            return String.format("ActionAdapter[runnable=%s]", action);
        }
    }

    static class WithLoggingAction implements Action<ExecutionListener> {
        private final LoggingConfiguration loggingConfiguration;
        private final ServiceRegistry loggingServices;
        private final Action<ExecutionListener> action;

        public WithLoggingAction(LoggingConfiguration loggingConfiguration, ServiceRegistry loggingServices, Action<ExecutionListener> action) {
            this.loggingConfiguration = loggingConfiguration;
            this.loggingServices = loggingServices;
            this.action = action;
        }

        public void execute(ExecutionListener executionListener) {
            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevel(loggingConfiguration.getLogLevel());
            loggingManager.colorStdOutAndStdErr(loggingConfiguration.isColorOutput());
            loggingManager.start();
            action.execute(executionListener);
        }
    }
}
