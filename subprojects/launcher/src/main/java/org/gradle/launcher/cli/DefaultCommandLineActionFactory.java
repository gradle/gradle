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
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class DefaultCommandLineActionFactory implements CommandLineActionFactory {
    public static final String WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY = "org.gradle.internal.launcher.welcomeMessageEnabled";
    private static final String HELP = "h";
    private static final String VERSION = "v";
    private static final String VERSION_CONTINUE = "V";

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

    /**
     * This method is left visible so that tests can override it to inject {@link CommandLineActionCreator}s which
     * don't actually attempt to run the build per normally.
     *
     * @param loggingServices logging services to use when instantiating any {@link CommandLineActionCreator}s
     * @param actionCreators collection of {@link CommandLineActionCreator}s to which to add a new {@link BuildActionsFactory}
     */
    @VisibleForTesting
    protected void createBuildActionFactoryActionCreator(ServiceRegistry loggingServices, List<CommandLineActionCreator> actionCreators) {
        actionCreators.add(new BuildActionsFactory(loggingServices));
    }

    /**
     * This method is left visible so that tests can override it to inject mocked {@link ServiceRegistry}s.
     *
     * @return the created {@link ServiceRegistry}
     */
    @VisibleForTesting
    protected ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
    }

    private static class BuiltInActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info and exit.");
            parser.option(VERSION_CONTINUE, "show-version").hasDescription("Print version info and continue.");
        }

        @Override
        @Nullable
        public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(HELP)) {
                return new ShowUsageAction(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return new ShowVersionAction();
            }
            return null;
        }
    }

    /**
     * This {@link CommandLineActionCreator} is responsible for handling any command line options that produce {@link ContinuingAction}s.
     */
    private static class ContinuingActionCreator extends NonParserConfiguringCommandLineActionCreator {
        @Override
        @Nullable
        public ContinuingAction<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(DefaultCommandLineActionFactory.VERSION_CONTINUE)) {
                return (ContinuingAction<ExecutionListener>) executionListener -> new ShowVersionAction().execute(executionListener);
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

    private static class ShowUsageAction implements Action<ExecutionListener> {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            System.out.println();
            System.out.print("To see help contextual to the project, use ");
            clientMetaData().describeCommand(System.out, "help");
            System.out.println();
            showUsage(System.out, parser);
        }
    }

    public static class ShowVersionAction implements Action<ExecutionListener> {
        @Override
        public void execute(ExecutionListener executionListener) {
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

    /**
     * This {@link Action} is also a {@link CommandLineActionCreator} that, when run, will create new {@link Action}s that
     * will be immediately executed.
     *
     * This class accomplishes this be maintaining a list of {@link CommandLineActionCreator}s which can each attempt to
     * create an {@link Action} from the given CLI args, and handles the logic for deciding whether or not to continue processing
     * based on whether the result is a {@link ContinuingAction} or not.  It allows for injecting alternate Creators which
     * won't actually attempt to run a build via the containing class' {@link #createBuildActionFactoryActionCreator(ServiceRegistry, List)}
     * method - this is why this class is not {@code static}.
     */
    private class ParseAndBuildAction extends NonParserConfiguringCommandLineActionCreator implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;
        private List<CommandLineActionCreator> actionCreators;
        private CommandLineParser parser = new CommandLineParser();

        private ParseAndBuildAction(ServiceRegistry loggingServices, List<String> args) {
            this.loggingServices = loggingServices;
            this.args = args;

            actionCreators = new ArrayList<>();
            actionCreators.add(new BuiltInActionCreator());
            actionCreators.add(new ContinuingActionCreator());
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            // This must be added only during execute, because the actual constructor is called by various tests and this will not succeed if called then
            createBuildActionFactoryActionCreator(loggingServices, actionCreators);
            configureCreators();

            Action<? super ExecutionListener> action;
            try {
                ParsedCommandLine commandLine = parser.parse(args);
                action = createAction(parser, commandLine);
            } catch (CommandLineArgumentException e) {
                action = new CommandLineParseFailureAction(parser, e);
            }

            action.execute(executionListener);
        }

        private void configureCreators() {
            actionCreators.forEach(creator -> creator.configureCommandLineParser(parser));
        }

        @Override
        public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
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

    /**
     * Abstract type for any {@link CommandLineActionCreator} that does not make use of the {@link#configureCommandLineParser(CommandLineParser)}
     * method.
     */
    private static abstract class NonParserConfiguringCommandLineActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            // no-op
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
