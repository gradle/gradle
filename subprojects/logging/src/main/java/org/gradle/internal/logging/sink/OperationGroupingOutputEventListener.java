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
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventGroup;
import org.gradle.internal.logging.events.OutputEventGroupListener;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.progress.BuildOperationCategory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An {@code org.gradle.logging.internal.OutputEventListener} implementation which generates output events to log the
 * progress of operations.
 */
public class OperationGroupingOutputEventListener implements OutputEventListener {

    private static final long LONG_RUNNING_TASK_OUTPUT_FLUSH_PERIOD = TimeUnit.SECONDS.toMillis(5);
    private final OutputEventGroupListener listener;
    private final TimeProvider timeProvider;
    private final ScheduledExecutorService executorService;
    private final long bufferFlushPeriodMs;
    private final Object lock = new Object();

    // Maintain a hierarchy of all build operation ids — heads up: this is a *forest*, not just 1 tree
    // FIXME(ew): fix deadlock occurring in gradle-js-plugin during compileGroovy task
    // FIXME(EW): Seems like log messages during configuration are no longer being grouped under a header
    // FIXME(ew): duplicate logging for some things
    private final Map<Object, Object> buildOpIdHierarchy = new HashMap<Object, Object>();
    private final Map<Object, OutputEventGroup> operationsInProgress = new LinkedHashMap<Object, OutputEventGroup>();
    private final Map<Object, List<RenderableOutputEvent>> logBuffers = new LinkedHashMap<Object, List<RenderableOutputEvent>>();
    private final Map<OperationIdentifier, Object> progressToBuildOpIdMap = new HashMap<OperationIdentifier, Object>();

    public OperationGroupingOutputEventListener(OutputEventGroupListener listener, TimeProvider timeProvider) {
        this(listener, timeProvider, Executors.newSingleThreadScheduledExecutor(), LONG_RUNNING_TASK_OUTPUT_FLUSH_PERIOD);
    }

    OperationGroupingOutputEventListener(OutputEventGroupListener listener, TimeProvider timeProvider, ScheduledExecutorService executorService, long bufferFlushPeriodMs) {
        this.listener = listener;
        this.timeProvider = timeProvider;
        this.executorService = executorService;
        this.bufferFlushPeriodMs = bufferFlushPeriodMs;
        flushLogsPeriodically();
    }

    @Override
    public void onOutput(OutputEvent event) {
        synchronized (lock) {
            if (event instanceof ProgressStartEvent) {
                onStart((ProgressStartEvent) event);
            } else if (event instanceof RenderableOutputEvent) {
                handleOutput((RenderableOutputEvent) event);
            } else if (event instanceof ProgressCompleteEvent) {
                onComplete((ProgressCompleteEvent) event);
            } else if (event instanceof EndOutputEvent) {
                onEnd((EndOutputEvent) event);
            } else {
                listener.onOutput(event);
            }
        }
    }

    private void debugLog(String message) {
        listener.onOutput(new LogEvent(timeProvider.getCurrentTime(), "OperationGroupingOutputEventListener", LogLevel.QUIET, message, null));
    }

    private void onStart(ProgressStartEvent startEvent) {
        Object buildOpId = startEvent.getBuildOperationId();
        boolean isGrouped = isGroupedOperation(startEvent.getBuildOperationCategory());
        if (buildOpId != null) {
            buildOpIdHierarchy.put(buildOpId, startEvent.getParentBuildOperationId());
            progressToBuildOpIdMap.put(startEvent.getProgressOperationId(), buildOpId);

            // Create a new group for tasks or configure project
            if (isGrouped) {
                operationsInProgress.put(buildOpId, newEventGroup(startEvent));
                logBuffers.put(buildOpId, new ArrayList<RenderableOutputEvent>());
            }
        }

        // Preserve logging of headers for progress operations started outside of the build operation executor as was done in Gradle 3.x
        // Basically, if we see an operation with a logging header and it's not grouped, just log it
        if (GUtil.isTrue(startEvent.getLoggingHeader()) && !startEvent.getLoggingHeader().equals(startEvent.getShortDescription()) && (buildOpId == null || !isGrouped)) {
            listener.onOutput(new LogEvent(startEvent.getTimestamp(), startEvent.getCategory(), startEvent.getLogLevel(), startEvent.getLoggingHeader(), null, startEvent.getBuildOperationId()));
        }

        listener.onOutput(startEvent);
    }

