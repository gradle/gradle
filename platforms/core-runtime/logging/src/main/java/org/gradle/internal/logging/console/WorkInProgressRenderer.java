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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkInProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final ProgressOperations operations = new ProgressOperations();
    private final BuildProgressArea progressArea;
    private final DefaultWorkInProgressFormatter labelFormatter;
    private final ConsoleLayoutCalculator consoleLayoutCalculator;

    private final List<OutputEvent> queue = new ArrayList<>();

    // Track all unused labels to display future progress operation
    private final Deque<StyledLabel> unusedProgressLabels;

    // Track currently associated label with its progress operation
    private final Map<OperationIdentifier, AssociationLabel> operationIdToAssignedLabels = new HashMap<>();

    // Track any progress operation that either can't be display due to label shortage or child progress operation is already been displayed
    private final Deque<ProgressOperation> unassignedProgressOperations = new ArrayDeque<>();

    public WorkInProgressRenderer(OutputEventListener listener, BuildProgressArea progressArea, DefaultWorkInProgressFormatter labelFormatter, ConsoleLayoutCalculator consoleLayoutCalculator) {
        this.listener = listener;
        this.progressArea = progressArea;
        this.labelFormatter = labelFormatter;
        this.consoleLayoutCalculator = consoleLayoutCalculator;
        this.unusedProgressLabels = new ArrayDeque<>(progressArea.getBuildProgressLabels());
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
        return Sets.newHashSet(Iterables.transform(events, ProgressCompleteEvent::getProgressOperationId));
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
        if (operation.hasChildrenOtherThanWorkActions() || !isRenderable(operation)) {
            return;
        }

        // Don't show the parent operation while a child is visible, except for a work action, which is shown next to its parent
        // Instead, reuse the parent label, if any, for the child
        if (operation.getParent() != null && !operation.isWorkAction()) {
            unshow(operation.getParent());
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
            reportLinesNotShown();
        } else {
            attach(operation, unusedProgressLabels.pop());
        }
    }

    private void attach(ProgressOperation operation, StyledLabel label) {
        AssociationLabel association = new AssociationLabel(operation, label);
        operationIdToAssignedLabels.put(operation.getOperationId(), association);
    }

    // The work actions of an operation are shown from its second one on, including the ones that already run
    private void showWorkActions(@Nullable ProgressOperation parent) {
        if (parent == null || !parent.showsWorkActions()) {
            return;
        }
        for (ProgressOperation workAction : parent.getWorkActions()) {
            if (!isShownOrWaiting(workAction)) {
                attach(workAction);
            }
        }
    }

    private boolean isShownOrWaiting(ProgressOperation operation) {
        return operationIdToAssignedLabels.containsKey(operation.getOperationId()) || unassignedProgressOperations.contains(operation);
    }

    // Declares that we're not following updates from this ProgressOperation anymore
    private void detach(ProgressOperation operation) {
        if (!isRenderable(operation)) {
            return;
        }
        // A work action that was not shown frees no line
        if (operation.isWorkAction() && !isShownOrWaiting(operation)) {
            return;
        }

        unshow(operation);

        // The parent of a work action is still shown
        if (!operation.isWorkAction() && operation.getParent() != null && isRenderable(operation.getParent())) {
            attach(operation.getParent());
        } else if (!unassignedProgressOperations.isEmpty()) {
            attach(unassignedProgressOperations.pop());
            reportLinesNotShown();
        }
    }

    // Declares that we are stopping showing updates from this ProgressOperation.
    // We might be completely done following this ProgressOperation, or
    // we might simply be waiting for its children to complete.
    private void unshow(ProgressOperation operation) {
        OperationIdentifier operationId = operation.getOperationId();
        AssociationLabel association = operationIdToAssignedLabels.remove(operationId);
        if (association != null) {
            unusedProgressLabels.push(association.label);
        }
        unassignedProgressOperations.remove(operation);
        reportLinesNotShown();
    }

    private void reportLinesNotShown() {
        int linesNotShown = unassignedProgressOperations.size();

        String text;
        if (linesNotShown == 0) {
            text = "";
        } else if (linesNotShown == 1) {
            text = "  (1 line not showing)";
        } else {
            text = "  (" + linesNotShown + " lines not showing)";
        }

        progressArea.getCursorParkLine().setText(text);
    }

    // Any ProgressOperation in the parent chain has a message, the operation is considered renderable.
    private boolean isRenderable(ProgressOperation operation) {
        for (ProgressOperation current = operation; current != null; current = current.getParent()) {
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
        Set<OperationIdentifier> operationIdsToSkip = new HashSet<>();

        for (OutputEvent event : queue) {
            if (event instanceof ProgressStartEvent) {
                progressArea.setVisible(true);
                ProgressStartEvent startEvent = (ProgressStartEvent) event;
                boolean workAction = startEvent.getBuildOperationCategory() == BuildOperationCategory.WORK_ACTION;
                if (completeEventOperationIds.contains(startEvent.getProgressOperationId())) {
                    operationIdsToSkip.add(startEvent.getProgressOperationId());
                    // Don't attach to any labels
                    if (workAction) {
                        showWorkActions(operations.workActionUnshown(startEvent.getParentProgressOperationId()));
                    }
                } else {
                    ProgressOperation operation = operations.start(startEvent.getStatus(), startEvent.getCategory(), startEvent.getProgressOperationId(), startEvent.getParentProgressOperationId(), workAction);
                    if (!workAction) {
                        attach(operation);
                    } else if (operation.getParent() == null) {
                        attach(operation);
                    } else {
                        showWorkActions(operation.getParent());
                    }
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
