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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingOutput;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.listener.ListenerBroadcast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintStream;

/**
 * A {@link org.gradle.logging.internal.OutputEventListener} implementation which renders output events to various
 * destinations. This implementation is thread-safe.
 */
public class OutputEventRenderer implements OutputEventListener, LoggingConfigurer, LoggingOutput {
    private final ListenerBroadcast<OutputEventListener> formatters = new ListenerBroadcast<OutputEventListener>(OutputEventListener.class);
    private final ListenerBroadcast<StandardOutputListener> stdoutListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final ListenerBroadcast<StandardOutputListener> stderrListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final Object lock = new Object();
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    public OutputEventRenderer() {
        formatters.add(new BasicProgressLoggingAwareFormatter(stdoutListeners.getSource(), stderrListeners.getSource()));
    }

    public OutputEventRenderer addStandardOutputAndError() {
        TerminalDetector terminalDetector = new TerminalDetector();
        boolean stdOutIsTerminal = terminalDetector.isSatisfiedBy(FileDescriptor.out);
        boolean stdErrIsTerminal = terminalDetector.isSatisfiedBy(FileDescriptor.err);
        if (stdOutIsTerminal) {
            PrintStream outStr = org.fusesource.jansi.AnsiConsole.out();
            Console console = new AnsiConsole(outStr, outStr);
            addConsole(console, true, stdErrIsTerminal);
        } else if (stdErrIsTerminal) {
            // Only std err is connected to a terminal
            PrintStream errStr = org.fusesource.jansi.AnsiConsole.err();
            Console console = new AnsiConsole(errStr, errStr);
            addConsole(console, false, true);
        }
        if (!stdOutIsTerminal) {
            addStandardOutput(System.out);
        }
        if (!stdErrIsTerminal) {
            addStandardError(System.err);
        }
        return this;
    }

    public OutputEventRenderer addStandardOutput(final Appendable out) {
        addStandardOutputListener(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                try {
                    out.append(output);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

    public OutputEventRenderer addStandardError(final Appendable err) {
        addStandardErrorListener(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                try {
                    err.append(output);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return this;
    }

    public OutputEventRenderer addConsole(Console console, boolean stdout, boolean stderr) {
        final ConsoleBackedFormatter consoleFormatter = new ConsoleBackedFormatter(console);
        if (stdout && stderr) {
            formatters.add(consoleFormatter);
        } else if (stdout) {
            formatters.add(onNonError(consoleFormatter));
        } else {
            formatters.add(onError(consoleFormatter));
        }
        return this;
    }

    private OutputEventListener onError(final OutputEventListener listener) {
        return new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                if (event.relevantFor(LogLevel.ERROR)) {
                    listener.onOutput(event);
                }
            }
        };
    }

    private OutputEventListener onNonError(final OutputEventListener listener) {
        return new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                if (!event.relevantFor(LogLevel.ERROR)) {
                    listener.onOutput(event);
                }
            }
        };
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
        stderrListeners.add(listener);
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        stdoutListeners.add(listener);
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        stdoutListeners.remove(listener);
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        stderrListeners.remove(listener);
    }

    public void configure(LogLevel logLevel) {
        synchronized (lock) {
            this.logLevel = logLevel;
            formatters.getSource().onOutput(new LogLevelChangeEvent(logLevel));
        }
    }

    public void onOutput(OutputEvent event) {
        synchronized (lock) {
            if (!event.relevantFor(logLevel)) {
                return;
            }
            formatters.getSource().onOutput(event);
        }
    }
}