    private boolean isGroupedOperation(BuildOperationCategory buildOperationCategory) {
        return buildOperationCategory == BuildOperationCategory.TASK || buildOperationCategory == BuildOperationCategory.CONFIGURE_PROJECT;
    }

    private void handleOutput(final RenderableOutputEvent event) {
        final Object logBufferId = getLogBufferId(event.getBuildOperationId());
        if (logBufferId != null) {
            logBuffers.get(logBufferId).add(event);
        } else {
            listener.onOutput(event);
        }
    }

    // Return the id of the operation/group, checking up the build operation hierarchy
    private Object getLogBufferId(@Nullable final Object buildOpId) {
        Object current = buildOpId;
        while (current != null) {
            if (logBuffers.containsKey(current)) {
                return current;
            }
            current = buildOpIdHierarchy.get(current);
        }
        return null;
    }

    private void onComplete(final ProgressCompleteEvent completeEvent) {
        Object buildOpId = progressToBuildOpIdMap.remove(completeEvent.getProgressOperationId());
        if (buildOpId != null) {
            buildOpIdHierarchy.remove(buildOpId);
            OutputEventGroup eventGroup = operationsInProgress.remove(buildOpId);
            if (eventGroup != null) {
                flushGroup(eventGroup, logBuffers.remove(buildOpId), completeEvent.getStatus());
            }
        }

        listener.onOutput(completeEvent);
    }

    private void flushGroup(OutputEventGroup eventGroup, List<RenderableOutputEvent> logs, String status) {
        listener.onOutput(new OutputEventGroup(
            timeProvider.getCurrentTime(),
            eventGroup.getCategory(),
            eventGroup.getLoggingHeader(),
            eventGroup.getDescription(),
            eventGroup.getShortDescription(),
            status,
            logs,
            eventGroup.getBuildOperationId(),
            eventGroup.getBuildOperationCategory()
        ));
    }

    private OutputEventGroup newEventGroup(ProgressStartEvent startEvent) {
        return new OutputEventGroup(
            timeProvider.getCurrentTime(),
            startEvent.getCategory(),
            startEvent.getLoggingHeader(),
            startEvent.getDescription(),
            startEvent.getShortDescription(),
            "",
            new ArrayList<RenderableOutputEvent>(),
            startEvent.getBuildOperationId(),
            startEvent.getBuildOperationCategory()
        );
    }

    private void onEnd(EndOutputEvent event) {
        for (OutputEventGroup group : operationsInProgress.values()) {
            flushGroup(group, logBuffers.get(group.getBuildOperationId()), "");
        }
        listener.onOutput(event);
        buildOpIdHierarchy.clear();
        logBuffers.clear();
        progressToBuildOpIdMap.clear();
    }

    private void flushLogsPeriodically() {
        executorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                synchronized (lock) {
                    flushLogs();
                }
            }
        }, bufferFlushPeriodMs, bufferFlushPeriodMs, TimeUnit.MILLISECONDS);
    }

    private void flushLogs() {
        for (OutputEventGroup operation : operationsInProgress.values()) {
            Object buildOpId = operation.getBuildOperationId();
            List<RenderableOutputEvent> logs = logBuffers.get(buildOpId);
            if (!logs.isEmpty()) {
                flushGroup(operation, logs, "");
                logBuffers.put(buildOpId, new ArrayList<RenderableOutputEvent>());
            }
        }
    }
}
