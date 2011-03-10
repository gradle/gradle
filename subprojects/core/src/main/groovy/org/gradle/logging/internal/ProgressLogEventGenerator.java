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
import org.gradle.util.SystemProperties;

import java.util.LinkedList;

import static org.gradle.logging.StyledTextOutput.Style;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 */
public class ProgressLogEventGenerator implements OutputEventListener {
    private static final String EOL = SystemProperties.getLineSeparator();

    private final OutputEventListener listener;
    private final boolean deferHeader;
    private final LinkedList<Operation> operations = new LinkedList<Operation>();

    public ProgressLogEventGenerator(OutputEventListener listener, boolean deferHeader) {
        this.listener = listener;
        this.deferHeader = deferHeader;
    }

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
        for (Operation operation : operations) {
            operation.completeHeader();
        }
        listener.onOutput(event);
    }

    private void onComplete(ProgressCompleteEvent progressCompleteEvent) {
        assert !operations.isEmpty();
        Operation operation = operations.removeLast();
        operation.status = progressCompleteEvent.getStatus();
        operation.completeTime = progressCompleteEvent.getTimestamp();
        operation.complete();
    }

    private void onStart(ProgressStartEvent progressStartEvent) {
        Operation operation = new Operation();
        operation.category = progressStartEvent.getCategory();
        operation.description = progressStartEvent.getDescription();
        operation.startTime = progressStartEvent.getTimestamp();
        operation.status = "";
        operations.add(operation);

        if (!deferHeader) {
            operation.startHeader();
        }
    }

    enum State {None, HeaderStarted, HeaderCompleted, Completed}

    private class Operation {
        private String category;
        private String description;
        private String status;
        private State state = State.None;
        public long startTime;
        public long completeTime;

        public String getDescription() {
            return description;
        }

        public String getStatus() {
            return status;
        }

        private void doOutput(RenderableOutputEvent event) {
            for (Operation pending : operations) {
                if (pending == this) {
                    break;
                }
                pending.completeHeader();
            }
            listener.onOutput(event);
        }

        public void startHeader() {
            assert state == State.None;
            boolean hasDescription = description.length() > 0;
            if (hasDescription) {
                state = State.HeaderStarted;
                doOutput(new StyledTextOutputEvent(startTime, category, LogLevel.LIFECYCLE, description));
            } else {
                state = State.HeaderCompleted;
            }
        }

        public void completeHeader() {
            boolean hasDescription = description.length() > 0;
            switch (state) {
                case None:
                    if (hasDescription) {
                        listener.onOutput(new StyledTextOutputEvent(startTime, category, LogLevel.LIFECYCLE, description + EOL));
                    }
                    break;
                case HeaderStarted:
                    listener.onOutput(new StyledTextOutputEvent(startTime, category, LogLevel.LIFECYCLE, EOL));
                    break;
                case HeaderCompleted:
                    return;
                default:
                    throw new IllegalStateException();
            }
            state = State.HeaderCompleted;
        }

        public void complete() {
            boolean hasStatus = status.length() > 0;
            boolean hasDescription = description.length() > 0;
            switch (state) {
                case None:
                    if (hasDescription && hasStatus) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE,
                                new StyledTextOutputEvent.Span(description + ' '),
                                new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                                new StyledTextOutputEvent.Span(EOL)));
                    } else if (hasDescription) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE, description + EOL));
                    } else if (hasStatus) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE,
                                new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                                new StyledTextOutputEvent.Span(EOL)));
                    }
                    break;
                case HeaderStarted:
                    if (hasStatus) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE,
                                new StyledTextOutputEvent.Span(" "),
                                new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                                new StyledTextOutputEvent.Span(EOL)));
                    } else {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE, EOL));
                    }
                    break;
                case HeaderCompleted:
                    if (hasDescription && hasStatus) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE,
                                new StyledTextOutputEvent.Span(description + ' '),
                                new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                                new StyledTextOutputEvent.Span(EOL)));
                    } else if (hasStatus) {
                        doOutput(new StyledTextOutputEvent(completeTime, category, LogLevel.LIFECYCLE, 
                                new StyledTextOutputEvent.Span(Style.ProgressStatus, status),
                                new StyledTextOutputEvent.Span(EOL)));
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
            state = State.Completed;
        }
    }
}
