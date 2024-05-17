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
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.logging.format.LogHeaderFormatter;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 *
 * <p>This listener forwards nothing unless it receives periodic {@link UpdateNowEvent} clock events.</p>
 */
public class GroupingProgressLogEventGenerator implements OutputEventListener {
    private static final long HIGH_WATERMARK_FLUSH_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static final long LOW_WATERMARK_FLUSH_TIMEOUT = TimeUnit.SECONDS.toMillis(2);
    private final OutputEventListener listener;
    private final LogHeaderFormatter headerFormatter;
    private final boolean verbose;

    // Maintain a hierarchy of all progress operations in progress — heads up: this is a *forest*, not just 1 tree
    private final Map<OperationIdentifier, OperationState> operationsInProgress = new LinkedHashMap<OperationIdentifier, OperationState>();

    private Object lastRenderedBuildOpId;
    private boolean needHeaderSeparator;
    private long currentTimePeriod;

    public GroupingProgressLogEventGenerator(OutputEventListener listener, LogHeaderFormatter headerFormatter, boolean verbose) {
        this.listener = listener;
        this.headerFormatter = headerFormatter;
        this.verbose = verbose;
    }

    @Override
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
        boolean isGrouped = startEvent.getBuildOperationCategory().isGrouped();
        OperationIdentifier progressId = startEvent.getProgressOperationId();
        if (startEvent.isBuildOperationStart() && isGrouped) {
            // Create a new group for tasks or configure project
            operationsInProgress.put(progressId, new OperationGroup(startEvent.getCategory(), startEvent.getDescription(), startEvent.getTimestamp(), startEvent.getParentProgressOperationId(), progressId, startEvent.getBuildOperationCategory()));
        } else {
            operationsInProgress.put(progressId, new OperationState(startEvent.getParentProgressOperationId(), progressId));
        }

