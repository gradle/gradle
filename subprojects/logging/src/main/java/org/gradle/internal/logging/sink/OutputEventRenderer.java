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

package org.gradle.internal.logging.sink;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.logging.config.LoggingRouter;
import org.gradle.internal.logging.console.BuildLogLevelFilterRenderer;
import org.gradle.internal.logging.console.BuildStatusRenderer;
import org.gradle.internal.logging.console.ColorMap;
import org.gradle.internal.logging.console.Console;
import org.gradle.internal.logging.console.ConsoleLayoutCalculator;
import org.gradle.internal.logging.console.DefaultColorMap;
import org.gradle.internal.logging.console.DefaultWorkInProgressFormatter;
import org.gradle.internal.logging.console.FlushConsoleListener;
import org.gradle.internal.logging.console.StyledTextOutputBackedRenderer;
import org.gradle.internal.logging.console.ThrottlingOutputEventListener;
import org.gradle.internal.logging.console.UserInputConsoleRenderer;
import org.gradle.internal.logging.console.UserInputStandardOutputRenderer;
import org.gradle.internal.logging.console.WorkInProgressRenderer;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.format.PrettyPrefixedLogHeaderFormatter;
import org.gradle.internal.logging.text.StreamBackedStandardOutputListener;
import org.gradle.internal.logging.text.StreamingStyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.internal.nativeintegration.console.FallbackConsoleMetaData;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link OutputEventListener} implementation which renders output events to various
 * destinations. This implementation is thread-safe.
 */
@ThreadSafe
public class OutputEventRenderer implements OutputEventListener, LoggingRouter {
    private final Object lock = new Object();
    private final AtomicReference<LogLevel> logLevel = new AtomicReference<LogLevel>(LogLevel.LIFECYCLE);
    private final Clock clock;
    private final ListenerBroadcast<OutputEventListener> formatters = new ListenerBroadcast<OutputEventListener>(OutputEventListener.class);
    private final OutputEventTransformer transformer = new OutputEventTransformer(formatters.getSource());

    private ColorMap colourMap;
    private OutputStream originalStdOut;
    private OutputStream originalStdErr;
    private OutputEventListener stdOutListener;
    private OutputEventListener stdErrListener;
    private OutputEventListener console;
    private OutputEventListener userListenerChain;
    private ListenerBroadcast<StandardOutputListener> userStdoutListeners;
    private ListenerBroadcast<StandardOutputListener> userStderrListeners;

