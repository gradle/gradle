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

package org.gradle.logging;

import org.gradle.internal.Actions;
import org.gradle.cli.CommandLineConverter;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.logging.internal.logback.LogbackLoggingConfigurer;

/**
 * A {@link org.gradle.internal.service.ServiceRegistry} implementation that provides the logging services. To use this:
 *
 * <ol>
 *     <li>Create an instance using one of the static factory methods below.</li>
 *     <li>Create an instance of {@link LoggingManagerInternal}.</li>
 *     <li>Configure the logging manager as appropriate.</li>
 *     <li>Start the logging manager using {@link org.gradle.logging.LoggingManagerInternal#start()}.</li>
 *     <li>When finished, stop the logging manager using {@link LoggingManagerInternal#stop()}.</li>
 * </ol>
 */
public abstract class LoggingServiceRegistry extends DefaultServiceRegistry {
    private TextStreamOutputEventListener stdoutListener;

    /**
     * Creates a set of logging services which are suitable to use in a command-line process. In particular:
     *
     * <ul>
     *     <li>Replaces System.out and System.err with implementations that route output through the logging system.</li>
     *     <li>Configures slf4j, logback, log4j and java util logging to route log messages through the logging system.</li>
     *     <li>Routes logging output to the original System.out and System.err.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     *
     * <p>Allows dynamic and colored output to be written to the console. Use {@link LoggingManagerInternal#attachConsole(boolean)} to enable this.</p>
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        return new CommandLineLogging();
    }

    /**
     * Creates a set of logging services which are suitable to use globally in a process. In particular:
     *
     * <ul>
     *     <li>Replaces System.out and System.err with implementations that route output through the logging system.</li>
     *     <li>Configures slf4j, logback, log4j and java util logging to route log messages through the logging system.</li>
     *     <li>Routes logging output to the original System.out and System.err.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newProcessLogging() {
        return new ChildProcessLogging();
    }

    /**
     * Creates a set of logging services which are suitable to use embedded in another application. In particular:
     *
     * <ul>
     *     <li>Routes logging output to System.out and System.err.</li>
     *     <li>Configures slf4j and logback.</li>
     * </ul>
     *
     * <p>Does not:</p>
     *
     * <ul>
     *     <li>Replace System.out and System.err to capture output written to these destinations.</li>
     *     <li>Configure log4j or java util logging.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newEmbeddableLogging() {
        return new EmbeddedLogging();
    }

    /**
     * Creates a set of logging services to set up a new logging scope. Does not configure any static state.
     */
    public LoggingServiceRegistry newLogging() {
        return new NestedLogging();
    }

    protected CommandLineConverter<LoggingConfiguration> createCommandLineConverter() {
        return new LoggingCommandLineConverter();
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(getStdoutListener(), get(TimeProvider.class));
    }

    protected TextStreamOutputEventListener getStdoutListener() {
        if (stdoutListener == null) {
            stdoutListener = new TextStreamOutputEventListener(get(OutputEventListener.class));
        }
        return stdoutListener;
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected abstract Factory<LoggingManagerInternal> createLoggingManagerFactory();

    protected OutputEventRenderer createOutputEventRenderer() {
        OutputEventRenderer renderer = new OutputEventRenderer(Actions.doNothing());
        renderer.addStandardOutputAndError();
        return renderer;
    }

    private static class ChildProcessLogging extends LoggingServiceRegistry {
        protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
            OutputEventRenderer renderer = get(OutputEventRenderer.class);
            // Configure logback and java util logging, and capture stdout and stderr
            LoggingSystem stdout = new DefaultStdOutLoggingSystem(getStdoutListener(), get(TimeProvider.class));
            LoggingSystem stderr = new DefaultStdErrLoggingSystem(new TextStreamOutputEventListener(get(OutputEventListener.class)), get(TimeProvider.class));
            return new DefaultLoggingManagerFactory(
                    new DefaultLoggingConfigurer(renderer,
                            new LogbackLoggingConfigurer(renderer),
                            new JavaUtilLoggingConfigurer()),
                    renderer,
                    stdout,
                    stderr);
        }
    }

    private static class CommandLineLogging extends ChildProcessLogging {
        protected OutputEventRenderer createOutputEventRenderer() {
            OutputEventRenderer renderer = new OutputEventRenderer(new ConsoleConfigureAction());
            renderer.addStandardOutputAndError();
            return renderer;
        }
    }

    private static class EmbeddedLogging extends LoggingServiceRegistry {
        protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
            OutputEventRenderer renderer = get(OutputEventRenderer.class);
            // Configure logback only
            return new DefaultLoggingManagerFactory(
                    new DefaultLoggingConfigurer(renderer,
                            new LogbackLoggingConfigurer(renderer)),
                    renderer,
                    new NoOpLoggingSystem(),
                    new NoOpLoggingSystem());
        }
    }

    private static class NestedLogging extends LoggingServiceRegistry {
        protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
            OutputEventRenderer renderer = get(OutputEventRenderer.class);
            // Don't configure anything
            return new DefaultLoggingManagerFactory(renderer,
                    renderer,
                    new NoOpLoggingSystem(),
                    new NoOpLoggingSystem());
        }
    }
}
