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

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.progress.BuildOperationCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 */
public class GroupingProgressLogEventGenerator extends BatchOutputEventListener {
    static final String EOL = SystemProperties.getInstance().getLineSeparator();

    private final OutputEventListener listener;

    // Maintain a hierarchy of all build operation ids — heads up: this is a *forest*, not just 1 tree
    private final Map<Object, Object> buildOpIdHierarchy = new HashMap<Object, Object>();
    private final Map<Object, OperationGroup> operationsInProgress = new LinkedHashMap<Object, OperationGroup>();
    private final Map<OperationIdentifier, Object> progressToBuildOpIdMap = new HashMap<OperationIdentifier, Object>();

    private Object lastRenderedBuildOpId;
    private boolean needsHeader;

    public GroupingProgressLogEventGenerator(OutputEventListener listener) {
        this.listener = listener;
    }

    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            onStart((ProgressStartEvent) event);
        } else if (event instanceof RenderableOutputEvent) {
            handleOutput((RenderableOutputEvent) event);
        } else if (event instanceof ProgressCompleteEvent) {
            onComplete((ProgressCompleteEvent) event);
        } else if (event instanceof EndOutputEvent) {
            onEnd((EndOutputEvent) event);
        } else if (!(event instanceof ProgressEvent)) {
            listener.onOutput(event);
        }
    }

    private void onStart(ProgressStartEvent startEvent) {
        Object buildOpId = startEvent.getBuildOperationId();
        if (buildOpId != null) {
            buildOpIdHierarchy.put(buildOpId, startEvent.getParentBuildOperationId());
            progressToBuildOpIdMap.put(startEvent.getProgressOperationId(), buildOpId);

            // Create a new group for tasks or configure project
            if (isGroupedOperation(startEvent.getBuildOperationCategory())) {
                String header = startEvent.getLoggingHeader() != null ? startEvent.getLoggingHeader() : startEvent.getDescription();
                operationsInProgress.put(buildOpId, new OperationGroup(startEvent.getCategory(), header, startEvent.getTimestamp(), startEvent.getBuildOperationId()));
            }
        }
    }

    private boolean isGroupedOperation(BuildOperationCategory buildOperationCategory) {
        return buildOperationCategory == BuildOperationCategory.TASK || buildOperationCategory == BuildOperationCategory.CONFIGURE_PROJECT;
    }

    private void handleOutput(RenderableOutputEvent event) {
        Object operationId = getOperationId(event.getBuildOperationId());
        if (operationId != null) {
            operationsInProgress.get(operationId).bufferOutput(event);
        } else {
            onUngroupedOutput(event);
        }
    }

    private void onComplete(ProgressCompleteEvent completeEvent) {
        Object buildOpId = progressToBuildOpIdMap.remove(completeEvent.getProgressOperationId());
        buildOpIdHierarchy.remove(buildOpId);
        OperationGroup group = operationsInProgress.remove(buildOpId);
        if (group != null) {
            group.flushOutput();
        }
    }

    private void onEnd(EndOutputEvent event) {
        for (OperationGroup group : operationsInProgress.values()) {
            group.flushOutput();
        }
        listener.onOutput(event);
        buildOpIdHierarchy.clear();
        operationsInProgress.clear();
        progressToBuildOpIdMap.clear();
    }

    private void onUngroupedOutput(RenderableOutputEvent event) {
        needsHeader = true;
        listener.onOutput(event);
    }

    // Return the id of the operation/group, checking up the build operation hierarchy
    private Object getOperationId(@Nullable final Object buildOpId) {
        Object current = buildOpId;
        while (current != null) {
            if (operationsInProgress.containsKey(current)) {
                return current;
            }
            current = buildOpIdHierarchy.get(current);
        }
        return null;
    }

    private class OperationGroup {
        private final Object buildOpIdentifier;
        private final String category;
        private final String loggingHeader;
        private final long startTime;

        private List<RenderableOutputEvent> bufferedLogs = new ArrayList<RenderableOutputEvent>();

        private OperationGroup(String category, @Nullable String loggingHeader, long startTime, Object buildOpIdentifier) {
            this.category = category;
            this.loggingHeader = loggingHeader;
            this.startTime = startTime;
            this.buildOpIdentifier = buildOpIdentifier;
        }

        private LogEvent spacerLine() {
            return new LogEvent(startTime, category, null, "", null);
        }

        StyledTextOutputEvent header(final String message) {
            List<StyledTextOutputEvent.Span> spans = Lists.newArrayList(new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, "> " + message), new StyledTextOutputEvent.Span(EOL));
            return new StyledTextOutputEvent(startTime, category, null, buildOpIdentifier, spans);
        }

        void bufferOutput(RenderableOutputEvent output) {
            bufferedLogs.add(output);
        }

        void flushOutput() {
            if (!bufferedLogs.isEmpty()) {
                // Visually indicate group by adding surrounding lines
                if (needsHeader) {
                    listener.onOutput(spacerLine());
                    needsHeader = false;
                }
                listener.onOutput(header(loggingHeader));

                for (RenderableOutputEvent renderableEvent : bufferedLogs) {
                    listener.onOutput(renderableEvent);
                }

                // Visually indicate a new group by adding a line if not appending to last rendered group
                if (!buildOpIdentifier.equals(lastRenderedBuildOpId)) {
                    listener.onOutput(spacerLine());
                }
                lastRenderedBuildOpId = buildOpIdentifier;
            }
        }
    }
}
