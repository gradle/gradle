/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.logging.internal.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

import java.util.LinkedList;
import java.util.List;

public class OutputEventListenerBackedLogger implements Logger {

    private final String name;
    private final OutputEventListenerBackedLogger parent;
    private final OutputEventListenerBackedLoggerContext context;

    private List<OutputEventListenerBackedLogger> childrenList;

    private LogLevel level;
    private LogLevel effectiveLevel;
    private boolean disabled;

    public OutputEventListenerBackedLogger(String name, OutputEventListenerBackedLogger parent, OutputEventListenerBackedLoggerContext context) {
        this.name = name;
        this.parent = parent;
        this.context = context;
    }

    public String getName() {
        return name;
    }

    public synchronized void setLevel(LogLevel newLevel) {
        if (level == newLevel) {
            return;
        }
        if (newLevel == null && isRoot()) {
            throw new IllegalArgumentException("The level of the root logger cannot be set to null");
        }
        level = newLevel;
        effectiveLevel = newLevel == null ? parent.effectiveLevel : newLevel;
        informChildrenAboutNewLevel(effectiveLevel);
    }

    public synchronized void disable() {
        this.disabled = true;
        if (childrenList != null) {
            for (OutputEventListenerBackedLogger child : childrenList) {
                child.disable();
            }
        }
    }

    private boolean isRoot() {
        return parent == null;
    }

    private void informChildrenAboutNewLevel(LogLevel level) {
        if (childrenList != null) {
            for (OutputEventListenerBackedLogger child : childrenList) {
                child.parentLevelChanged(level);
            }
        }
    }

    private synchronized void parentLevelChanged(LogLevel newLevel) {
        if (level == null) {
            effectiveLevel = newLevel;
            informChildrenAboutNewLevel(newLevel);
        }
    }

    public LogLevel getEffectiveLevel() {
        return effectiveLevel;
    }

    public boolean isDisabled() {
        return disabled;
    }

    OutputEventListenerBackedLogger getChildByName(String name) {
        if (childrenList == null) {
            return null;
        } else {
            for (OutputEventListenerBackedLogger child : childrenList) {
                if (child.name.equals(name)) {
                    return child;
                }
            }
            return null;
        }
    }

    OutputEventListenerBackedLogger createChildByName(final String childName) {
        if (childrenList == null) {
            childrenList = new LinkedList<OutputEventListenerBackedLogger>();
        }
        OutputEventListenerBackedLogger childLogger = new OutputEventListenerBackedLogger(childName, this, context);
        childLogger.effectiveLevel = effectiveLevel;
        childLogger.disabled = disabled;
        childrenList.add(childLogger);
        return childLogger;
    }

    static int getSeparatorIndex(String name, int fromIndex) {
        int i = name.indexOf('.', fromIndex);
        if (i != -1) {
            return i;
        } else {
            return name.indexOf('$', fromIndex);
        }
    }

    private boolean isNotDisabledAndLevelIsAtMost(LogLevel levelLimit) {
        return !disabled && levelLimit.compareTo(effectiveLevel) >= 0;
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    public boolean isDebugEnabled() {
        return isNotDisabledAndLevelIsAtMost(LogLevel.DEBUG);
    }

    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return isNotDisabledAndLevelIsAtMost(LogLevel.INFO);
    }

    public boolean isInfoEnabled(Marker marker) {
        if (marker == Logging.LIFECYCLE) {
            return isNotDisabledAndLevelIsAtMost(LogLevel.LIFECYCLE);
        }
        if (marker == Logging.QUIET) {
            return isNotDisabledAndLevelIsAtMost(LogLevel.QUIET);
        }
        return isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return isNotDisabledAndLevelIsAtMost(LogLevel.WARN);
    }

    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    public boolean isErrorEnabled() {
        return !disabled;
    }

    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    public void trace(String msg) {
    }

    public void trace(String format, Object arg) {
    }