        // Preserve logging of headers for progress operations started outside of the build operation executor as was done in Gradle 3.x
        // Basically, if we see an operation with a logging header and it's not grouped, just log it
        if (!isGrouped && GUtil.isTrue(startEvent.getLoggingHeader())) {
            onUngroupedOutput(new LogEvent(startEvent.getTimestamp(), startEvent.getCategory(), startEvent.getLogLevel(), startEvent.getLoggingHeader(), null, null));
        }
    }

    private void handleOutput(RenderableOutputEvent event) {
        OperationGroup group = getGroupFor(event.getBuildOperationId());
        if (group != null) {
            group.bufferOutput(event);
        } else {
            onUngroupedOutput(event);
        }
    }

    private void onComplete(ProgressCompleteEvent completeEvent) {
        OperationState state = operationsInProgress.remove(completeEvent.getProgressOperationId());
        if (state instanceof OperationGroup) {
            OperationGroup group = (OperationGroup) state;
            group.setStatus(completeEvent.getStatus(), completeEvent.isFailed());
            group.flushOutput();
            if (group.hasForeground()) {
                lastRenderedBuildOpId = null;
            }
        }
    }

    private void onEnd(EndOutputEvent event) {
        for (OperationState state: operationsInProgress.values()) {
            state.flushOutput();
        }
        listener.onOutput(event);
        operationsInProgress.clear();
    }

    private void onUpdateNow(UpdateNowEvent event) {
        currentTimePeriod = event.getTimestamp();
        for (OperationState state: operationsInProgress.values()) {
            state.maybeFlushOutput(event.getTimestamp());
        }
    }

    private void onUngroupedOutput(RenderableOutputEvent event) {
        if (lastRenderedBuildOpId != null) {
            listener.onOutput(spacerLine(event.getTimestamp(), event.getCategory()));
            lastRenderedBuildOpId = null;
            needHeaderSeparator = true;
        }
        listener.onOutput(event);
    }

    // Return the group to use for the given build operation, searching up the build operation hierarchy for the first group
    private OperationGroup getGroupFor(@Nullable final OperationIdentifier progressId) {
        OperationIdentifier current = progressId;
        while (current != null) {
            OperationState state = operationsInProgress.get(current);
            if (state == null) {
                // This shouldn't be the case, however, start and complete events are filtered in the prior stage when the logging level is > lifecycle
                // Should instead move the filtering after this stage
                break;
            }
            if (state instanceof OperationGroup) {
                return (OperationGroup) state;
            }
            current = state.parentProgressOp;
        }
        return null;
    }

    private static LogEvent spacerLine(long timestamp, String category) {
        return new LogEvent(timestamp, category, LogLevel.LIFECYCLE, "", null);
    }

    private static class OperationState {
        final @Nullable
        OperationIdentifier parentProgressOp;
        final OperationIdentifier buildOpIdentifier;

        OperationState(@Nullable OperationIdentifier parentProgressOp, OperationIdentifier buildOpIdentifier) {
            this.parentProgressOp = parentProgressOp;
            this.buildOpIdentifier = buildOpIdentifier;
        }

        void flushOutput() {
        }

         // Used in subclasses
        void maybeFlushOutput(@SuppressWarnings("UnusedVariable") long timestamp) {
        }
    }

    private class OperationGroup extends OperationState {
        private final String category;
        private long lastUpdateTime;
        private final String description;
        private final BuildOperationCategory buildOperationCategory;

        private String status = "";
        private String lastHeaderStatus = "";
        private boolean failed;
        private boolean headerSent;
        private boolean outputRendered;

        private List<RenderableOutputEvent> bufferedLogs = new ArrayList<RenderableOutputEvent>();

        OperationGroup(String category, String description, long startTime, @Nullable OperationIdentifier parentBuildOp, OperationIdentifier buildOpIdentifier, BuildOperationCategory buildOperationCategory) {
            super(parentBuildOp, buildOpIdentifier);
            this.category = category;
            this.lastUpdateTime = startTime;
            this.description = description;
            this.buildOperationCategory = buildOperationCategory;
        }

        private StyledTextOutputEvent header() {
            return new StyledTextOutputEvent(lastUpdateTime, category, LogLevel.LIFECYCLE, buildOpIdentifier, headerFormatter.format(description, status, failed));
        }

        void bufferOutput(RenderableOutputEvent output) {
            // Forward output immediately when the focus is on this operation group
            if (Objects.equal(buildOpIdentifier, lastRenderedBuildOpId)) {
                listener.onOutput(output);
                lastUpdateTime = currentTimePeriod;
                needHeaderSeparator = true;
            } else {
                bufferedLogs.add(output);
            }
        }

        @Override
        void flushOutput() {
            if (shouldForward()) {
                boolean hasContent = !bufferedLogs.isEmpty();
                if (!hasForeground() || statusHasChanged()) {
                    if (needHeaderSeparator || hasContent) {
                        listener.onOutput(spacerLine(lastUpdateTime, category));
                    }
                    listener.onOutput(header());
                    headerSent = true;
                    lastHeaderStatus = status;
                }

                for (RenderableOutputEvent renderableEvent: bufferedLogs) {
                    outputRendered = true;
                    listener.onOutput(renderableEvent);
                }
                GroupingProgressLogEventGenerator.this.needHeaderSeparator = hasContent;

                bufferedLogs.clear();
                lastUpdateTime = currentTimePeriod;
                lastRenderedBuildOpId = buildOpIdentifier;
            }
        }

        @Override
        void maybeFlushOutput(long eventTimestamp) {
            if (timeoutExpired(eventTimestamp, HIGH_WATERMARK_FLUSH_TIMEOUT) || (timeoutExpired(eventTimestamp, LOW_WATERMARK_FLUSH_TIMEOUT) && canClaimForeground())) {
                flushOutput();
            }
        }

        private boolean timeoutExpired(long eventTimestamp, long timeout) {
            return (eventTimestamp - lastUpdateTime) > timeout;
        }

        private boolean canClaimForeground() {
            return hasForeground() || (!bufferedLogs.isEmpty() && lastRenderedBuildOpId == null);
        }

        private boolean hasForeground() {
            return buildOpIdentifier.equals(lastRenderedBuildOpId);
        }

        private boolean statusHasChanged() {
            return !status.equals(lastHeaderStatus);
        }

        private void setStatus(String status, boolean failed) {
            this.status = status;
            this.failed = failed;
        }

        private boolean shouldPrintHeader() {
            // Print the header if:
            //   we're in verbose mode OR we're in rich mode and some output has already been rendered
            //   AND
            //   we haven't displayed the header yet OR we've displayed the header but the status has since changed
            return (verbose || outputRendered) && (!headerSent || statusHasChanged());
        }

        private boolean statusIsFailed() {
            return failed && statusHasChanged();
        }

        private boolean shouldForward() {
            return !bufferedLogs.isEmpty() || (buildOperationCategory.isShowHeader() && (shouldPrintHeader() || statusIsFailed()));
        }
    }
}
