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

package org.gradle.logging.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.util.UncheckedException;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
 * A {@link org.gradle.logging.internal.LoggingConfigurer} implementation which configures SLF4J to route logging
 * events to a {@link org.gradle.logging.internal.OutputEventListener}.
 *
 * @author Hans Dockter
 */
public class Slf4jLoggingConfigurer implements LoggingConfigurer {
    private final Appender appender;
    private LogLevel currentLevel;
    private final PrintStream defaultStdOut;

    public Slf4jLoggingConfigurer(OutputEventListener outputListener) {
        defaultStdOut = System.out;
        appender = new Appender(outputListener);
    }

    public void configure(LogLevel logLevel) {
        if (currentLevel == logLevel) {
            return;
        }

        try {
            doConfigure(logLevel);
        } catch (Throwable e) {
            doFailsafeConfiguration();
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private void doFailsafeConfiguration() {
        // Not really failsafe, just less likely to fail
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.reset();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<ILoggingEvent>() {{
            setContext(lc);
            setTarget("System.err");
            setLayout(new PatternLayout() {{
                setPattern("%msg%n%ex");
                setContext(lc);
                start();
            }});
            start();
        }};

        ch.qos.logback.classic.Logger rootLogger = lc.getLogger("ROOT");
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(consoleAppender);
    }

    private void doConfigure(LogLevel logLevel) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger;
        if (currentLevel == null) {
            lc.reset();
            appender.setContext(lc);
            rootLogger = lc.getLogger("ROOT");
            rootLogger.addAppender(appender);
        } else {
            rootLogger = lc.getLogger("ROOT");
        }

        currentLevel = logLevel;
        appender.stop();
        appender.clearAllFilters();

        switch (logLevel) {
            case DEBUG:
                rootLogger.setLevel(Level.DEBUG);
                break;
            case INFO:
                rootLogger.setLevel(Level.INFO);
                break;
            case LIFECYCLE:
                appender.addFilter(new MarkerFilter(Logging.QUIET, Logging.LIFECYCLE));
                appender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.DENY, FilterReply.NEUTRAL));
                rootLogger.setLevel(Level.INFO);
                break;
            case QUIET:
                appender.addFilter(new MarkerFilter(Logging.QUIET));
                appender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.DENY, FilterReply.NEUTRAL));
                rootLogger.setLevel(Level.INFO);
                break;
            case WARN:
                rootLogger.setLevel(Level.WARN);
                break;
            case ERROR:
                rootLogger.setLevel(Level.ERROR);
                break;
            default:
                throw new IllegalArgumentException();
        }

        appender.start();
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

    private class Appender extends AppenderBase<ILoggingEvent> {
        private final OutputEventListener listener;

        private Appender(OutputEventListener listener) {
            this.listener = listener;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
                Throwable throwable = throwableProxy == null ? null : throwableProxy.getThrowable();
                String message = event.getFormattedMessage();
                listener.onOutput(new LogEvent(event.getTimeStamp(), event.getLoggerName(), toLogLevel(event), message, throwable));
            } catch (Throwable t) {
                // Give up and try stdout
                t.printStackTrace(defaultStdOut);
            }
        }

        private LogLevel toLogLevel(ILoggingEvent event) {
            switch (event.getLevel().toInt()) {
                case Level.DEBUG_INT:
                    return LogLevel.DEBUG;
                case Level.INFO_INT:
                    if (event.getMarker() == Logging.LIFECYCLE) {
                        return LogLevel.LIFECYCLE;
                    }
                    if (event.getMarker() == Logging.QUIET) {
                        return LogLevel.QUIET;
                    }
                    return LogLevel.INFO;
                case Level.WARN_INT:
                    return LogLevel.WARN;
                case Level.ERROR_INT:
                    return LogLevel.ERROR;
            }
            throw new IllegalArgumentException(String.format("Cannot map SLF4j Level %s to a LogLevel", event.getLevel()));
        }
    }
}
