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

package org.gradle.api.logging;

import org.apache.tools.ant.Project;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>The main entry point for Gradle's logging system. Gradle routes all logging via SLF4J. You can use either an SLF4J
 * {@link org.slf4j.Logger} or a Gradle {@link Logger} to perform logging.</p>
 */
public class Logging {
    public static final Marker LIFECYCLE = MarkerFactory.getDetachedMarker("LIFECYCLE");
    public static final Marker QUIET = MarkerFactory.getDetachedMarker("QUIET");

    /**
     * Returns the logger for the given class.
     *
     * @param c the class.
     * @return the logger. Never returns null.
     */
    public static Logger getLogger(Class c) {
        return new LoggerImpl(LoggerFactory.getLogger(c));
    }

    /**
     * Returns the logger with the given name.
     *
     * @param name the logger name.
     * @return the logger. Never returns null.
     */
    public static Logger getLogger(String name) {
        return new LoggerImpl(LoggerFactory.getLogger(name));
    }

    public static final Map<Integer, LogLevel> ANT_IVY_2_SLF4J_LEVEL_MAPPER = new HashMap<Integer, LogLevel>() {
        {
            put(Project.MSG_ERR, LogLevel.ERROR);
            put(Project.MSG_WARN, LogLevel.WARN);
            put(Project.MSG_INFO, LogLevel.INFO);
            put(Project.MSG_DEBUG, LogLevel.DEBUG);
            put(Project.MSG_VERBOSE, LogLevel.DEBUG);
        }};

    private static class LoggerImpl implements Logger {
        private final org.slf4j.Logger logger;

        public LoggerImpl(org.slf4j.Logger logger) {
            this.logger = logger;
        }

        public boolean isEnabled(LogLevel level) {
            return level.isEnabled(this);
        }

        public void log(LogLevel level, String message) {
            level.log(this, message);
        }

        public void log(LogLevel level, String message, Object... objects) {
            level.log(this, message, objects);
        }

        public void log(LogLevel level, String message, Throwable throwable) {
            level.log(this, message, throwable);
        }

        public boolean isLifecycleEnabled() {
            return logger.isInfoEnabled(LIFECYCLE);
        }

        public void lifecycle(String message) {
            logger.info(LIFECYCLE, message);
        }

        public void lifecycle(String message, Object... objects) {
            logger.info(LIFECYCLE, message, objects);
        }

        public void lifecycle(String message, Throwable throwable) {
            logger.info(LIFECYCLE, message, throwable);
        }

        public boolean isQuietEnabled() {
            return logger.isInfoEnabled(QUIET);
        }

        public void quiet(String message) {
            logger.info(QUIET, message);
        }

        public void quiet(String message, Object... objects) {
            logger.info(QUIET, message, objects);
        }

        public void quiet(String message, Throwable throwable) {
            logger.info(QUIET, message, throwable);
        }

        public void debug(Marker marker, String s) {
            logger.debug(marker, s);
        }

        public void debug(Marker marker, String s, Object o) {
            logger.debug(marker, s, o);
        }

        public void debug(Marker marker, String s, Object o, Object o1) {
            logger.debug(marker, s, o, o1);
        }

        public void debug(Marker marker, String s, Object[] objects) {
            logger.debug(marker, s, objects);
        }

        public void debug(Marker marker, String s, Throwable throwable) {
            logger.debug(marker, s, throwable);
        }

        public void debug(String s) {
            logger.debug(s);
        }

        public void debug(String s, Object o) {
            logger.debug(s, o);
        }

        public void debug(String s, Object o, Object o1) {
            logger.debug(s, o, o1);
        }

        public void debug(String s, Object[] objects) {
            logger.debug(s, objects);
        }

        public void debug(String s, Throwable throwable) {
            logger.debug(s, throwable);
        }

        public void error(Marker marker, String s) {
            logger.error(marker, s);
        }

        public void error(Marker marker, String s, Object o) {
            logger.error(marker, s, o);
        }

        public void error(Marker marker, String s, Object o, Object o1) {
            logger.error(marker, s, o, o1);
        }

        public void error(Marker marker, String s, Object[] objects) {
            logger.error(marker, s, objects);
        }