    public void trace(String format, Object arg1, Object arg2) {
    }

    public void trace(String format, Object... arguments) {
    }

    public void trace(String msg, Throwable t) {
    }

    public void trace(Marker marker, String msg) {
    }

    public void trace(Marker marker, String format, Object arg) {
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void trace(Marker marker, String format, Object... argArray) {
    }

    public void trace(Marker marker, String msg, Throwable t) {
    }

    private void log(LogLevel logLevel, Throwable throwable, String message) {
        LogEvent logEvent = new LogEvent(System.currentTimeMillis(), name, logLevel, message, throwable);
        OutputEventListener outputEventListener = context.getOutputEventListener();
        try {
            outputEventListener.onOutput(logEvent);
        } catch (Throwable e) {
            // fall back to standard out
            e.printStackTrace(System.out);
        }
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object arg) {
        log(logLevel, throwable, MessageFormatter.format(format, arg).getMessage());
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object arg1, Object arg2) {
        log(logLevel, throwable, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object[] args) {
        log(logLevel, throwable, MessageFormatter.arrayFormat(format, args).getMessage());
    }

    public void debug(String message) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, message);
        }
    }

    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg);
        }
    }

    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg1, arg2);
        }
    }

    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arguments);
        }
    }

    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, t, msg);
        }
    }

    public void debug(Marker marker, String msg) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, msg);
        }
    }

    public void debug(Marker marker, String format, Object arg) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg);
        }
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg1, arg2);
        }
    }

    public void debug(Marker marker, String format, Object... argArray) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, argArray);
        }
    }

    public void debug(Marker marker, String msg, Throwable t) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, t, msg);
        }
    }

    public void info(String message) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, message);
        }
    }

    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg);
        }
    }

    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg1, arg2);
        }
    }

    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arguments);
        }
    }

    public void info(String msg, Throwable t) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, t, msg);
        }
    }

    private LogLevel toLogLevel(Marker marker) {
        if (marker == null) {
            return LogLevel.INFO;
        }
        if (marker == Logging.LIFECYCLE) {
            return LogLevel.LIFECYCLE;
        }
        if (marker == Logging.QUIET) {
            return LogLevel.QUIET;
        }
        return LogLevel.INFO;
    }

    public void info(Marker marker, String msg) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, msg);
        }
    }

    public void info(Marker marker, String format, Object arg) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg);
        }
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg1, arg2);
        }
    }

    public void info(Marker marker, String format, Object... argArray) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, argArray);
        }
    }

    public void info(Marker marker, String msg, Throwable t) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), t, msg);
        }
    }

    public void warn(String message) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, message);
        }
    }

    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg);
        }
    }

    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg1, arg2);
        }
    }

    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arguments);
        }
    }

    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, t, msg);
        }
    }

    public void warn(Marker marker, String msg) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, msg);
        }
    }

    public void warn(Marker marker, String format, Object arg) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg);
        }
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg1, arg2);
        }
    }

    public void warn(Marker marker, String format, Object... argArray) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, argArray);
        }
    }

    public void warn(Marker marker, String msg, Throwable t) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, t, msg);
        }
    }

    public void error(String message) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, message);
        }
    }

    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg);
        }
    }

    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg1, arg2);
        }
    }

    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arguments);
        }
    }

    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, t, msg);
        }
    }

    public void error(Marker marker, String msg) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, msg);
        }
    }

    public void error(Marker marker, String format, Object arg) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg);
        }
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg1, arg2);
        }
    }

    public void error(Marker marker, String format, Object... argArray) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, argArray);
        }
    }

    public void error(Marker marker, String msg, Throwable t) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, t, msg);
        }
    }

    public void reset() {
        level = null;
        effectiveLevel = LogLevel.INFO;
        disabled = false;
        if (childrenList != null) {
            for (OutputEventListenerBackedLogger child : childrenList) {
                child.reset();
            }
        }
    }
}
