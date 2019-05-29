/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.logging.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class DefaultContextAwareTaskLogger implements ContextAwareTaskLogger {

    private BuildOperationAwareLogger delegate;
    private OperationIdentifier fallbackOperationIdentifier = null;

    public DefaultContextAwareTaskLogger(Logger delegate) {
        this.delegate = Cast.cast(BuildOperationAwareLogger.class, delegate);
    }

    @Override
    public void setFallbackBuildOperationId(OperationIdentifier operationIdentifier) {
        this.fallbackOperationIdentifier = operationIdentifier;
    }

    @Override
    public boolean isLifecycleEnabled() {
        return delegate.isLifecycleEnabled();
    }

    @Override
    public boolean isQuietEnabled() {
        return delegate.isQuietEnabled();
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return delegate.isEnabled(level);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    @Override
    public void trace(String msg) {
    }

    @Override
    public void trace(String format, Object arg) {
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
    }

    @Override
    public void trace(String format, Object... arguments) {
    }

    @Override
    public void trace(String msg, Throwable t) {
    }

    @Override
    public void trace(Marker marker, String msg) {
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
    }

    private void log(LogLevel logLevel, Throwable throwable, String message) {
        OperationIdentifier buildOperationId = CurrentBuildOperationRef.instance().getId();
        if (buildOperationId == null) {
            buildOperationId = fallbackOperationIdentifier;
        }
        delegate.log(logLevel, throwable, message, buildOperationId);
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object arg) {
        log(logLevel, throwable, format, new Object[]{arg});
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object arg1, Object arg2) {
        log(logLevel, throwable, format, new Object[]{arg1, arg2});
    }

    private void log(LogLevel logLevel, Throwable throwable, String format, Object[] args) {
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, args);
        Throwable loggedThrowable = throwable == null ? tuple.getThrowable() : throwable;

        log(logLevel, loggedThrowable, tuple.getMessage());
    }

    @Override
    public void debug(String message) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, message);
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg);
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arg1, arg2);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, null, format, arguments);
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (isDebugEnabled()) {
            log(LogLevel.DEBUG, t, msg);
        }
    }

    @Override
    public void debug(Marker marker, String msg) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, msg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, arg1, arg2);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object... argArray) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, null, format, argArray);
        }
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        if (isDebugEnabled(marker)) {
            log(LogLevel.DEBUG, t, msg);
        }
    }

    @Override
    public void info(String message) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, message);
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg);
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arg1, arg2);
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnabled()) {
            log(LogLevel.INFO, null, format, arguments);
        }
    }

    @Override
    public void lifecycle(String message) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, null, message);
        }
    }

    @Override
    public void lifecycle(String message, Object... objects) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, null, message, objects);
        }
    }

    @Override
    public void lifecycle(String message, Throwable throwable) {
        if (isLifecycleEnabled()) {
            log(LogLevel.LIFECYCLE, throwable, message);
        }
    }


    @Override
    public void quiet(String message) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, null, message);
        }
    }

    @Override
    public void quiet(String message, Object... objects) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, null, message, objects);
        }
    }

    @Override
    public void quiet(String message, Throwable throwable) {
        if (isQuietEnabled()) {
            log(LogLevel.QUIET, throwable, message);
        }
    }

    @Override
    public void log(LogLevel level, String message) {
        if (isEnabled(level)) {
            log(level, null, message);
        }
    }

    @Override
    public void log(LogLevel level, String message, Object... objects) {
        if (isEnabled(level)) {
            log(level, null, message, objects);
        }
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        if (isEnabled(level)) {
            log(level, throwable, message);
        }
    }

    @Override
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

    @Override
    public void info(Marker marker, String msg) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, msg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, arg1, arg2);
        }
    }

    @Override
    public void info(Marker marker, String format, Object... argArray) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), null, format, argArray);
        }
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        if (isInfoEnabled(marker)) {
            log(toLogLevel(marker), t, msg);
        }
    }

    @Override
    public void warn(String message) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, message);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg);
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arg1, arg2);
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, null, format, arguments);
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (isWarnEnabled()) {
            log(LogLevel.WARN, t, msg);
        }
    }

    @Override
    public void warn(Marker marker, String msg) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, msg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, arg1, arg2);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object... argArray) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, null, format, argArray);
        }
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        if (isWarnEnabled(marker)) {
            log(LogLevel.WARN, t, msg);
        }
    }

    @Override
    public void error(String message) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, message);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg);
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arg1, arg2);
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, null, format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (isErrorEnabled()) {
            log(LogLevel.ERROR, t, msg);
        }
    }

    @Override
    public void error(Marker marker, String msg) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, msg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, arg1, arg2);
        }
    }

    @Override
    public void error(Marker marker, String format, Object... argArray) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, null, format, argArray);
        }
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        if (isErrorEnabled(marker)) {
            log(LogLevel.ERROR, t, msg);
        }
    }

}
