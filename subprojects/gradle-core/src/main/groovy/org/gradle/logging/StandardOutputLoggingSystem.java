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

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.util.LinePerThreadBufferingOutputStream;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

public class StandardOutputLoggingSystem implements LoggingSystem {
    private final Destination stdout = new Destination(Logging.getLogger("system.out")) {
        @Override
        PrintStream get() {
            return System.out;
        }

        @Override
        void set(PrintStream printStream) {
            System.setOut(printStream);
        }
    };
    private final Destination stderr = new Destination(Logging.getLogger("system.err")) {
        @Override
        PrintStream get() {
            return System.err;
        }

        @Override
        void set(PrintStream printStream) {
            System.setErr(printStream);
        }
    };

    public Snapshot off() {
        Snapshot state = snapshot();
        stdout.off();
        stderr.off();
        return state;
    }

    public Snapshot on(LogLevel level) {
        Snapshot state = snapshot();
        stdout.on(level);
        stderr.on(LogLevel.ERROR);
        return state;
    }

    public Snapshot snapshot() {
        return new SnapshotImpl(stdout.getDestination(), stderr.getDestination());
    }

    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        stdout.setDestination(snapshot.stdoutListener);
        stderr.setDestination(snapshot.stderrListener);
    }

    private static class SnapshotImpl implements Snapshot {
        private final StandardOutputListener stdoutListener;
        private final StandardOutputListener stderrListener;

        private SnapshotImpl(StandardOutputListener stdoutListener, StandardOutputListener stderrListener) {
            this.stdoutListener = stdoutListener;
            this.stderrListener = stderrListener;
        }

        @Override
        public String toString() {
            return String.format("out: %s, err: %s", stdoutListener, stderrListener);
        }
    }

    private static abstract class Destination {
        private final AtomicReference<StandardOutputListener> destination
                = new AtomicReference<StandardOutputListener>();
        private final PrintStream outstr = new LinePerThreadBufferingOutputStream(new Action<String>() {
            public void execute(String output) {
                destination.get().onOutput(output);
            }
        });
        private final Logger logger;
        private StandardOutputListener original;

        protected Destination(Logger logger) {
            this.logger = logger;
        }

        public StandardOutputListener getDestination() {
            return destination.get();
        }

        public void on(final LogLevel level) {
            install();
            destination.set(new LoggerDestination(level));
        }

        public void off() {
            if (original == null) {
                return;
            }
            outstr.flush();
            destination.set(original);
        }

        public void setDestination(StandardOutputListener listener) {
            install();
            if (listener == null) {
                destination.set(original);
            } else {
                destination.set(listener);
            }
        }

        private void install() {
            if (original == null) {
                PrintStream originalStream = get();
                original = new PrintStreamDestination(originalStream);
            }
            outstr.flush();
            if (get() != outstr) {
                set(outstr);
            }
        }

        abstract PrintStream get();

        abstract void set(PrintStream printStream);

        private static class PrintStreamDestination implements StandardOutputListener {
            private final PrintStream originalStream;

            public PrintStreamDestination(PrintStream originalStream) {
                this.originalStream = originalStream;
            }

            public void onOutput(CharSequence output) {
                originalStream.println(output);
            }
        }

        private class LoggerDestination implements StandardOutputListener {
            private final LogLevel level;

            public LoggerDestination(LogLevel level) {
                this.level = level;
            }

            public void onOutput(CharSequence output) {
                logger.log(level, output.toString());
            }
        }
    }
}