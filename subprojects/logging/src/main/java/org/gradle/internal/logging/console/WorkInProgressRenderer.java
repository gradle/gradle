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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.logging.console.BuildStatusRenderer.BUILD_PROGRESS_CATEGORY;

public class WorkInProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final ProgressOperations operations = new ProgressOperations();
    private final BuildProgressArea progressArea;
    private final DefaultWorkInProgressFormatter labelFormatter;
    private final ConsoleLayoutCalculator consoleLayoutCalculator;

    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();

    // Track all unused labels to display future progress operation
    private final Deque<StyledLabel> unusedProgressLabels;

    // Track currently associated label with its progress operation
    private final Map<OperationIdentifier, AssociationLabel> operationIdToAssignedLabels = new HashMap<OperationIdentifier, AssociationLabel>();

    // Track any progress operation that either can't be display due to label shortage or child progress operation is already been displayed
    private final Deque<ProgressOperation> unassignedProgressOperations = new ArrayDeque<ProgressOperation>();

    public WorkInProgressRenderer(OutputEventListener listener, BuildProgressArea progressArea, DefaultWorkInProgressFormatter labelFormatter, ConsoleLayoutCalculator consoleLayoutCalculator) {
        this.listener = listener;
        this.progressArea = progressArea;
        this.labelFormatter = labelFormatter;
        this.consoleLayoutCalculator = consoleLayoutCalculator;
        this.unusedProgressLabels = new ArrayDeque<StyledLabel>(progressArea.getBuildProgressLabels());
    }

    @Override
    public void onOutput(OutputEvent event) {
        queue.add(event);

        if (event instanceof UpdateNowEvent) {
            renderNow();
        } else if (event instanceof EndOutputEvent) {
            progressArea.setVisible(false);
        }

        listener.onOutput(event);
    }

    // Transform ProgressCompleteEvent into their corresponding progress OperationIdentifier.
    private Set<OperationIdentifier> toOperationIdSet(Iterable<ProgressCompleteEvent> events) {
        return Sets.newHashSet(Iterables.transform(events, new Function<ProgressCompleteEvent, OperationIdentifier>() {
            @Override
            public OperationIdentifier apply(ProgressCompleteEvent event) {
                return event.getProgressOperationId();
            }
        }));
    }

    private void resizeTo(int newBuildProgressLabelCount) {
        int previousBuildProgressLabelCount = progressArea.getBuildProgressLabels().size();
        newBuildProgressLabelCount = consoleLayoutCalculator.calculateNumWorkersForConsoleDisplay(newBuildProgressLabelCount);
        if (previousBuildProgressLabelCount >= newBuildProgressLabelCount) {
            // We don't support shrinking at the moment
            return;
        }

        progressArea.resizeBuildProgressTo(newBuildProgressLabelCount);

        // Add new labels to the unused queue
        for (int i = newBuildProgressLabelCount - 1; i >= previousBuildProgressLabelCount; --i) {
            unusedProgressLabels.push(progressArea.getBuildProgressLabels().get(i));
        }
    }

    private void attach(ProgressOperation operation) {
        if (operation.hasChildren() || !isRenderable(operation)) {
            return;
        }

        // Reuse parent label if possible
        if (operation.getParent() != null) {
            detach(operation.getParent().getOperationId());
        }

        // No more unused label? Try to resize.
        if (unusedProgressLabels.isEmpty()) {
            int newValue = operationIdToAssignedLabels.size() + 1;
            resizeTo(newValue);
            // At this point, the work-in-progress area may or may not have been resized due to maximum size constraint.
        }

        // Try to use a new label
        if (unusedProgressLabels.isEmpty()) {
            unassignedProgressOperations.add(operation);
        } else {
            attach(operation, unusedProgressLabels.pop());
        }
    }

    private void attach(ProgressOperation operation, StyledLabel label) {
        AssociationLabel association = new AssociationLabel(operation, label);
        operationIdToAssignedLabels.put(operation.getOperationId(), association);
    }

    private void detach(ProgressOperation operation) {
        if (!isRenderable(operation)) {
            return;
        }

        detach(operation.getOperationId());
        unassignedProgressOperations.remove(operation);

        if (operation.getParent() != null && isRenderable(operation.getParent())) {
            attach(operation.getParent());
        } else if (!unassignedProgressOperations.isEmpty()) {
            attach(unassignedProgressOperations.pop());
        }
    }

    private void detach(OperationIdentifier operationId) {
        AssociationLabel association = operationIdToAssignedLabels.remove(operationId);
        if (association != null) {
            unusedProgressLabels.push(association.label);
        }
    }

    // Any ProgressOperation in the parent chain has a message, the operation is considered renderable.
    private boolean isRenderable(ProgressOperation operation) {
        for (ProgressOperation current = operation;
             current != null && !BUILD_PROGRESS_CATEGORY.equals(current.getCategory());
             current = current.getParent()) {
            if (current.getMessage() != null) {
                return true;
            }
        }

        return false;
    }

    private void renderNow() {
        if (queue.isEmpty()) {
            return;
        }

        // Skip processing of any operations that both start and complete in the queue
        Set<OperationIdentifier> completeEventOperationIds = toOperationIdSet(Iterables.filter(queue, ProgressCompleteEvent.class));
        Set<OperationIdentifier> operationIdsToSkip = new HashSet<OperationIdentifier>();

        for (OutputEvent event : queue) {
            if (event instanceof ProgressStartEvent) {
                progressArea.setVisible(true);
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                if (completeEventOperationIds.contains(startEvent.getProgressOperationId())) {
                    operationIdsToSkip.add(startEvent.getProgressOperationId());
                    // Don't attach to any labels
                } else {
                    attach(operations.start(startEvent.getShortDescription(), startEvent.getStatus(), startEvent.getCategory(), startEvent.getProgressOperationId(), startEvent.getParentProgressOperationId()));
                }
            } else if (event instanceof ProgressCompleteEvent) {
                ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
                if (!operationIdsToSkip.contains(completeEvent.getProgressOperationId())) {
                    detach(operations.complete(completeEvent.getProgressOperationId()));
                }
            } else if (event instanceof ProgressEvent) {
                ProgressEvent progressEvent = (ProgressEvent) event;
                if (!operationIdsToSkip.contains(progressEvent.getProgressOperationId())) {
                    operations.progress(progressEvent.getStatus(), progressEvent.getProgressOperationId());
                }
            }
        }
        queue.clear();

        for (AssociationLabel associatedLabel : operationIdToAssignedLabels.values()) {
            associatedLabel.renderNow();
        }
        for (StyledLabel emptyLabel : unusedProgressLabels) {
            emptyLabel.setText(labelFormatter.format());
        }
    }

    private class AssociationLabel {
        final ProgressOperation operation;
        final StyledLabel label;

        AssociationLabel(ProgressOperation operation, StyledLabel label) {
            this.operation = operation;
            this.label = label;
        }

        void renderNow() {
            label.setText(labelFormatter.format(operation));
        }
    }
}