        public void error(Marker marker, String s, Throwable throwable) {
            logger.error(marker, s, throwable);
        }

        public void error(String s) {
            logger.error(s);
        }

        public void error(String s, Object o) {
            logger.error(s, o);
        }

        public void error(String s, Object o, Object o1) {
            logger.error(s, o, o1);
        }

        public void error(String s, Object[] objects) {
            logger.error(s, objects);
        }

        public void error(String s, Throwable throwable) {
            logger.error(s, throwable);
        }

        public String getName() {
            return logger.getName();
        }

        public void info(Marker marker, String s) {
            logger.info(marker, s);
        }

        public void info(Marker marker, String s, Object o) {
            logger.info(marker, s, o);
        }

        public void info(Marker marker, String s, Object o, Object o1) {
            logger.info(marker, s, o, o1);
        }

        public void info(Marker marker, String s, Object[] objects) {
            logger.info(marker, s, objects);
        }

        public void info(Marker marker, String s, Throwable throwable) {
            logger.info(marker, s, throwable);
        }

        public void info(String s) {
            logger.info(s);
        }

        public void info(String s, Object o) {
            logger.info(s, o);
        }

        public void info(String s, Object o, Object o1) {
            logger.info(s, o, o1);
        }

        public void info(String s, Object[] objects) {
            logger.info(s, objects);
        }

        public void info(String s, Throwable throwable) {
            logger.info(s, throwable);
        }

        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        public boolean isDebugEnabled(Marker marker) {
            return logger.isDebugEnabled(marker);
        }

        public boolean isErrorEnabled() {
            return logger.isErrorEnabled();
        }

        public boolean isErrorEnabled(Marker marker) {
            return logger.isErrorEnabled(marker);
        }

        public boolean isInfoEnabled() {
            return logger.isInfoEnabled();
        }

        public boolean isInfoEnabled(Marker marker) {
            return logger.isInfoEnabled(marker);
        }

        public boolean isTraceEnabled() {
            return logger.isTraceEnabled();
        }

        public boolean isTraceEnabled(Marker marker) {
            return logger.isTraceEnabled(marker);
        }

        public boolean isWarnEnabled() {
            return logger.isWarnEnabled();
        }

        public boolean isWarnEnabled(Marker marker) {
            return logger.isWarnEnabled(marker);
        }

        public void trace(Marker marker, String s) {
            logger.trace(marker, s);
        }

        public void trace(Marker marker, String s, Object o) {
            logger.trace(marker, s, o);
        }

        public void trace(Marker marker, String s, Object o, Object o1) {
            logger.trace(marker, s, o, o1);
        }

        public void trace(Marker marker, String s, Object[] objects) {
            logger.trace(marker, s, objects);
        }

        public void trace(Marker marker, String s, Throwable throwable) {
            logger.trace(marker, s, throwable);
        }

        public void trace(String s) {
            logger.trace(s);
        }

        public void trace(String s, Object o) {
            logger.trace(s, o);
        }

        public void trace(String s, Object o, Object o1) {
            logger.trace(s, o, o1);
        }

        public void trace(String s, Object[] objects) {
            logger.trace(s, objects);
        }

        public void trace(String s, Throwable throwable) {
            logger.trace(s, throwable);
        }

        public void warn(Marker marker, String s) {
            logger.warn(marker, s);
        }

        public void warn(Marker marker, String s, Object o) {
            logger.warn(marker, s, o);
        }

        public void warn(Marker marker, String s, Object o, Object o1) {
            logger.warn(marker, s, o, o1);
        }

        public void warn(Marker marker, String s, Object[] objects) {
            logger.warn(marker, s, objects);
        }

        public void warn(Marker marker, String s, Throwable throwable) {
            logger.warn(marker, s, throwable);
        }

        public void warn(String s) {
            logger.warn(s);
        }

        public void warn(String s, Object o) {
            logger.warn(s, o);
        }

        public void warn(String s, Object o, Object o1) {
            logger.warn(s, o, o1);
        }

        public void warn(String s, Object[] objects) {
            logger.warn(s, objects);
        }

        public void warn(String s, Throwable throwable) {
            logger.warn(s, throwable);
        }
    }
}
