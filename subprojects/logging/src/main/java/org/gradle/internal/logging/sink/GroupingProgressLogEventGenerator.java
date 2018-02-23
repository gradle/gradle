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

import com.google.common.base.Objects;
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
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.logging.format.LogHeaderFormatter;
import org.gradle.internal.progress.BuildOperationCategory;
import org.gradle.internal.time.Clock;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 */
public class GroupingProgressLogEventGenerator implements OutputEventListener {

    private static final long LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    private final OutputEventListener listener;
    private final Clock clock;
    private final LogHeaderFormatter headerFormatter;
    private final boolean verbose;

    // Maintain a hierarchy of all build operation ids — heads up: this is a *forest*, not just 1 tree
    private final Map<Object, Object> buildOpIdHierarchy = new HashMap<Object, Object>();
    private final Map<Object, OperationGroup> operationsInProgress = new LinkedHashMap<Object, OperationGroup>();
    private final Map<OperationIdentifier, Object> progressToBuildOpIdMap = new HashMap<OperationIdentifier, Object>();

    private Object lastRenderedBuildOpId;

    public GroupingProgressLogEventGenerator(OutputEventListener listener, Clock clock, LogHeaderFormatter headerFormatter, boolean verbose) {
        this.listener = listener;
        this.clock = clock;
        this.headerFormatter = headerFormatter;
        this.verbose = verbose;
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
        } else if (event instanceof UpdateNowEvent) {
            onUpdateNow((UpdateNowEvent) event);
        } else if (!(event instanceof ProgressEvent)) {
            listener.onOutput(event);
        }
    }

    private void onStart(ProgressStartEvent startEvent) {
        Object buildOpId = startEvent.getBuildOperationId();
        boolean isGrouped = isGroupedOperation(startEvent.getBuildOperationCategory());
        if (buildOpId != null) {
            buildOpIdHierarchy.put(buildOpId, startEvent.getParentBuildOperationId());
            progressToBuildOpIdMap.put(startEvent.getProgressOperationId(), buildOpId);

            // Create a new group for tasks or configure project
            if (isGrouped) {
                operationsInProgress.put(buildOpId, new OperationGroup(startEvent.getCategory(), startEvent.getLoggingHeader(), startEvent.getDescription(), startEvent.getShortDescription(), startEvent.getTimestamp(), startEvent.getBuildOperationId(), startEvent.getBuildOperationCategory()));
            }
        }

        // Preserve logging of headers for progress operations started outside of the build operation executor as was done in Gradle 3.x
        // Basically, if we see an operation with a logging header and it's not grouped, just log it
        if (GUtil.isTrue(startEvent.getLoggingHeader()) && !startEvent.getLoggingHeader().equals(startEvent.getShortDescription()) && (buildOpId == null || !isGrouped)) {
            onUngroupedOutput(new LogEvent(startEvent.getTimestamp(), startEvent.getCategory(), startEvent.getLogLevel(), startEvent.getLoggingHeader(), null, startEvent.getBuildOperationId()));
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
            group.setStatus(completeEvent.getStatus(), completeEvent.isFailed());
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

    private void onUpdateNow(UpdateNowEvent event) {
        for (OperationGroup group : operationsInProgress.values()) {
            group.maybeFlushOutput(event.getTimestamp());
        }
    }

    private void onUngroupedOutput(RenderableOutputEvent event) {
        if (lastRenderedBuildOpId != null) {
            listener.onOutput(spacerLine(event.getTimestamp(), event.getCategory()));
            lastRenderedBuildOpId = null;
        }
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

    private static LogEvent spacerLine(long timestamp, String category) {
        return new LogEvent(timestamp, category, null, "", null);
    }

    private class OperationGroup {
        private final String category;
        private final String loggingHeader;
        private long lastUpdateTime;
        private final String description;
        private final String shortDescription;
        private final Object buildOpIdentifier;
        private final BuildOperationCategory buildOperationCategory;

        private String status = "";
        private boolean failed;

        private List<RenderableOutputEvent> bufferedLogs = new ArrayList<RenderableOutputEvent>();

        private OperationGroup(String category, @Nullable String loggingHeader, String description, @Nullable String shortDescription, long startTime, Object buildOpIdentifier, BuildOperationCategory buildOperationCategory) {
            this.category = category;
            this.loggingHeader = loggingHeader;
            this.lastUpdateTime = startTime;
            this.description = description;
            this.shortDescription = shortDescription;
            this.lastUpdateTime = startTime;
            this.buildOpIdentifier = buildOpIdentifier;
            this.buildOperationCategory = buildOperationCategory;
        }

        private StyledTextOutputEvent header() {
            return new StyledTextOutputEvent(lastUpdateTime, category, null, buildOpIdentifier, headerFormatter.format(loggingHeader, description, shortDescription, status, failed));
        }

        private void bufferOutput(RenderableOutputEvent output) {
            // Forward output immediately when the focus is on this operation group
            if (Objects.equal(buildOpIdentifier, lastRenderedBuildOpId)) {
                listener.onOutput(output);
                lastUpdateTime = clock.getCurrentTime();
            } else {
                bufferedLogs.add(output);
            }
        }

        private void flushOutput() {
            if (shouldForward()) {
                if (!buildOpIdentifier.equals(lastRenderedBuildOpId)) {
                    listener.onOutput(header());
                }

                for (RenderableOutputEvent renderableEvent : bufferedLogs) {
                    listener.onOutput(renderableEvent);
                }

                bufferedLogs.clear();
                lastUpdateTime = clock.getCurrentTime();
                lastRenderedBuildOpId = buildOpIdentifier;
            }
        }

        private void maybeFlushOutput(long eventTimestamp) {
            if ((eventTimestamp - lastUpdateTime) > LONG_RUNNING_TASK_OUTPUT_FLUSH_TIMEOUT) {
                flushOutput();
            }
        }

        private void setStatus(String status, boolean failed) {
            this.status = status;
            this.failed = failed;
        }

        private boolean shouldForward() {
            return !bufferedLogs.isEmpty() || (verbose && buildOperationCategory == BuildOperationCategory.TASK);
        }
    }
}
