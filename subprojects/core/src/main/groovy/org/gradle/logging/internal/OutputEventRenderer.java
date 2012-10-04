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

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.console.ConsoleMetaData;
import org.gradle.internal.nativeplatform.TerminalDetector;
import org.gradle.listener.ListenerBroadcast;

import java.io.FileDescriptor;
import java.io.PrintStream;

/**
 * A {@link org.gradle.logging.internal.OutputEventListener} implementation which renders output events to various
 * destinations. This implementation is thread-safe.
 */
public class OutputEventRenderer implements OutputEventListener, LoggingConfigurer, LoggingOutputInternal {
    private final ListenerBroadcast<OutputEventListener> formatters = new ListenerBroadcast<OutputEventListener>(OutputEventListener.class);
    private final ListenerBroadcast<StandardOutputListener> stdoutListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final ListenerBroadcast<StandardOutputListener> stderrListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final TerminalDetector terminalDetector;
    private final Object lock = new Object();
    private final DefaultColorMap colourMap = new DefaultColorMap();
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private final ConsoleMetaData consoleMetaData;

    public OutputEventRenderer(TerminalDetector terminalDetector, ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
        OutputEventListener stdOutChain = onNonError(new ProgressLogEventGenerator(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(stdoutListeners.getSource())), false));
        formatters.add(stdOutChain);
        OutputEventListener stdErrChain = onError(new ProgressLogEventGenerator(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(stderrListeners.getSource())), false));
        formatters.add(stdErrChain);
        this.terminalDetector = terminalDetector;
    }

    public void colorStdOutAndStdErr(boolean colorOutput) {
        synchronized (lock) {
            colourMap.setUseColor(colorOutput);
        }
    }

    public OutputEventRenderer addStandardOutputAndError() {
        boolean stdOutIsTerminal = terminalDetector.isTerminal(FileDescriptor.out);
        boolean stdErrIsTerminal = terminalDetector.isTerminal(FileDescriptor.err);
        if (stdOutIsTerminal) {
            PrintStream outStr = org.fusesource.jansi.AnsiConsole.out();
            Console console = new AnsiConsole(outStr, outStr, colourMap);
            addConsole(console, true, stdErrIsTerminal);
        } else if (stdErrIsTerminal) {
            // Only stderr is connected to a terminal
            PrintStream errStr = org.fusesource.jansi.AnsiConsole.err();
            Console console = new AnsiConsole(errStr, errStr, colourMap);
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
        addStandardOutputListener(new StreamBackedStandardOutputListener(out));
        return this;
    }

    public OutputEventRenderer addStandardError(final Appendable err) {
        addStandardErrorListener(new StreamBackedStandardOutputListener(err));
        return this;
    }

    public void addOutputEventListener(OutputEventListener listener) {
        formatters.add(listener);
    }

    public void removeOutputEventListener(OutputEventListener listener) {
        formatters.remove(listener);
    }

    public OutputEventRenderer addConsole(final Console console, boolean stdout, boolean stderr) {
        final OutputEventListener consoleChain = new ConsoleBackedProgressRenderer(new ProgressLogEventGenerator(new StyledTextOutputBackedRenderer(console.getMainArea()), true), console, new DefaultStatusBarFormatter(consoleMetaData));
        synchronized (lock) {
            if (stdout && stderr) {
                formatters.add(consoleChain);
            } else if (stdout) {
                formatters.add(onNonError(consoleChain));
            } else {
                formatters.add(onError(consoleChain));
            }
        }
        return this;
    }

    private OutputEventListener onError(final OutputEventListener listener) {
        return new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                if (event.getLogLevel() == LogLevel.ERROR || event.getLogLevel() == null) {
                    listener.onOutput(event);
                }
            }
        };
    }

    private OutputEventListener onNonError(final OutputEventListener listener) {
        return new OutputEventListener() {
            public void onOutput(OutputEvent event) {
                if (event.getLogLevel() != LogLevel.ERROR || event.getLogLevel() == null) {
                    listener.onOutput(event);
                }
            }
        };
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
        synchronized (lock) {
            stderrListeners.add(listener);
        }
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        synchronized (lock) {
            stdoutListeners.add(listener);
        }
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        synchronized (lock) {
            stdoutListeners.remove(listener);
        }
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        synchronized (lock) {
            stderrListeners.remove(listener);
        }
    }

    public void configure(LogLevel logLevel) {
        onOutput(new LogLevelChangeEvent(logLevel));
    }

    public void onOutput(OutputEvent event) {
        synchronized (lock) {
            if (event.getLogLevel() != null && event.getLogLevel().compareTo(logLevel) < 0) {
                return;
            }
            if (event instanceof LogLevelChangeEvent) {
                LogLevelChangeEvent changeEvent = (LogLevelChangeEvent) event;
                LogLevel newLogLevel = changeEvent.getNewLogLevel();
                if (newLogLevel == this.logLevel) {
                    return;
                }
                this.logLevel = newLogLevel;
            }
            formatters.getSource().onOutput(event);
        }
    }
}