    public OutputEventRenderer(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            // Currently only snapshot the console output listener. Should snapshot all output listeners, and cleanup in restore()
            return new SnapshotImpl(logLevel.get(), console);
        }
    }

    @Override
    public void restore(Snapshot state) {
        synchronized (lock) {
            SnapshotImpl snapshot = (SnapshotImpl) state;
            if (snapshot.logLevel != logLevel.get()) {
                configure(snapshot.logLevel);
            }

            // TODO - also close console when it is replaced
            if (snapshot.console != console) {
                if (snapshot.console == null) {
                    removeChain(console);
                    console = null;
                } else {
                    throw new UnsupportedOperationException("Cannot restore previous console. This is not implemented yet.");
                }
            }
        }
    }

    private void addChain(OutputEventListener listener) {
        listener.onOutput(new LogLevelChangeEvent(logLevel.get()));
        formatters.add(listener);
    }

    private void removeChain(OutputEventListener listener) {
        formatters.remove(listener);
        listener.onOutput(new EndOutputEvent());
    }

    public ColorMap getColourMap() {
        synchronized (lock) {
            if (colourMap == null) {
                colourMap = new DefaultColorMap();
            }
        }
        return colourMap;
    }

    @Override
    public void flush() {
        onOutput(new FlushOutputEvent());
    }

    public OutputStream getOriginalStdOut() {
        return originalStdOut;
    }

    public OutputStream getOriginalStdErr() {
        return originalStdErr;
    }

    public void attachProcessConsole(ConsoleOutput consoleOutput) {
        synchronized (lock) {
            ConsoleConfigureAction.execute(this, consoleOutput);
        }
    }

    @Override
    public void attachConsole(OutputStream outputStream, OutputStream errorStream, ConsoleOutput consoleOutput) {
        attachConsole(outputStream, errorStream, consoleOutput, null);
    }

    @Override
    public void attachConsole(OutputStream outputStream, OutputStream errorStream, ConsoleOutput consoleOutput, @Nullable ConsoleMetaData consoleMetadata) {
        synchronized (lock) {
            if (consoleMetadata == null) {
                consoleMetadata = FallbackConsoleMetaData.NOT_ATTACHED;
            }
            ConsoleConfigureAction.execute(this, consoleOutput, consoleMetadata, outputStream, errorStream);
        }
    }

    public void attachSystemOutAndErr() {
        addSystemOutAsLoggingDestination();
        addSystemErrAsLoggingDestination();
    }

    private void addSystemOutAsLoggingDestination() {
        synchronized (lock) {
            originalStdOut = System.out;
            if (stdOutListener != null) {
                removeChain(stdOutListener);
            }
            stdOutListener = new LazyListener(new Factory<OutputEventListener>() {
                @Override
                public OutputEventListener create() {
                    return onNonError(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(new StreamBackedStandardOutputListener((Appendable) originalStdOut))));
                }
            });
            addChain(stdOutListener);
        }
    }

    private void addSystemErrAsLoggingDestination() {
        synchronized (lock) {
            originalStdErr = System.err;
            if (stdErrListener != null) {
                removeChain(stdErrListener);
            }
            stdErrListener = new LazyListener(new Factory<OutputEventListener>() {
                @Override
                public OutputEventListener create() {
                    return onError(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(new StreamBackedStandardOutputListener((Appendable) originalStdErr))));
                }
            });
            addChain(stdErrListener);
        }
    }

    private void removeSystemOutAsLoggingDestination() {
        synchronized (lock) {
            if (stdOutListener != null) {
                removeChain(stdOutListener);
                stdOutListener = null;
            }
        }
    }

    private void removeSystemErrAsLoggingDestination() {
        synchronized (lock) {
            if (stdErrListener != null) {
                removeChain(stdErrListener);
                stdErrListener = null;
            }
        }
    }

    public void addOutputEventListener(OutputEventListener listener) {
        synchronized (lock) {
            addChain(listener);
        }
    }

    public void removeOutputEventListener(OutputEventListener listener) {
        synchronized (lock) {
            removeChain(listener);
        }
    }

    public OutputEventRenderer addRichConsole(Console console, ConsoleMetaData consoleMetaData, OutputStream stdout, OutputStream stderr, boolean verbose) {
        OutputEventListener consoleChain;
        boolean stdoutAttachedToConsole = consoleMetaData.isStdOut();
        boolean stderrAttachedToConsole = consoleMetaData.isStdErr();
        StandardOutputListener outputListener = new StreamBackedStandardOutputListener(stdout);
        StandardOutputListener errorListener = new StreamBackedStandardOutputListener(stderr);
        if (stdoutAttachedToConsole && stderrAttachedToConsole) {
            OutputEventListener consoleListener = new StyledTextOutputBackedRenderer(console.getBuildOutputArea());
            consoleChain = getRichConsoleChain(console, consoleMetaData, verbose, consoleListener);
        } else if (stderrAttachedToConsole) {
            OutputEventListener stdoutChain = new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(outputListener));
            OutputEventListener stderrChain = new FlushConsoleListener(console, new StyledTextOutputBackedRenderer(console.getBuildOutputArea()));
            consoleChain = getPlainConsoleChain(stdoutChain, stderrChain, false, verbose);
        } else {
            OutputEventListener stdoutChain = new StyledTextOutputBackedRenderer(console.getBuildOutputArea());
            OutputEventListener stderrChain = new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(errorListener));
            OutputEventListener consoleListener = new ErrorOutputDispatchingListener(stderrChain, stdoutChain, false);
            consoleChain = getRichConsoleChain(console, consoleMetaData, verbose, consoleListener);
        }

        return addConsoleChain(consoleChain);
    }

    private OutputEventListener getRichConsoleChain(Console console, ConsoleMetaData consoleMetaData, boolean verbose, OutputEventListener consoleListener) {
        return throttled(
            new UserInputConsoleRenderer(
                new BuildStatusRenderer(
                    new WorkInProgressRenderer(
                        new BuildLogLevelFilterRenderer(
                            new GroupingProgressLogEventGenerator(consoleListener, new PrettyPrefixedLogHeaderFormatter(), verbose)
                        ),
                        console.getBuildProgressArea(),
                        new DefaultWorkInProgressFormatter(consoleMetaData),
                        new ConsoleLayoutCalculator(consoleMetaData)
                    ),
                console.getStatusBar(), console, consoleMetaData),
            console)
        );
    }

    public OutputEventRenderer addPlainConsole(OutputStream stdout, OutputStream stderr, boolean redirectStderr) {
        return addConsoleChain(getPlainConsoleChain(new StreamBackedStandardOutputListener(stdout), new StreamBackedStandardOutputListener(stderr), redirectStderr, true));
    }

    private OutputEventListener getPlainConsoleChain(StandardOutputListener outputListener, StandardOutputListener errorListener, boolean redirectStderr, boolean verbose) {
        final OutputEventListener stdoutChain = new UserInputStandardOutputRenderer(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(outputListener)));
        final OutputEventListener stderrChain = new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(errorListener));
        return getPlainConsoleChain(stdoutChain, stderrChain, redirectStderr, verbose);
    }

    private OutputEventListener getPlainConsoleChain(OutputEventListener stdoutChain, OutputEventListener stderrChain, boolean redirectStderr, boolean verbose) {
        return throttled(
            new BuildLogLevelFilterRenderer(
                new GroupingProgressLogEventGenerator(
                    new ErrorOutputDispatchingListener(stderrChain, stdoutChain, redirectStderr),
                    new PrettyPrefixedLogHeaderFormatter(),
                    verbose
                )
            )
        );
    }

    private OutputEventListener throttled(OutputEventListener consoleChain) {
        return new ThrottlingOutputEventListener(consoleChain, clock);
    }

    private OutputEventRenderer addConsoleChain(OutputEventListener consoleChain) {
        synchronized (lock) {
            this.console = consoleChain;
            removeSystemOutAsLoggingDestination();
            removeSystemErrAsLoggingDestination();
            addChain(this.console);
        }
        return this;
    }

    private OutputEventListener onError(final OutputEventListener listener) {
        return new LogEventDispatcher(null, listener);
    }

    private OutputEventListener onNonError(final OutputEventListener listener) {
        return new LogEventDispatcher(listener, null);
    }

    @Override
    public void enableUserStandardOutputListeners() {
        // Create all of the pipeline eagerly as soon as this is enabled, to track the state of build operations.
        // All of the pipelines do this, so should instead have a single stage that tracks this for all pipelines and that can replay the current state to new pipelines
        // Then, a pipeline can be added for each listener as required
        synchronized (lock) {
            if (userStdoutListeners == null) {
                userStdoutListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
                userStderrListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
                final OutputEventListener stdOutChain = new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(userStdoutListeners.getSource()));
                final OutputEventListener stdErrChain = new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(userStderrListeners.getSource()));
                userListenerChain = new BuildLogLevelFilterRenderer(
                    new ProgressLogEventGenerator(new OutputEventListener() {
                        @Override
                        public void onOutput(OutputEvent event) {
                            // Do not forward events for rendering when there are no listeners to receive
                            if (event instanceof LogLevelChangeEvent) {
                                stdOutChain.onOutput(event);
                                stdErrChain.onOutput(event);
                            } else if (event.getLogLevel() == LogLevel.ERROR && !userStderrListeners.isEmpty() && event instanceof RenderableOutputEvent) {
                                stdErrChain.onOutput(event);
                            } else if (event.getLogLevel() != LogLevel.ERROR && !userStdoutListeners.isEmpty() && event instanceof RenderableOutputEvent) {
                                stdOutChain.onOutput(event);
                            }
                        }
                    })
                );
                addChain(userListenerChain);
            }
        }
    }

    private void assertUserListenersEnabled() {
        if (userListenerChain == null) {
            throw new IllegalStateException("Custom standard output listeners not enabled.");
        }
        userListenerChain.onOutput(new FlushOutputEvent());
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
        synchronized (lock) {
            assertUserListenersEnabled();
            userStderrListeners.add(listener);
        }
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        synchronized (lock) {
            assertUserListenersEnabled();
            userStdoutListeners.add(listener);
        }
    }

    public void addStandardOutputListener(OutputStream outputStream) {
        addStandardOutputListener(new StreamBackedStandardOutputListener(outputStream));
    }

    public void addStandardErrorListener(OutputStream outputStream) {
        addStandardErrorListener(new StreamBackedStandardOutputListener(outputStream));
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        synchronized (lock) {
            assertUserListenersEnabled();
            userStdoutListeners.remove(listener);
        }
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        synchronized (lock) {
            assertUserListenersEnabled();
            userStderrListeners.remove(listener);
        }
    }

    public void configure(LogLevel logLevel) {
        onOutput(new LogLevelChangeEvent(logLevel));
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event.getLogLevel() != null && event.getLogLevel().compareTo(logLevel.get()) < 0 && !isProgressEvent(event)) {
            return;
        }
        if (event instanceof LogLevelChangeEvent) {
            LogLevelChangeEvent changeEvent = (LogLevelChangeEvent) event;
            LogLevel newLogLevel = changeEvent.getNewLogLevel();
            if (newLogLevel == this.logLevel.get()) {
                return;
            }
            this.logLevel.set(newLogLevel);
        }
        synchronized (lock) {
            transformer.onOutput(event);
        }
    }

    private boolean isProgressEvent(OutputEvent event) {
        return event instanceof ProgressStartEvent || event instanceof ProgressEvent || event instanceof ProgressCompleteEvent;
    }

    private static class SnapshotImpl implements Snapshot {
        private final LogLevel logLevel;
        private final OutputEventListener console;

        SnapshotImpl(LogLevel logLevel, OutputEventListener console) {
            this.logLevel = logLevel;
            this.console = console;
        }
    }

    private static class LazyListener implements OutputEventListener {
        private Factory<OutputEventListener> factory;
        private OutputEventListener delegate;
        private LogLevelChangeEvent pendingLogLevel;

        private LazyListener(Factory<OutputEventListener> factory) {
            this.factory = factory;
        }

        @Override
        public void onOutput(OutputEvent event) {
            if (delegate == null) {
                if (event instanceof EndOutputEvent || event instanceof FlushOutputEvent) {
                    // Ignore
                    return;
                }
                if (event instanceof LogLevelChangeEvent) {
                    // Keep until the listener is created
                    pendingLogLevel = (LogLevelChangeEvent) event;
                    return;
                }
                delegate = factory.create();
                factory = null;
                if (pendingLogLevel != null) {
                    delegate.onOutput(pendingLogLevel);
                    pendingLogLevel = null;
                }
            }
            delegate.onOutput(event);
        }
    }

}
