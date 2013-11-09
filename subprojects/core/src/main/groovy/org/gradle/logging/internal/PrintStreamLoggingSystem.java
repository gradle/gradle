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

import org.gradle.api.Nullable;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.io.TextStream;
import org.gradle.util.LinePerThreadBufferingOutputStream;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link LoggingSystem} which routes content written to a {@code PrintStream} to a {@link OutputEventListener}.
 * Generates a {@link StyledTextOutputEvent} instance when a line of text is written to the {@code PrintStream}.
 * Generates a {@link LogLevelChangeEvent} when the log level for this {@code LoggingSystem} is changed.
 */
abstract class PrintStreamLoggingSystem implements LoggingSystem {
    private final AtomicReference<StandardOutputListener> destination = new AtomicReference<StandardOutputListener>();
    private final PrintStream outstr = new LinePerThreadBufferingOutputStream(new TextStream() {
        public void text(String output) {
            destination.get().onOutput(output);
        }

        public void endOfStream(@Nullable Throwable failure) {
        }
    });
    private StandardOutputListener original;
    private LogLevel logLevel;
    private final StandardOutputListener listener;
    private final OutputEventListener outputEventListener;

    protected PrintStreamLoggingSystem(OutputEventListener listener, String category, TimeProvider timeProvider) {
        outputEventListener = listener;
        this.listener = new OutputEventDestination(listener, category, timeProvider);
    }

    /**
     * Returns the current value of the PrintStream
     */
    protected abstract PrintStream get();

    /**
     * Sets the current value of the PrintStream
     */
    protected abstract void set(PrintStream printStream);

    public Snapshot snapshot() {
        return new SnapshotImpl(logLevel);
    }

    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        install();
        if (snapshot.logLevel == null) {
            destination.set(original);
        } else {
            this.logLevel = snapshot.logLevel;
            outputEventListener.onOutput(new LogLevelChangeEvent(snapshot.logLevel));
            destination.set(listener);
        }
    }

    public Snapshot on(final LogLevel level) {
        Snapshot snapshot = snapshot();
        install();
        this.logLevel = level;
        outputEventListener.onOutput(new LogLevelChangeEvent(logLevel));
        destination.set(listener);
        return snapshot;
    }

    public Snapshot off() {
        Snapshot snapshot = snapshot();
        if (original != null) {
            outstr.flush();
            destination.set(original);
            logLevel = null;
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
            originalStream.print(output);
        }
    }

    private static class SnapshotImpl implements Snapshot {
        private final LogLevel logLevel;

        public SnapshotImpl(LogLevel logLevel) {
            this.logLevel = logLevel;
        }
    }

    private static class OutputEventDestination implements StandardOutputListener {
        private final OutputEventListener listener;
        private final String category;
        private final TimeProvider timeProvider;

        public OutputEventDestination(OutputEventListener listener, String category, TimeProvider timeProvider) {
            this.listener = listener;
            this.category = category;
            this.timeProvider = timeProvider;
        }

        public void onOutput(CharSequence output) {
            listener.onOutput(new StyledTextOutputEvent(timeProvider.getCurrentTime(), category, output.toString()));
        }
    }
}
