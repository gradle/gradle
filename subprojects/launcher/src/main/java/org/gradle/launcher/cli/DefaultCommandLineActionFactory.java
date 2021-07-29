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

import com.google.common.annotations.VisibleForTesting;
import org.apache.tools.ant.Main;
import org.codehaus.groovy.util.ReleaseInfo;
import org.gradle.api.Action;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.bootstrap.CommandLineActionFactory;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.util.internal.DefaultGradleVersion;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class DefaultCommandLineActionFactory implements CommandLineActionFactory {
    public static final String WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY = "org.gradle.internal.launcher.welcomeMessageEnabled";
    private static final String HELP = "h";
    private static final String VERSION = "v";

    /**
     * <p>Converts the given command-line arguments to an {@link Action} which performs the action requested by the
     * command-line args.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    @Override
    public CommandLineExecution convert(List<String> args) {
        ServiceRegistry loggingServices = createLoggingServices();

        LoggingConfiguration loggingConfiguration = new DefaultLoggingConfiguration();

        return new WithLogging(loggingServices,
            args,
            loggingConfiguration,
            new ParseAndBuildAction(loggingServices, args),
            new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData()));
    }

    @VisibleForTesting
    protected void createActionFactories(ServiceRegistry loggingServices, Collection<CommandLineAction> actions) {
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
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info.");
        }

        @Override
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
        private final Exception exception;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception exception) {
            this.parser = parser;
            this.exception = exception;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            System.err.println();
            System.err.println(exception.getMessage());
            showUsage(System.err, parser);
            executionListener.onFailure(exception);
        }
    }

    private static class ShowUsageAction implements Runnable {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        @Override
        public void run() {
            showUsage(System.out, parser);
        }
    }

    private static class ShowVersionAction implements Runnable {
        @Override
        public void run() {
            DefaultGradleVersion currentVersion = DefaultGradleVersion.current();

            final StringBuilder sb = new StringBuilder();
            sb.append("%n------------------------------------------------------------%nGradle ");
            sb.append(currentVersion.getVersion());
            sb.append("%n------------------------------------------------------------%n%nBuild time:   ");
            sb.append(currentVersion.getBuildTimestamp());
            sb.append("%nRevision:     ");
            sb.append(currentVersion.getGitRevision());
            sb.append("%n%nKotlin:       ");
            sb.append(KotlinDslVersion.current().getKotlinVersion());
            sb.append("%nGroovy:       ");
            sb.append(ReleaseInfo.getVersion());
            sb.append("%nAnt:          ");
            sb.append(Main.getAntVersion());
            sb.append("%nJVM:          ");
            sb.append(Jvm.current());
            sb.append("%nOS:           ");
            sb.append(OperatingSystem.current());
            sb.append("%n");

            System.out.println(String.format(sb.toString()));
        }
    }

    private static class WithLogging implements CommandLineExecution {
        private final ServiceRegistry loggingServices;
        private final List<String> args;
        private final LoggingConfiguration loggingConfiguration;
        private final Action<ExecutionListener> action;
        private final Action<Throwable> reporter;

        WithLogging(ServiceRegistry loggingServices, List<String> args, LoggingConfiguration loggingConfiguration, Action<ExecutionListener> action, Action<Throwable> reporter) {
            this.loggingServices = loggingServices;
            this.args = args;
            this.loggingConfiguration = loggingConfiguration;
            this.action = action;
            this.reporter = reporter;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            BuildOptionBackedConverter<LoggingConfiguration> loggingBuildOptions = new BuildOptionBackedConverter<>(new LoggingConfigurationBuildOptions());
            InitialPropertiesConverter propertiesConverter = new InitialPropertiesConverter();
            BuildLayoutConverter buildLayoutConverter = new BuildLayoutConverter();
            LayoutToPropertiesConverter layoutToPropertiesConverter = new LayoutToPropertiesConverter(new BuildLayoutFactory());

            BuildLayoutResult buildLayout = buildLayoutConverter.defaultValues();

            CommandLineParser parser = new CommandLineParser();
            propertiesConverter.configure(parser);
            buildLayoutConverter.configure(parser);
            loggingBuildOptions.configure(parser);

            parser.allowUnknownOptions();
            parser.allowMixedSubcommandsAndOptions();

            try {
                ParsedCommandLine parsedCommandLine = parser.parse(args);
                InitialProperties initialProperties = propertiesConverter.convert(parsedCommandLine);

                // Calculate build layout, for loading properties and other logging configuration
                buildLayout = buildLayoutConverter.convert(initialProperties, parsedCommandLine, null);

                // Read *.properties files
                AllProperties properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout);

                // Calculate the logging configuration
                loggingBuildOptions.convert(parsedCommandLine, properties, loggingConfiguration);
            } catch (CommandLineArgumentException e) {
                // Ignore, deal with this problem later
            }

            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevelInternal(loggingConfiguration.getLogLevel());
            loggingManager.start();
            try {
                Action<ExecutionListener> exceptionReportingAction =
                    new ExceptionReportingAction(reporter, loggingManager,
                        new NativeServicesInitializingAction(buildLayout, loggingConfiguration, loggingManager,
                            new WelcomeMessageAction(buildLayout,
                                new DebugLoggerWarningAction(loggingConfiguration, action))));
                exceptionReportingAction.execute(executionListener);
            } finally {
                loggingManager.stop();
            }
        }
    }

    private class ParseAndBuildAction implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;

        private ParseAndBuildAction(ServiceRegistry loggingServices, List<String> args) {
            this.loggingServices = loggingServices;
            this.args = args;
        }

        @Override
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
