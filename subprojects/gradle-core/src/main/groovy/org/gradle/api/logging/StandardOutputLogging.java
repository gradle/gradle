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

import org.gradle.logging.StandardOutputLoggingAdapter;

import java.io.PrintStream;

/**
 * @author Hans Dockter
 */
public class StandardOutputLogging {
    public static class LoggingPrintStream extends PrintStream {
        private StandardOutputLoggingAdapter standardOutputLoggingAdapter;

        public LoggingPrintStream(StandardOutputLoggingAdapter out) {
            super(out);
            this.standardOutputLoggingAdapter = out;
        }

        public StandardOutputLoggingAdapter getStandardOutputLoggingAdapter() {
            return standardOutputLoggingAdapter;
        }
    }

    private static final ThreadLocal<LoggingPrintStream> OUT_LOGGING_STREAM = new ThreadLocal<LoggingPrintStream>() {
        @Override
        protected LoggingPrintStream initialValue() {
            return new LoggingPrintStream(new StandardOutputLoggingAdapter(Logging.getLogger("Console out"), LogLevel.INFO));
        }
    };

    private static final ThreadLocal<LoggingPrintStream> ERR_LOGGING_STREAM = new ThreadLocal<LoggingPrintStream>() {
        @Override
        protected LoggingPrintStream initialValue() {
            return new LoggingPrintStream(
                    new StandardOutputLoggingAdapter(Logging.getLogger("Console err"), LogLevel.ERROR));
        }
    };

    public static LoggingPrintStream getOut() {
        return OUT_LOGGING_STREAM.get();
    }

    public static LoggingPrintStream getErr() {
        return ERR_LOGGING_STREAM.get();
    }

    static StandardOutputLoggingAdapter getOutAdapter() {
        return getOut().getStandardOutputLoggingAdapter();
    }

    static StandardOutputLoggingAdapter getErrAdapter() {
        return getErr().getStandardOutputLoggingAdapter();
    }

    public static final PrintStream DEFAULT_OUT = System.out;
    public static final PrintStream DEFAULT_ERR = System.err;

    /**
     * Redirects the standard out to the Gradle logging.  The System.out is redirected to specified level.
     * System.err is always redirected to the ERROR level.
     *
     * @param outLogLevel Log level for System.out
     */
    public static void on(LogLevel outLogLevel) {
        getOutAdapter().setLevel(outLogLevel);
        getErrAdapter().setLevel(LogLevel.ERROR);
        redirect(getOut(), getErr());
    }

    /**
     * Redirects only System.out to the specified level. System.err is not redirected.
     *
     * @param outLogLevel Log level for System.out
     */
    public static void onOut(LogLevel outLogLevel) {
        getOutAdapter().setLevel(outLogLevel);
        System.setOut(getOut());
    }

    /**
     * Redirects only System.err to the specified level. System.out is not redirected.
     *
     * @param errLogLevel Log level for System.err
     */
    public static void onErr(LogLevel errLogLevel) {
        getErrAdapter().setLevel(errLogLevel);
        System.setErr(getErr());
    }

    public static void flush() {
        getOut().flush();
        getErr().flush();
    }

    /**
     * Sets System.err and System.out to the values they had before Gradle has been started.
     */
    public static void off() {
        redirect(DEFAULT_OUT, DEFAULT_ERR);
    }

    /**
     * Sets System.out to the values it had before Gradle has been started.
     */
    public static void offOut() {
        System.setOut(DEFAULT_OUT);
    }

    /**
     * Sets System.err to the values it had before Gradle has been started.
     */
    public static void offErr() {
        System.setErr(DEFAULT_ERR);
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
}
