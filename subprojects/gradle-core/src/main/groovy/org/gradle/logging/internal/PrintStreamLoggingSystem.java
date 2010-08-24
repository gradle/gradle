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

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.util.LinePerThreadBufferingOutputStream;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link LoggingSystem} which routes content written to a PrintStream to the logging system.
 */
abstract class PrintStreamLoggingSystem implements LoggingSystem {
    private final AtomicReference<StandardOutputListener> destination
            = new AtomicReference<StandardOutputListener>();
    private final PrintStream outstr = new LinePerThreadBufferingOutputStream(new Action<String>() {
        public void execute(String output) {
            destination.get().onOutput(output);
        }
    });
    private final LoggingBackedStyledTextOutput textOutput;
    private StandardOutputListener original;

    protected PrintStreamLoggingSystem(OutputEventListener listener, String category) {
        this.textOutput = new LoggingBackedStyledTextOutput(listener, category, LogLevel.LIFECYCLE);
    }

    /**
     * Returns the current value of the PrintStream
     */
    protected abstract PrintStream get();

    /**
     * Sets the current value of the PrintStream
     */
    protected abstract void set(PrintStream printStream);

    protected LoggingBackedStyledTextOutput getTextOutput() {
        return textOutput;
    }

    public Snapshot snapshot() {
        return new SnapshotImpl(destination.get(), textOutput.getLogLevel());
    }

    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        install();
        if (snapshot.listener == null) {
            destination.set(original);
        } else {
            destination.set(snapshot.listener);
        }
        textOutput.configure(snapshot.logLevel);
    }

    public Snapshot on(final LogLevel level) {
        Snapshot snapshot = snapshot();
        install();
        textOutput.configure(level);
        destination.set(textOutput);
        return snapshot;
    }

    public Snapshot off() {
        Snapshot snapshot = snapshot();
        if (original != null) {
            outstr.flush();
            destination.set(original);
        }
        return snapshot;
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

    private static class PrintStreamDestination implements StandardOutputListener {
        private final PrintStream originalStream;

        public PrintStreamDestination(PrintStream originalStream) {
            this.originalStream = originalStream;
        }

        public void onOutput(CharSequence output) {
            originalStream.println(output);
        }
    }

    private static class SnapshotImpl implements Snapshot {
        private final StandardOutputListener listener;
        private final LogLevel logLevel;

        public SnapshotImpl(StandardOutputListener listener, LogLevel logLevel) {
            this.listener = listener;
            this.logLevel = logLevel;
        }
    }

}
