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
package org.gradle.initialization;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.StandardOutputLogging;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.logging.MarkerFilter;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.LogManager;

/**
 * @author Hans Dockter
 */
public class DefaultLoggingConfigurer implements LoggingConfigurer {
    private final Appender stderrConsoleAppender = new Appender();
    private final Appender stdoutConsoleAppender = new Appender();
    private boolean applied;

    public void addStandardErrorListener(StandardOutputListener listener) {
        stderrConsoleAppender.addListener(listener);
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        stderrConsoleAppender.removeListener(listener);
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        stdoutConsoleAppender.addListener(listener);
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        stdoutConsoleAppender.removeListener(listener);
    }

    public void configure(LogLevel logLevel) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger;
        if (!applied) {
            lc.reset();
            LogManager.getLogManager().reset();
            SLF4JBridgeHandler.install();
            stderrConsoleAppender.setContext(lc);
            stderrConsoleAppender.setTarget("System.err");
            stdoutConsoleAppender.setContext(lc);
            rootLogger = lc.getLogger("ROOT");
            rootLogger.addAppender(stdoutConsoleAppender);
            rootLogger.addAppender(stderrConsoleAppender);
        } else {
            rootLogger = lc.getLogger("ROOT");
        }

        applied = true;
        stderrConsoleAppender.stop();
        stdoutConsoleAppender.stop();
        stderrConsoleAppender.clearAllFilters();
        stdoutConsoleAppender.clearAllFilters();

        stderrConsoleAppender.addFilter(createLevelFilter(lc, Level.ERROR, FilterReply.ACCEPT, FilterReply.DENY));
        Level level = Level.INFO;

        setLayouts(logLevel, stderrConsoleAppender, stdoutConsoleAppender, lc);

        MarkerFilter quietFilter = new MarkerFilter(FilterReply.DENY, Logging.QUIET);
        stdoutConsoleAppender.addFilter(quietFilter);
        if (!(logLevel == LogLevel.QUIET)) {
            quietFilter.setOnMismatch(FilterReply.NEUTRAL);
            if (logLevel == LogLevel.DEBUG) {
                level = Level.DEBUG;
                stdoutConsoleAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT,
                        FilterReply.NEUTRAL));
                stdoutConsoleAppender.addFilter(createLevelFilter(lc, Level.DEBUG, FilterReply.ACCEPT,
                        FilterReply.NEUTRAL));
            } else {
                if (logLevel == LogLevel.INFO) {
                    level = Level.INFO;
                    stdoutConsoleAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT,
                            FilterReply.NEUTRAL));
                } else {
                    stdoutConsoleAppender.addFilter(new MarkerFilter(Logging.LIFECYCLE, Logging.PROGRESS));
                }
            }
            stdoutConsoleAppender.addFilter(createLevelFilter(lc, Level.WARN, FilterReply.ACCEPT, FilterReply.DENY));
        }
        rootLogger.setLevel(level);
        stdoutConsoleAppender.start();
        stderrConsoleAppender.start();
    }

    private void setLayouts(LogLevel logLevel, ConsoleAppender errorConsoleAppender,
                            ConsoleAppender nonErrorConsoleAppender, LoggerContext lc) {
        String debugLayout = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n%ex";
        String infoLayout = "%msg%n%ex";
        if (logLevel == LogLevel.DEBUG) {
            nonErrorConsoleAppender.setLayout(createPatternLayout(lc, debugLayout));
            errorConsoleAppender.setLayout(createPatternLayout(lc, debugLayout));
        } else {
            nonErrorConsoleAppender.setLayout(createPatternLayout(lc, infoLayout));
            errorConsoleAppender.setLayout(createPatternLayout(lc, infoLayout));
        }
    }

    private Filter createLevelFilter(LoggerContext lc, Level level, FilterReply onMatch, FilterReply onMismatch) {
        LevelFilter levelFilter = new LevelFilter();
        levelFilter.setContext(lc);
        levelFilter.setOnMatch(onMatch);
        levelFilter.setOnMismatch(onMismatch);
        levelFilter.setLevel(level.toString());
        levelFilter.start();
        return levelFilter;
    }

    private PatternLayout createPatternLayout(LoggerContext loggerContext, String pattern) {
        PatternLayout patternLayout = new Layout();
        patternLayout.setPattern(pattern);
        patternLayout.setContext(loggerContext);
        patternLayout.start();
        return patternLayout;
    }

    private static class Layout extends PatternLayout {
        @Override
        public String doLayout(ILoggingEvent loggingEvent) {
            if (loggingEvent.getMarker() == Logging.PROGRESS) {
                return loggingEvent.getFormattedMessage();
            }
            return super.doLayout(loggingEvent);
        }
    }

    private static class Appender extends ConsoleAppender<ILoggingEvent> {
        private final ListenerBroadcast<StandardOutputListener> listeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);

        public void removeListener(StandardOutputListener listener) {
            listeners.remove(listener);
        }

        public void addListener(StandardOutputListener listener) {
            listeners.add(listener);
        }

        @Override
        protected void append(ILoggingEvent event) {
            super.append(event);
            listeners.getSource().onOutput(layout.doLayout(event));
        }

        @Override
        public void start() {
            super.start();
            if (target.equals(SYSTEM_OUT)) {
                setWriter(createWriter(StandardOutputLogging.DEFAULT_OUT));
            } else {
                setWriter(createWriter(StandardOutputLogging.DEFAULT_ERR));
            }
        }
    }
}
