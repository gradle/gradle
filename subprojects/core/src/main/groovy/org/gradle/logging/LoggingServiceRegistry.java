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

import org.gradle.StartParameter;
import org.gradle.cli.CommandLineConverter;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.console.ConsoleMetaData;
import org.gradle.internal.console.FallbackConsoleMetaData;
import org.gradle.internal.nativeplatform.ConsoleDetector;
import org.gradle.internal.nativeplatform.NoOpConsoleDetector;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.logging.internal.logback.LogbackLoggingConfigurer;

/**
 * A {@link org.gradle.internal.service.ServiceRegistry} implementation which provides the logging services.
 */
public class LoggingServiceRegistry extends DefaultServiceRegistry {
    private enum Type {
        CommandLine, Child, Embedded, Nested
    }
    private TextStreamOutputEventListener stdoutListener;
    private final Type type;

    private LoggingServiceRegistry(Type type) {
        this.type = type;
    }

    /**
     * Creates a set of logging services which are suitable to use in a command-line process. In particular:
     *
     * <ul>
     *     <li>Replaces System.out and System.err with implementations that route output through the logging system.</li>
     *     <li>Configures slf4j, logback, log4j and java util logging to route log messages through the logging system.</li>
     *     <li>Routes logging output to the original System.out and System.err.</li>
     *     <li>Enables coloured and dynamic output when System.out or System.err are attached to a terminal.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        return new LoggingServiceRegistry(Type.CommandLine);
    }

    /**
     * Creates a set of logging services which are suitable to use in a child process. In particular:
     *
     * <ul>
     *     <li>Replaces System.out and System.err with implementations that route output through the logging system.</li>
     *     <li>Configures slf4j, logback, log4j and java util logging to route log messages through the logging system.</li>
     *     <li>Routes logging output to the original System.out and System.err.</li>
     * </ul>
     *
     * <p>Does not enable colours or dynamic output.</p>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newChildProcessLogging() {
        return new LoggingServiceRegistry(Type.Child);
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
        return new LoggingServiceRegistry(Type.Embedded);
    }

    /**
     * Creates a set of logging services to set up a new logging scope. Does not configure any static state.
     */
    public LoggingServiceRegistry newLogging() {
        return new LoggingServiceRegistry(Type.Nested);
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

    private TextStreamOutputEventListener getStdoutListener() {
        if (stdoutListener == null) {
            stdoutListener = new TextStreamOutputEventListener(get(OutputEventListener.class));
        }
        return stdoutListener;
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
        OutputEventRenderer renderer = get(OutputEventRenderer.class);
        switch (type) {
            case CommandLine:
            case Child:
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
            case Embedded:
                // Configure logback only
                return new DefaultLoggingManagerFactory(
                        new DefaultLoggingConfigurer(renderer,
                                new LogbackLoggingConfigurer(renderer)),
                        renderer,
                        new NoOpLoggingSystem(),
                        new NoOpLoggingSystem());
            case Nested:
                // Don't configure anything
                return new DefaultLoggingManagerFactory(renderer,
                        renderer,
                        new NoOpLoggingSystem(),
                        new NoOpLoggingSystem());
            default:
                throw new IllegalStateException();
        }
    }

    protected OutputEventRenderer createOutputEventRenderer() {
        ConsoleDetector consoleDetector;
        ConsoleMetaData consoleMetaData;
        if (type == Type.CommandLine) {
            StartParameter startParameter = new StartParameter();
            NativeServices.initialize(startParameter.getGradleUserHomeDir());
            consoleDetector = NativeServices.getInstance().get(ConsoleDetector.class);
            consoleMetaData = NativeServices.getInstance().get(ConsoleMetaData.class);
        } else {
            consoleDetector = new NoOpConsoleDetector();
            consoleMetaData = new FallbackConsoleMetaData();
        }
        OutputEventRenderer renderer = new OutputEventRenderer(consoleDetector, consoleMetaData);
        renderer.addStandardOutputAndError();
        return renderer;
    }
}
