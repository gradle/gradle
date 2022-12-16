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
import com.google.common.collect.ImmutableList;
import org.apache.tools.ant.Main;
import org.codehaus.groovy.util.ReleaseInfo;
import org.gradle.api.Action;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
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
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.util.internal.DefaultGradleVersion;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Responsible for executing a set of command-line arguments.</p>
 */
public class DefaultCommandLineActionFactory implements CommandLineActionFactory {
    public static final String WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY = "org.gradle.internal.launcher.welcomeMessageEnabled";
    private static final String HELP = "h";
    private static final String VERSION = "v";
    private static final String VERSION_CONTINUE = "V";

    private final LoggingServiceRegistry loggingServices;
    private final List<CommandLineActionCreator> actionCreators;

    public DefaultCommandLineActionFactory() {
        this(LoggingServiceRegistry.newCommandLineProcessLogging());
    }

    private DefaultCommandLineActionFactory(LoggingServiceRegistry loggingServices) {
        this(loggingServices, new BuildActionsFactory(loggingServices));
    }

    /**
     * Allows tests to inject a mock build action factory to avoid attempting to execute actual builds.
     */
    @VisibleForTesting
    public DefaultCommandLineActionFactory(LoggingServiceRegistry loggingServices, CommandLineActionCreator buildActionsFactory) {
        this.loggingServices = loggingServices;
        this.actionCreators = ImmutableList.of(new BuiltInActionCreator(), new ContinuingActionCreator(), buildActionsFactory);
    }

    /**
     * Creates an {@link Action} which executes the commands specified by the provided arguments and
     * reports execution events to the provided listener.
     *
     * @param args The arguments describing the command to run.
     */
    @Override
    public Action<ExecutionListener> convert(List<String> args) {
        Action<ExecutionListener> action = new ParseAndBuildAction(args, actionCreators);
        return new WithLogging(loggingServices, args, new DefaultLoggingConfiguration(), action);
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
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

    private static class BuiltInActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info and exit.");
        }

        @Override
        @Nullable
        public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(HELP)) {
                return listener -> showHelp(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return DefaultCommandLineActionFactory::showVersion;
            }
            return null;
        }
    }

    /**
     * This {@link CommandLineActionCreator} is responsible for handling any command line options that produce {@link ContinuingAction}s.
     */
    private static class ContinuingActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(VERSION_CONTINUE, "show-version").hasDescription("Print version info and continue.");
        }

        @Override
        @Nullable
        public ContinuingAction<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(DefaultCommandLineActionFactory.VERSION_CONTINUE)) {
                return DefaultCommandLineActionFactory::showVersion;
            }
            return null;
        }
    }

    private static void showCommandLineParseFailure(Exception exception, CommandLineParser parser) {
        System.err.println();
        System.err.println(exception.getMessage());
        showUsage(System.err, parser);
    }

    private static void showHelp(CommandLineParser parser) {
        System.out.println();
        System.out.print("To see help contextual to the project, use ");
        clientMetaData().describeCommand(System.out, "help");
        System.out.println();
        showUsage(System.out, parser);
    }

    private static void showVersion(ExecutionListener executionListener) {
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

    /**
     * An {@link Action} which executes the actions generated by list of provided {@link CommandLineActionCreator}s.
     *
     * The provided {@link CommandLineActionCreator}s each attempt to create an {@link Action} from the given CLI args.
     * After processing each created action, subsequent actions are only executed if the processed action is a {@link ContinuingAction}.
     */
    private static class ParseAndBuildAction implements Action<ExecutionListener> {
        private final List<String> args;
        private final List<CommandLineActionCreator> actionCreators;

        private ParseAndBuildAction(List<String> args, List<CommandLineActionCreator> actionCreators) {
            this.args = args;
            this.actionCreators = actionCreators;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            CommandLineParser parser = new CommandLineParser();
            actionCreators.forEach(creator -> creator.configureCommandLineParser(parser));

            try {
                ParsedCommandLine commandLine = parser.parse(args);
                combineActions(parser, commandLine, actionCreators).execute(executionListener);
            } catch (CommandLineArgumentException e) {
                showCommandLineParseFailure(e, parser);
                executionListener.onFailure(e);
            }
        }

        private static Action<? super ExecutionListener> combineActions(
            CommandLineParser parser, ParsedCommandLine commandLine, List<CommandLineActionCreator> actionCreators
        ) {
            List<Action<? super ExecutionListener>> actions = new ArrayList<>(2);
            for (CommandLineActionCreator actionCreator : actionCreators) {
                Action<? super ExecutionListener> action = actionCreator.createAction(parser, commandLine);
                if (action != null) {
                    actions.add(action);
                    if (!(action instanceof ContinuingAction)) {
                        break;
                    }
                }
            }

            if (!actions.isEmpty()) {
                return Actions.composite(actions);
            }

            throw new UnsupportedOperationException("No action factory for specified command-line arguments.");
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

        @Override
        public void execute(ExecutionListener executionListener) {
            final Action<Throwable> reporter = new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData());

            BuildOptionBackedConverter<WelcomeMessageConfiguration> welcomeMessageConverter = new BuildOptionBackedConverter<>(new WelcomeMessageBuildOptions());
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

            WelcomeMessageConfiguration welcomeMessageConfiguration = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE);

            try {
                ParsedCommandLine parsedCommandLine = parser.parse(args);
                InitialProperties initialProperties = propertiesConverter.convert(parsedCommandLine);

                // Calculate build layout, for loading properties and other logging configuration
                buildLayout = buildLayoutConverter.convert(initialProperties, parsedCommandLine, null);

                // Read *.properties files
                AllProperties properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout);

                // Calculate the logging configuration
                loggingBuildOptions.convert(parsedCommandLine, properties, loggingConfiguration);

                // Get configuration for showing the welcome message
                welcomeMessageConverter.convert(parsedCommandLine, properties, welcomeMessageConfiguration);
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
                            new WelcomeMessageAction(buildLayout, welcomeMessageConfiguration,
                                new DebugLoggerWarningAction(loggingConfiguration, action))));

                exceptionReportingAction.execute(executionListener);
            } finally {
                loggingManager.stop();
            }
        }
    }
}
