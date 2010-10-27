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
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication;
import org.gradle.initialization.*;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * Responsible for converting a set of command-line arguments into a {@link Runnable} action.
 */
public class CommandLineActionFactory {
    private static final String HELP = "h";
    private static final String GUI = "gui";
    private static final String VERSION = "v";
    private static final String FOREGROUND = "foreground";
    private static final String NO_DAEMON = "no-daemon";
    private static final String STOP_DAEMON = "stop";
    private final BuildCompleter buildCompleter;

    public CommandLineActionFactory(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    /**
     * Converts the given command-line arguments to a {@code Runnable} action which performs the action requested by the
     * command-line args. Does not have any side-effects. Each action will call the supplied {@link
     * org.gradle.launcher.BuildCompleter} once it has completed.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    Runnable convert(List<String> args) {
        CommandLineParser parser = new CommandLineParser();

        CommandLineConverter<StartParameter> startParameterConverter = createStartParameterConverter();
        startParameterConverter.configure(parser);

        parser.option(HELP, "?", "help").hasDescription("Shows this help message");
        parser.option(VERSION, "version").hasDescription("Print version info.");
        parser.option(GUI).hasDescription("Launches a GUI application");
        parser.option(FOREGROUND).hasDescription("Runs the Gradle daemon in the foreground.");
        parser.option(NO_DAEMON).hasDescription("Does not use the Gradle daemon.");
        parser.option(STOP_DAEMON).hasDescription("Stops the Gradle daemon, if running.");

        LoggingConfiguration loggingConfiguration = new LoggingConfiguration();
        ServiceRegistry loggingServices = createLoggingServices();

        Runnable action;
        try {
            ParsedCommandLine parsedCommandLine = parser.parse(args);
            CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = loggingServices.get(CommandLineConverter.class);
            loggingConfiguration = loggingConfigurationConverter.convert(parsedCommandLine);
            action = createAction(parser, parsedCommandLine, args, loggingServices);
        } catch (CommandLineArgumentException e) {
            action = new CommandLineParseFailureAction(parser, e);
        }

        return new WithLoggingAction(loggingConfiguration, loggingServices, action);
    }

    CommandLineConverter<StartParameter> createStartParameterConverter() {
        return new DefaultCommandLineConverter();
    }

    ServiceRegistry createLoggingServices() {
        return new LoggingServiceRegistry();
    }

    GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry loggingServices) {
        return new DefaultGradleLauncherFactory(loggingServices);
    }

    private Runnable createAction(CommandLineParser parser, final ParsedCommandLine commandLine, final List<String> args, final ServiceRegistry loggingServices) {
        if (commandLine.hasOption(HELP)) {
            return new ShowUsageAction(parser);
        }
        if (commandLine.hasOption(VERSION)) {
            return new ShowVersionAction();
        }
        if (commandLine.hasOption(GUI)) {
            return new ShowGuiAction();
        }
        if (commandLine.hasOption(STOP_DAEMON)) {
            return new Runnable() {
                public void run() {
                    new GradleDaemon(loggingServices).stop();
                }
            };
        }
        if (commandLine.hasOption(FOREGROUND)) {
            return new Runnable() {
                public void run() {
                    new GradleDaemon(loggingServices).run();
                }
            };
        }
        if (commandLine.hasOption(NO_DAEMON)) {
            return new Runnable() {
                public void run() {
                    new GradleDaemon(loggingServices).build(new File(System.getProperty("user.dir")), commandLine.getExtraArguments());
                }
            };
        }
        return new Runnable() {
            public void run() {
                new GradleDaemon(loggingServices).clientMain(new File(System.getProperty("user.dir")), args);
            }
        };
    }

    private void showUsage(PrintStream out, CommandLineParser parser) {
        out.println();
        out.print("USAGE: ");
        new GradleLauncherMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parser.printUsage(out);
        out.println();
    }

    private class CommandLineParseFailureAction implements Runnable {
        private final Exception e;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception e) {
            this.parser = parser;
            this.e = e;
        }

        public void run() {
            System.err.println();
            System.err.println(e.getMessage());
            showUsage(System.err, parser);
            buildCompleter.exit(e);
        }
    }

    private class ShowUsageAction implements Runnable {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        public void run() {
            showUsage(System.out, parser);
            buildCompleter.exit(null);
        }
    }

    private class ShowVersionAction implements Runnable {
        public void run() {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }
    }

    class ShowGuiAction implements Runnable {
        public void run() {
            BlockingApplication.launchAndBlock();
            buildCompleter.exit(null);
        }
    }

    private class RunBuildAction implements Runnable {
        private final StartParameter startParameter;
        private final ServiceRegistry loggingServices;

        public RunBuildAction(StartParameter startParameter, ServiceRegistry loggingServices) {
            this.startParameter = startParameter;
            this.loggingServices = loggingServices;
        }

        public void run() {
            GradleLauncherFactory gradleLauncherFactory = createGradleLauncherFactory(loggingServices);
            GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameter);
            BuildResult buildResult = gradleLauncher.run();
            buildCompleter.exit(buildResult.getFailure());
        }
    }

    class WithLoggingAction implements Runnable {
        private final LoggingConfiguration loggingConfiguration;
        private final ServiceRegistry loggingServices;
        private final Runnable action;

        public WithLoggingAction(LoggingConfiguration loggingConfiguration, ServiceRegistry loggingServices, Runnable action) {
            this.loggingConfiguration = loggingConfiguration;
            this.loggingServices = loggingServices;
            this.action = action;
        }

        public void run() {
            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevel(loggingConfiguration.getLogLevel());
            loggingManager.colorStdOutAndStdErr(loggingConfiguration.isColorOutput());
            loggingManager.start();
            action.run();
        }
    }
}
