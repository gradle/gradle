/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console;

import com.google.common.collect.Lists;
import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.PhaseProgressStartEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.RenderableOutputEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GroupedBuildOperationRenderer extends BatchOutputEventListener {

    // FIXME(ew): This breaks OutputEventRendererTest.rendersLogEventsInConsoleWhenLogLevelIsDebug() — perhaps everything is being output as LogEvents from this thing?
    static final int SCHEDULER_INITIAL_DELAY_MS = 100;
    static final int SCHEDULER_CHECK_PERIOD_MS = 5000;
    private final BatchOutputEventListener listener;
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private final Map<OperationIdentifier, Object> progressIdToBuildOperationIdMap = new LinkedHashMap<OperationIdentifier, Object>();
    private final Map<Object, List<OutputEvent>> groupedTaskBuildOperations = new LinkedHashMap<Object, List<OutputEvent>>();
    private final RenderState renderState = new RenderState();

    public GroupedBuildOperationRenderer(BatchOutputEventListener listener) {
        this(listener, true);
    }

    GroupedBuildOperationRenderer(BatchOutputEventListener listener, boolean enableScheduler) {
        this.listener = listener;
        executor = Executors.newSingleThreadScheduledExecutor();

        if (enableScheduler) {
            scheduleTimedEventForwarding();
        }
    }

    private void scheduleTimedEventForwarding() {
        executor.scheduleAtFixedRate(new ForwardingOutputEventRunnable(), SCHEDULER_INITIAL_DELAY_MS, SCHEDULER_CHECK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOutput(OutputEvent event) {
        synchronized (lock) {
            if (event instanceof ProgressStartEvent) {
                onStart((ProgressStartEvent) event);
            } else if (event instanceof ProgressCompleteEvent) {
                onComplete((ProgressCompleteEvent) event);
            } else if (event instanceof RenderableOutputEvent) {
                onRenderable((RenderableOutputEvent) event);
            } else if (event instanceof EndOutputEvent) {
                onEnd((EndOutputEvent) event);
            }
        }
    }

    private void onStart(ProgressStartEvent event) {
        // FIXME(ew): logic here is surely off
        if (event.getBuildOperationId() != null && !(event instanceof PhaseProgressStartEvent)) {
            progressIdToBuildOperationIdMap.put(event.getProgressOperationId(), event.getBuildOperationId());
            groupedTaskBuildOperations.put(event.getBuildOperationId(), Lists.newArrayList((OutputEvent) event));
        } else {
            forwardEvent(event);
        }
    }

    private void onComplete(ProgressCompleteEvent event) {
        Object operationId = null;
        if (progressIdToBuildOperationIdMap.containsKey(event.getProgressOperationId())) {
            operationId = progressIdToBuildOperationIdMap.get(event.getProgressOperationId());
            if (groupedTaskBuildOperations.containsKey(operationId)) {
                List<OutputEvent> outputEvents = groupedTaskBuildOperations.get(operationId);

                if (renderState.isCurrentlyRendered(operationId)) {
                    List<OutputEvent> outputEventsWithoutHeader = outputEvents.subList(1, outputEvents.size());
                    forwardBatchedEvents(outputEventsWithoutHeader);
                    renderState.clearCurrentlyRendered(operationId);
                } else {
                    forwardBatchedEvents(outputEvents);
                }

                forwardEvent(event);
                groupedTaskBuildOperations.remove(operationId);
            }
        }

        if (groupedTaskBuildOperations.containsKey(operationId)) {
            List<OutputEvent> outputEvents = groupedTaskBuildOperations.get(operationId);

            if (renderState.isCurrentlyRendered(operationId)) {
                List<OutputEvent> outputEventsWithoutHeader = outputEvents.subList(1, outputEvents.size());
                forwardBatchedEvents(outputEventsWithoutHeader);
                renderState.clearCurrentlyRendered(operationId);
            } else {
                forwardBatchedEvents(outputEvents);
            }

            forwardEvent(event);
            groupedTaskBuildOperations.remove(operationId);
        } else {
            forwardEvent(event);
        }
    }

    private void onRenderable(RenderableOutputEvent event) {
        Object buildOperationId = event.getBuildOperationId();
        if (buildOperationId != null && groupedTaskBuildOperations.containsKey(buildOperationId)) {
            List<OutputEvent> outputEvents = groupedTaskBuildOperations.get(buildOperationId);
            outputEvents.add(event);
        } else {
            forwardEvent(event);
        }
    }

    private void onEnd(EndOutputEvent event) {
        forwardEvent(event);
        executor.shutdown();
        groupedTaskBuildOperations.clear();
        renderState.clear();
    }

    private void forwardEvent(OutputEvent event) {
        listener.onOutput(event);
    }

    private void forwardBatchedEvents(Iterable<OutputEvent> events) {
        listener.onOutput(events);
    }

    private class ForwardingOutputEventRunnable implements Runnable {

        @Override
        public void run() {
            synchronized (lock) {
                Iterator<Map.Entry<Object, List<OutputEvent>>> entryIterator = groupedTaskBuildOperations.entrySet().iterator();
                Map.Entry<Object, List<OutputEvent>> lastEntry = null;

                while (entryIterator.hasNext()) {
                    lastEntry = entryIterator.next();
                    forwardOutputEvents(lastEntry);
                }

                if (lastEntry != null) {
                    renderState.setCurrentlyRendered(lastEntry.getKey());
                }
            }
        }

        private void forwardOutputEvents(Map.Entry<Object, List<OutputEvent>> groupedEvents) {
            List<OutputEvent> originalOutputEvents = groupedEvents.getValue();
            List<OutputEvent> outputEventsWithoutHeader = getOutputEventsWithoutHeader(originalOutputEvents);

            if (renderState.isCurrentlyRendered(groupedEvents.getKey())) {
                // Only forward output events if there's more than just the header
                if (!outputEventsWithoutHeader.isEmpty()) {
                    forwardBatchedEvents(outputEventsWithoutHeader);
                }
            } else {
                forwardBatchedEvents(originalOutputEvents);
            }

            outputEventsWithoutHeader.clear();
        }

        private List<OutputEvent> getOutputEventsWithoutHeader(List<OutputEvent> outputEvents) {
            return outputEvents.subList(1, outputEvents.size());
        }
    }

    private static class RenderState {

        private Object currentlyRendered;

        public Object getCurrentlyRendered() {
            return currentlyRendered;
        }

        public void setCurrentlyRendered(Object operationId) {
            currentlyRendered = operationId;
        }

        public boolean isCurrentlyRendered(Object operationId) {
            return currentlyRendered != null && currentlyRendered.equals(operationId);
        }

        public void clearCurrentlyRendered(Object operationId) {
            if (isCurrentlyRendered(operationId)) {
                clear();
            }
        }

        public void clear() {
            currentlyRendered = null;
        }
    }

    Map<Object, List<OutputEvent>> getGroupedTaskBuildOperations() {
        return groupedTaskBuildOperations;
    }

    RenderState getRenderState() {
        return renderState;
    }
}
