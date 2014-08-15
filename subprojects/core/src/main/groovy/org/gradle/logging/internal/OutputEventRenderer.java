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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;
import org.gradle.listener.ListenerBroadcast;

import java.io.OutputStream;

/**
 * A {@link org.gradle.logging.internal.OutputEventListener} implementation which renders output events to various
 * destinations. This implementation is thread-safe.
 */
@ThreadSafe
public class OutputEventRenderer implements OutputEventListener, LoggingConfigurer, LoggingOutputInternal {
    private final ListenerBroadcast<OutputEventListener> formatters = new ListenerBroadcast<OutputEventListener>(OutputEventListener.class);
    private final ListenerBroadcast<StandardOutputListener> stdoutListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final ListenerBroadcast<StandardOutputListener> stderrListeners = new ListenerBroadcast<StandardOutputListener>(StandardOutputListener.class);
    private final Object lock = new Object();
    private final DefaultColorMap colourMap = new DefaultColorMap();
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private final Action<? super OutputEventRenderer> consoleConfigureAction;
    private OutputStream originalStdOut;
    private OutputStream originalStdErr;
    private StreamBackedStandardOutputListener stdOutListener;
    private StreamBackedStandardOutputListener stdErrListener;

    public OutputEventRenderer(Action<? super OutputEventRenderer> consoleConfigureAction) {
        OutputEventListener stdOutChain = onNonError(new ProgressLogEventGenerator(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(stdoutListeners.getSource())), false));
        formatters.add(stdOutChain);
        OutputEventListener stdErrChain = onError(new ProgressLogEventGenerator(new StyledTextOutputBackedRenderer(new StreamingStyledTextOutput(stderrListeners.getSource())), false));
        formatters.add(stdErrChain);
        this.consoleConfigureAction = consoleConfigureAction;
    }

    public ColorMap getColourMap() {
        return colourMap;
    }

    public OutputStream getOriginalStdOut() {
        return originalStdOut;
    }

    public OutputStream getOriginalStdErr() {
        return originalStdErr;
    }

    public void attachConsole(boolean colorOutput) {
        synchronized (lock) {
            colourMap.setUseColor(colorOutput);
            consoleConfigureAction.execute(this);
        }
    }

    public void addStandardOutputAndError() {
        synchronized (lock) {
            originalStdOut = System.out;
            originalStdErr = System.err;
            stdOutListener = new StreamBackedStandardOutputListener((Appendable) System.out);
            stdErrListener = new StreamBackedStandardOutputListener((Appendable) System.err);
            addStandardOutputListener(stdOutListener);
            addStandardErrorListener(stdErrListener);
        }
    }

    public void addOutputEventListener(OutputEventListener listener) {
        formatters.add(listener);
    }

    public void removeOutputEventListener(OutputEventListener listener) {
        formatters.remove(listener);
    }

    public OutputEventRenderer addConsole(Console console, boolean stdout, boolean stderr, ConsoleMetaData consoleMetaData) {
        final OutputEventListener consoleChain = new ConsoleBackedProgressRenderer(
                new ProgressLogEventGenerator(
                        new StyledTextOutputBackedRenderer(console.getMainArea()), true),
                console,
                new DefaultStatusBarFormatter(consoleMetaData));
        synchronized (lock) {
            if (stdout && stderr) {
                formatters.add(consoleChain);
                stdoutListeners.remove(this.stdOutListener);
                stderrListeners.remove(this.stdErrListener);
            } else if (stdout) {
                formatters.add(onNonError(consoleChain));
                stdoutListeners.remove(this.stdOutListener);
            } else {
                formatters.add(onError(consoleChain));
                stderrListeners.remove(this.stdErrListener);
            }
            consoleChain.onOutput(new LogLevelChangeEvent(logLevel));
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
