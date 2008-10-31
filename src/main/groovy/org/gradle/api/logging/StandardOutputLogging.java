/*
 * Copyright 2007-2008 the original author or authors.
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

import ch.qos.logback.classic.Level;

import java.io.PrintStream;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.LoggerFactory;
import org.gradle.logging.StandardOutputLoggingAdapter;
import org.gradle.api.logging.StandardOutputState;

/**
 * @author Hans Dockter
 */
public class StandardOutputLogging {
    private static Map<LogLevel, Level> logLevelToLevel = new HashMap() {{
        put(LogLevel.DEBUG, Level.DEBUG);
        put(LogLevel.INFO, Level.INFO);        
        put(LogLevel.WARN, Level.WARN);
        put(LogLevel.ERROR, Level.ERROR);
        put(LogLevel.LIFECYCLE, Level.INFO);
    }};
    static final StandardOutputLoggingAdapter OUT_LOGGING_ADAPTER = new StandardOutputLoggingAdapter(
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("Console out"), Level.INFO);

    static final StandardOutputLoggingAdapter ERR_LOGGING_ADAPTER = new StandardOutputLoggingAdapter(
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("Console out"), Level.ERROR);

    static final PrintStream outLoggingStream = new PrintStream(OUT_LOGGING_ADAPTER);
    static final PrintStream errLoggingStream = new PrintStream(ERR_LOGGING_ADAPTER);

    static final PrintStream defaultOut = System.out;
    static final PrintStream defaultErr = System.err;

    /**
     * Redirects the standard out to the Gradle logging.  The System.out is redirected to specified level.
     * System.err is always redirected to the ERROR level.
     *
     *  @param outLogLevel Log level for System.out
     */
    public static void on(LogLevel outLogLevel) {
        convert(OUT_LOGGING_ADAPTER, outLogLevel);
        convert(ERR_LOGGING_ADAPTER, LogLevel.ERROR);
        redirect(outLoggingStream, errLoggingStream);
    }

    /**
     * Redirects only System.out to the specified level. System.err is not redirected.
     *
     *  @param outLogLevel Log level for System.out
     */
    public static void onOut(LogLevel outLogLevel) {
        convert(OUT_LOGGING_ADAPTER, outLogLevel);
        System.setOut(outLoggingStream);
    }

    /**
     * Redirects only System.err to the specified level. System.out is not redirected.
     *
     *  @param errLogLevel Log level for System.err
     */
    public static void onErr(LogLevel errLogLevel) {
        convert(ERR_LOGGING_ADAPTER, errLogLevel);
        System.setErr(errLoggingStream);
    }

    /**
     * Sets System.err and System.out to the values they had before Gradle has been started.
     */
    public static void off() {
        redirect(defaultOut, defaultErr);
    }

    /**
     * Sets System.out to the values it had before Gradle has been started.
     */
    public static void offOut() {
        System.setOut(defaultOut);
    }

    /**
     * Sets System.err to the values it had before Gradle has been started.
     */
    public static void offErr() {
        System.setErr(defaultErr);
    }

    /**
     * Returns the current values for System.out and Sytem.err.
     */
    public static StandardOutputState getStateSnapshot() {
        return new StandardOutputState(System.out, System.err);
    }

    /**
     * Sets the values for System.out and Sytem.err.
     */
    public static void restoreState(StandardOutputState state) {
        redirect(state.getOutStream(), state.getErrStream());
    }

    private static void redirect(PrintStream outStream, PrintStream errStream) {
        System.setOut(outStream);
        System.setErr(errStream);
    }

    private static void convert(StandardOutputLoggingAdapter adapter, LogLevel logLevel) {
        if (logLevel == LogLevel.LIFECYCLE) {
            adapter.setMarker(Logging.LIFECYCLE);
        } else {
            adapter.setMarker(null);
        }
        adapter.setLevel(logLevelToLevel.get(logLevel));    
    }
}
