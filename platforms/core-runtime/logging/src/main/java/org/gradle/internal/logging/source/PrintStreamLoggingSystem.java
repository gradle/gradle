/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.source;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.io.LinePerThreadBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.Nullable;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link LoggingSourceSystem} which routes content written to a {@code PrintStream} to a {@link OutputEventListener}.
 * Generates a {@link StyledTextOutputEvent} instance when a line of text is written to the {@code PrintStream}.
 * Generates a {@link LogLevelChangeEvent} when the log level for this {@code LoggingSystem} is changed.
 *
 * <p>This type mutates process-global state ({@code System.out} / {@code System.err}) on behalf of many
 * independent scopes, and those scopes are started and stopped concurrently: under Isolated Projects each
 * project is configured on its own build operation worker thread, and each project script is wrapped in a
 * {@code start()}/{@code stop()} pair by {@code DefaultScriptRunnerFactory}. The same is true of parallel task
 * execution. Two properties are therefore required, and both are implemented here:</p>
 *
 * <ul>
 *     <li>All state transitions are guarded by a single monitor, so a snapshot/mutate/restore sequence on one
 *     thread cannot interleave with another thread's.</li>
 *     <li>Capture is reference counted, so one scope ending cannot tear down capture while sibling scopes are
 *     still active. Previously the last scope to restore its snapshot won, which silently dropped everything
 *     written to {@code System.out} by projects that were still being configured.</li>
 * </ul>
 */
abstract class PrintStreamLoggingSystem implements LoggingSourceSystem {
    private final AtomicReference<StandardOutputListener> destination = new AtomicReference<StandardOutputListener>();
    private final PrintStream outstr = new LinePerThreadBufferingOutputStream(new TextStream() {
        @Override
        public void text(String output) {
            destination.get().onOutput(output);
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
        }
    });
    private final Object lock = new Object();
    private PrintStreamDestination original;
    /**
     * The number of scopes that currently have capture enabled. Capture is installed while this is positive.
     */
    private int captureCount;
    private boolean installed;
    private LogLevel logLevel;
    private final StandardOutputListener listener;
    private final OutputEventListener outputEventListener;

    protected PrintStreamLoggingSystem(OutputEventListener listener, String category, Clock clock) {
        outputEventListener = listener;
        this.listener = new OutputEventDestination(listener, category, clock);
    }

    /**
     * Returns the current value of the PrintStream
     */
    protected abstract PrintStream get();

    /**
     * Sets the current value of the PrintStream
     */
    protected abstract void set(PrintStream printStream);

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            return new SnapshotImpl(logLevel);
        }
    }

    @Override
    public void restore(Snapshot state) {
        SnapshotImpl snapshot = (SnapshotImpl) state;
        synchronized (lock) {
            boolean levelChanged = snapshot.logLevel != logLevel;
            logLevel = snapshot.logLevel;
            boolean wasInstalled = installed;
            // Capture is driven purely by the reference count. Restoring the snapshot's `enabled` flag here would
            // let a scope that finishes early uninstall capture out from under sibling scopes that are still
            // running. Scopes release their own capture via endCapture().
            reconcileCapture();
            if (levelChanged && installed && wasInstalled) {
                // install() emits this itself when it newly installs, so only do it when capture was already up.
                outstr.flush();
                outputEventListener.onOutput(new LogLevelChangeEvent(logLevel));
            }
        }
    }

    @Override
    public Snapshot setLevel(LogLevel logLevel) {
        synchronized (lock) {
            Snapshot snapshot = new SnapshotImpl(this.logLevel);
            if (logLevel != this.logLevel) {
                this.logLevel = logLevel;
                if (captureCount > 0) {
                    outstr.flush();
                    outputEventListener.onOutput(new LogLevelChangeEvent(logLevel));
                }
            }
            return snapshot;
        }
    }

    @Override
    public Snapshot startCapture() {
        synchronized (lock) {
            Snapshot snapshot = new SnapshotImpl(logLevel);
            captureCount++;
            reconcileCapture();
            return snapshot;
        }
    }

    @Override
    public void endCapture() {
        synchronized (lock) {
            if (captureCount > 0) {
                captureCount--;
            }
            reconcileCapture();
        }
    }

    private void reconcileCapture() {
        if (captureCount > 0) {
            install();
        } else {
            uninstall();
        }
    }

    private void uninstall() {
        if (!installed) {
            return;
        }
        outstr.flush();
        if (original != null) {
            destination.set(original);
            set(original.originalStream);
            original = null;
        }
        installed = false;
    }

    private void install() {
        if (installed) {
            return;
        }
        if (original == null) {
            PrintStream originalStream = get();
            // Never snapshot our own capture stream as the stream to restore later: that would make
            // uninstall() point System.out at outstr with `destination` writing back into outstr, a
            // self-referential sink that silently discards everything written to it.
            if (originalStream != outstr) {
                original = new PrintStreamDestination(originalStream);
            }
        }
        outstr.flush();
        outputEventListener.onOutput(new LogLevelChangeEvent(logLevel));
        destination.set(listener);
        if (get() != outstr) {
            set(outstr);
        }
        installed = true;
    }

    private static class PrintStreamDestination implements StandardOutputListener {
        private final PrintStream originalStream;

        public PrintStreamDestination(PrintStream originalStream) {
            this.originalStream = originalStream;
        }

        @Override
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
        private final Clock clock;

        public OutputEventDestination(OutputEventListener listener, String category, Clock clock) {
            this.listener = listener;
            this.category = category;
            this.clock = clock;
        }

        @Override
        public void onOutput(CharSequence output) {
            OperationIdentifier buildOperationId = CurrentBuildOperationRef.instance().getId();
            StyledTextOutputEvent event = new StyledTextOutputEvent(clock.getCurrentTime(), category, null, buildOperationId, output.toString());
            listener.onOutput(event);
        }
    }
}
