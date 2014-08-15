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

import groovy.lang.GroovySystem;
import org.apache.tools.ant.Main;
import org.gradle.BuildExceptionReporter;
import org.gradle.api.Action;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.LayoutCommandLineConverter;
import org.gradle.internal.Actions;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.logging.internal.LoggingCommandLineConverter;
import org.gradle.util.GradleVersion;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class CommandLineActionFactory {
    private static final String HELP = "h";
    private static final String VERSION = "v";

    /**
     * <p>Converts the given command-line arguments to an {@link Action} which performs the action requested by the
     * command-line args.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    public Action<ExecutionListener> convert(List<String> args) {
        ServiceRegistry loggingServices = createLoggingServices();

        LoggingConfiguration loggingConfiguration = new LoggingConfiguration();

        return new ExceptionReportingAction(
                new WithLogging(loggingServices, args, loggingConfiguration,
                        new JavaRuntimeValidationAction(
                            new ParseAndBuildAction(loggingServices, args))),
                new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData()));
    }

    protected void createActionFactories(ServiceRegistry loggingServices, Collection<CommandLineAction> actions) {
        actions.add(new GuiActionsFactory());
        actions.add(new BuildActionsFactory(loggingServices));
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }

    public ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
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

    private static class BuiltInActions implements CommandLineAction {
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info.");
        }

        public Runnable createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(HELP)) {
                return new ShowUsageAction(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return new ShowVersionAction();
            }
            return null;
        }
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
            GradleVersion currentVersion = GradleVersion.current();

            final StringBuilder sb = new StringBuilder();
            sb.append("\n------------------------------------------------------------\nGradle ");
            sb.append(currentVersion.getVersion());
            sb.append("\n------------------------------------------------------------\n\nBuild time:   ");
            sb.append(currentVersion.getBuildTime());
            sb.append("\nBuild number: ");
            sb.append(currentVersion.getBuildNumber());
            sb.append("\nRevision:     ");
            sb.append(currentVersion.getRevision());
            sb.append("\n\nGroovy:       ");
            sb.append(GroovySystem.getVersion());
            sb.append("\nAnt:          ");
            sb.append(Main.getAntVersion());
            sb.append("\nJVM:          ");
            sb.append(Jvm.current());
            sb.append("\nOS:           ");
            sb.append(OperatingSystem.current());
            sb.append("\n");

            System.out.println(sb.toString());
        }
    }

    private static class WithLogging implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;
        private final LoggingConfiguration loggingConfiguration;
        private final Action<ExecutionListener> action;

        WithLogging(ServiceRegistry loggingServices, List<String> args, LoggingConfiguration loggingConfiguration, Action<ExecutionListener> action) {
            this.loggingServices = loggingServices;
            this.args = args;
            this.loggingConfiguration = loggingConfiguration;
            this.action = action;
        }

        public void execute(ExecutionListener executionListener) {
            CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = new LoggingCommandLineConverter();
            CommandLineConverter<BuildLayoutParameters> buildLayoutConverter = new LayoutCommandLineConverter();
            BuildLayoutParameters buildLayout = new BuildLayoutParameters();
            CommandLineParser parser = new CommandLineParser();
            loggingConfigurationConverter.configure(parser);
            buildLayoutConverter.configure(parser);
            parser.allowUnknownOptions();
            parser.allowMixedSubcommandsAndOptions();
            try {
                ParsedCommandLine parsedCommandLine = parser.parse(args);
                loggingConfigurationConverter.convert(parsedCommandLine, loggingConfiguration);
                buildLayoutConverter.convert(parsedCommandLine, buildLayout);
            } catch (CommandLineArgumentException e) {
                // Ignore, deal with this problem later
            }

            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevel(loggingConfiguration.getLogLevel());
            loggingManager.start();

            NativeServices.initialize(buildLayout.getGradleUserHomeDir());
            loggingManager.attachConsole(loggingConfiguration.isColorOutput());

            action.execute(executionListener);
        }
    }

    private class ParseAndBuildAction implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;

        private ParseAndBuildAction(ServiceRegistry loggingServices, List<String> args) {
            this.loggingServices = loggingServices;
            this.args = args;
        }

        public void execute(ExecutionListener executionListener) {
            List<CommandLineAction> actions = new ArrayList<CommandLineAction>();
            actions.add(new BuiltInActions());
            createActionFactories(loggingServices, actions);

            CommandLineParser parser = new CommandLineParser();
            for (CommandLineAction action : actions) {
                action.configureCommandLineParser(parser);
            }

            Action<? super ExecutionListener> action;
            try {
                ParsedCommandLine commandLine = parser.parse(args);
                action = createAction(actions, parser, commandLine);
            } catch (CommandLineArgumentException e) {
                action = new CommandLineParseFailureAction(parser, e);
            }

            action.execute(executionListener);
        }

        private Action<? super ExecutionListener> createAction(Iterable<CommandLineAction> factories, CommandLineParser parser, ParsedCommandLine commandLine) {
            for (CommandLineAction factory : factories) {
                Runnable action = factory.createAction(parser, commandLine);
                if (action != null) {
                    return Actions.toAction(action);
                }
            }
            throw new UnsupportedOperationException("No action factory for specified command-line arguments.");
        }
    }
}
