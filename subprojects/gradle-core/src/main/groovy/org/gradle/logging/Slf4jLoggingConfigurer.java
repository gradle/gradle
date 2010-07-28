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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.fusesource.jansi.AnsiConsole;
import org.gradle.api.logging.*;
import org.gradle.api.specs.Spec;
import org.gradle.listener.ListenerBroadcast;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.PrintStream;

/**
 * @author Hans Dockter
 */
public class Slf4jLoggingConfigurer implements LoggingConfigurer, LoggingOutput, TerminalLoggingConfigurer {
    private final LoggingDestination stdout = new LoggingDestination();
    private final LoggingDestination stderr = new LoggingDestination();
    private final Appender errorAppender = new Appender();
    private final Appender infoAppender = new Appender();
    private final Spec<FileDescriptor> terminalDetector;
    private LogEventFormatter consoleFormatter;
    private LogEventFormatter nonConsoleFormatter;
    private LogLevel currentLevel;
    private final PrintStream defaultStdOut;

    public Slf4jLoggingConfigurer() {
        this(new TerminalDetector());
    }

    Slf4jLoggingConfigurer(Spec<FileDescriptor> terminalDetector) {
        this.terminalDetector = terminalDetector;
        defaultStdOut = System.out;
    }

    Console createConsole() {
        if (stdout.terminal) {
            PrintStream target = System.out;
            return new org.gradle.logging.AnsiConsole(target, target);
        }
        if (stderr.terminal) {
            PrintStream target = System.err;
            return new org.gradle.logging.AnsiConsole(target, target);
        }
        return null;
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
        stderr.addListener(listener);
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        stderr.removeListener(listener);
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        stdout.addListener(listener);
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        stdout.removeListener(listener);
    }

    @Override
    public void configure(boolean stdOutIsTerminal, boolean stdErrIsTerminal) {
        stdout.terminal = stdOutIsTerminal;
        stderr.terminal = stdErrIsTerminal;
        doConfigure(true);
    }

    public void configure(LogLevel logLevel) {
        if (currentLevel == logLevel) {
            return;
        }
        boolean init = currentLevel == null;
        currentLevel = logLevel;
        doConfigure(init);
    }

    private void doConfigure(boolean init) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger;
        if (init) {
            lc.reset();

            stdout.init(System.out);
            stderr.init(System.err);

            Console console = createConsole();
            consoleFormatter = console == null ? null : new ConsoleBackedFormatter(lc, console);
            nonConsoleFormatter = new BasicProgressLoggingAwareFormatter(lc, stdout.getBroadcast(),
                    stderr.getBroadcast());

            errorAppender.setContext(lc);
            infoAppender.setContext(lc);

            rootLogger = lc.getLogger("ROOT");
            rootLogger.addAppender(infoAppender);
            rootLogger.addAppender(errorAppender);
        } else {
            rootLogger = lc.getLogger("ROOT");
        }

        errorAppender.stop();
        infoAppender.stop();
        errorAppender.clearAllFilters();
        infoAppender.clearAllFilters();

        errorAppender.addFilter(createLevelFilter(lc, Level.ERROR, FilterReply.ACCEPT, FilterReply.DENY));
        Level level = Level.INFO;

        setLayouts(currentLevel, errorAppender, infoAppender, lc);

        MarkerFilter quietFilter = new MarkerFilter(FilterReply.DENY, Logging.QUIET);
        infoAppender.addFilter(quietFilter);
        if (!(currentLevel == LogLevel.QUIET)) {
            quietFilter.setOnMismatch(FilterReply.NEUTRAL);
            if (currentLevel == LogLevel.DEBUG) {
                level = Level.DEBUG;
                infoAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT, FilterReply.NEUTRAL));
                infoAppender.addFilter(createLevelFilter(lc, Level.DEBUG, FilterReply.ACCEPT, FilterReply.NEUTRAL));
            } else {
                if (currentLevel == LogLevel.INFO) {
                    level = Level.INFO;
                    infoAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT, FilterReply.NEUTRAL));
                } else {
                    infoAppender.addFilter(new MarkerFilter(Logging.LIFECYCLE, Logging.PROGRESS));
                }
            }
            infoAppender.addFilter(createLevelFilter(lc, Level.WARN, FilterReply.ACCEPT, FilterReply.DENY));
        }
        rootLogger.setLevel(level);
        infoAppender.start();
        errorAppender.start();
    }

    private void setLayouts(LogLevel logLevel, Appender errorAppender, Appender nonErrorAppender, LoggerContext lc) {
        nonErrorAppender.setFormatter(stdout.createFormatter(lc, logLevel));
        errorAppender.setFormatter(stderr.createFormatter(lc, logLevel));
    }

    private Filter<ILoggingEvent> createLevelFilter(LoggerContext lc, Level level, FilterReply onMatch,
                                                    FilterReply onMismatch) {
        LevelFilter levelFilter = new LevelFilter();
        levelFilter.setContext(lc);
        levelFilter.setOnMatch(onMatch);
        levelFilter.setOnMismatch(onMismatch);
        levelFilter.setLevel(level);
        levelFilter.start();
        return levelFilter;
    }

    private static class DebugLayout extends PatternLayout {
        private DebugLayout() {
            setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n%ex");
        }
    }

    private class LoggingDestination {
        private final ListenerBroadcast<StandardOutputListener> listeners
                = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
        private boolean terminal;
        private PrintStream target;

        private void init(PrintStream target) {
            this.target = target;
        }

        private LogEventFormatter createFormatter(LoggerContext loggerContext, LogLevel logLevel) {
            if (logLevel == LogLevel.DEBUG) {
                Layout<ILoggingEvent> layout = new DebugLayout();
                layout.setContext(loggerContext);
                layout.start();
                return new LayoutBasedFormatter(layout, getBroadcastWithTarget());
            }
            if (terminal) {
                return new LogEventFormatter() {
                    public void format(ILoggingEvent event) {
                        consoleFormatter.format(event);
                        nonConsoleFormatter.format(event);
                    }
                };
            } else {
                return nonConsoleFormatter;
            }
        }

        public void removeListener(StandardOutputListener listener) {
            listeners.remove(listener);
        }

        public void addListener(StandardOutputListener listener) {
            listeners.add(listener);
        }

        public StandardOutputListener getBroadcast() {
            if (terminal) {
                return listeners.getSource();
            } else {
                return getBroadcastWithTarget();
            }
        }

        private StandardOutputListener getBroadcastWithTarget() {
            final StandardOutputListener targetListener = new OutputStreamStandardOutputListenerAdapter(target);
            return new StandardOutputListener() {
                public void onOutput(CharSequence output) {
                    targetListener.onOutput(output);
                    listeners.getSource().onOutput(output);
                }
            };
        }
    }

    private class Appender extends AppenderBase<ILoggingEvent> {
        private LogEventFormatter formatter;

        public void setFormatter(LogEventFormatter formatter) {
            this.formatter = formatter;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                formatter.format(event);
            } catch (Throwable t) {
                // Give up and try stdout
                t.printStackTrace(defaultStdOut);
            }
        }
    }
}
