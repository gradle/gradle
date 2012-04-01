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
import org.gradle.api.Action;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.ExceptionReportingAction;
import org.gradle.launcher.exec.ExecutionListener;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.StyledTextOutputFactory;
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

        ServiceRegistry loggingServices = createLoggingServices();
        List<CommandLineAction> actions = new ArrayList<CommandLineAction>();
        actions.add(new BuiltInActions());
        createActionFactories(loggingServices, actions);

        CommandLineParser parser = new CommandLineParser();
        for (CommandLineAction action : actions) {
            action.configureCommandLineParser(parser);
        }

        LoggingConfiguration loggingConfiguration = new LoggingConfiguration();
        Action<ExecutionListener> action;
        try {
            ParsedCommandLine commandLine = parser.parse(args);
            @SuppressWarnings("unchecked")
            CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = (CommandLineConverter<LoggingConfiguration>)loggingServices.get(CommandLineConverter.class);
            loggingConfigurationConverter.convert(commandLine, loggingConfiguration);
            action = createAction(actions, parser, commandLine);
        } catch (CommandLineArgumentException e) {
            action = new CommandLineParseFailureAction(parser, e);
        } catch (Throwable t) {
            action = new ActionAdapter(new BrokenAction(t));
        }

        return new WithLoggingAction(loggingConfiguration, loggingServices,
                new ExceptionReportingAction(action,
                        new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData())));
    }

    protected void createActionFactories(ServiceRegistry loggingServices, Collection<CommandLineAction> actions) {
        actions.add(new GuiActionsFactory());
        actions.add(new BuildActionsFactory(loggingServices, new DefaultCommandLineConverter()));
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }

    public ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
    }

    private Action<ExecutionListener> createAction(Iterable<CommandLineAction> factories, CommandLineParser parser, ParsedCommandLine commandLine) {
        for (CommandLineAction factory : factories) {
            Action<ExecutionListener> action = factory.createAction(parser, commandLine);
            if (action != null) {
                return action;
            }
        }
        throw new UnsupportedOperationException("No action factory for specified command-line arguments.");
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

        public Action<ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(HELP)) {
                return new ActionAdapter(new ShowUsageAction(parser));
            }
            if (commandLine.hasOption(VERSION)) {
                return new ActionAdapter(new ShowVersionAction());
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

    private static class BrokenAction implements Runnable {
        private final Throwable throwable;

        private BrokenAction(Throwable throwable) {
            this.throwable = throwable;
        }

        public void run() {
            throw UncheckedException.throwAsUncheckedException(throwable);
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
