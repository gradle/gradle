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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.util.GUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.internal.logging.text.StyledTextOutput.Style;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 */
public class ProgressLogEventGenerator implements OutputEventListener {
    private static final String EOL = SystemProperties.getInstance().getLineSeparator();

    private final OutputEventListener listener;
    private final Map<OperationIdentifier, Operation> operations = new LinkedHashMap<OperationIdentifier, Operation>();

    public ProgressLogEventGenerator(OutputEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            onStart((ProgressStartEvent) event);
        } else if (event instanceof ProgressCompleteEvent) {
            onComplete((ProgressCompleteEvent) event);
        } else if (event instanceof RenderableOutputEvent) {
            doOutput((RenderableOutputEvent) event);
        } else if (!(event instanceof ProgressEvent)) {
            listener.onOutput(event);
        }
    }

    private void doOutput(RenderableOutputEvent event) {
        for (Operation operation : operations.values()) {
            operation.completeHeader();
        }
        listener.onOutput(event);
    }

    private void onComplete(ProgressCompleteEvent progressCompleteEvent) {
        if (operations.isEmpty()) {
            return;
        }
        Operation operation = operations.remove(progressCompleteEvent.getProgressOperationId());
        if (operation == null) {
            return;
        }
        completeOperation(progressCompleteEvent, operation);
    }

    private void completeOperation(ProgressCompleteEvent progressCompleteEvent, Operation operation) {
        operation.status = progressCompleteEvent.getStatus();
        operation.completeTime = progressCompleteEvent.getTimestamp();
        operation.complete();
    }

    private void onStart(ProgressStartEvent progressStartEvent) {
        Operation operation = new Operation(progressStartEvent.getCategory(), progressStartEvent.getLoggingHeader(), progressStartEvent.getTimestamp(), progressStartEvent.getBuildOperationId());
        operations.put(progressStartEvent.getProgressOperationId(), operation);
    }

    enum State {None, HeaderStarted, HeaderCompleted, Completed}

    private class Operation {
        private final OperationIdentifier buildOperationIdentifier;
        private final String category;
        private final String loggingHeader;
        private final long startTime;
        private final boolean hasLoggingHeader;
        private String status = "";
        private State state = State.None;
        private long completeTime;

        private Operation(String category, String loggingHeader, long startTime, OperationIdentifier buildOperationIdentifier) {
            this.category = category;
            this.loggingHeader = loggingHeader;
            this.startTime = startTime;
            this.hasLoggingHeader = GUtil.isTrue(loggingHeader);
            this.buildOperationIdentifier = buildOperationIdentifier;
        }

        private StyledTextOutputEvent plainTextEvent(long timestamp, String text) {
            return new StyledTextOutputEvent(timestamp, category, LogLevel.LIFECYCLE, buildOperationIdentifier, Collections.singletonList(new StyledTextOutputEvent.Span(text)));
        }

        private StyledTextOutputEvent styledTextEvent(long timestamp, StyledTextOutputEvent.Span... spans) {
            return new StyledTextOutputEvent(timestamp, category, LogLevel.LIFECYCLE, buildOperationIdentifier, Arrays.asList(spans));
        }

        private void doOutput(RenderableOutputEvent event) {
            for (Operation pending : operations.values()) {
                if (pending == this) {
                    break;
                }
                pending.completeHeader();
            }
            listener.onOutput(event);
        }

        public void startHeader() {
            assert state == State.None;
            if (hasLoggingHeader) {
                state = State.HeaderStarted;
                doOutput(plainTextEvent(startTime, loggingHeader));
            } else {
                state = State.HeaderCompleted;
            }
        }

        public void completeHeader() {
            switch (state) {
                case None:
                    if (hasLoggingHeader) {
                        listener.onOutput(plainTextEvent(startTime, loggingHeader + EOL));
                    }
                    break;
                case HeaderStarted:
                    listener.onOutput(plainTextEvent(startTime, EOL));
                    break;
                case HeaderCompleted:
                    return;
                default:
                    throw new IllegalStateException("state is " + state);
            }
            state = State.HeaderCompleted;
        }

        public void complete() {
            boolean hasStatus = GUtil.isTrue(status);
            switch (state) {
                case None:
                    if (hasLoggingHeader && hasStatus) {
                        doOutput(styledTextEvent(completeTime,
                            new StyledTextOutputEvent.Span(loggingHeader + ' '),
                            new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                            new StyledTextOutputEvent.Span(EOL)));
                    } else if (hasLoggingHeader) {
                        doOutput(plainTextEvent(completeTime, loggingHeader + EOL));
                    }
                    break;
                case HeaderStarted:
                    assert hasLoggingHeader;
                    if (hasStatus) {
                        doOutput(styledTextEvent(completeTime,
                            new StyledTextOutputEvent.Span(" "),
                            new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                            new StyledTextOutputEvent.Span(EOL)));
                    } else {
                        doOutput(plainTextEvent(completeTime, EOL));
                    }
                    break;
                case HeaderCompleted:
                    if (hasLoggingHeader && hasStatus) {
                        doOutput(styledTextEvent(completeTime,
                            new StyledTextOutputEvent.Span(loggingHeader + ' '),
                            new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                            new StyledTextOutputEvent.Span(EOL)));
                    }
                    break;
                default:
                    throw new IllegalStateException("state is " + state);
            }
            state = State.Completed;
        }
    }
}
