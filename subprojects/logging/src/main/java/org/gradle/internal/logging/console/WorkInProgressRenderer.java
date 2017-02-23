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

import org.gradle.internal.logging.events.BatchOutputEventListener;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WorkInProgressRenderer extends BatchOutputEventListener {
    private final OutputEventListener listener;
    private final ProgressOperations operations = new ProgressOperations();
    private final BuildProgressArea progressArea;
    private final DefaultWorkInProgressFormatter labelFormatter;

    // Track all unused labels to display future progress operation
    private final Deque<StyledLabel> unusedProgressLabels;

    // Track currently associated label with its progress operation
    private final Map<OperationIdentifier, AssociationLabel> operationIdToAssignedLabels = new HashMap<OperationIdentifier, AssociationLabel>();

    // Track any progress operation that either can't be display due to label shortage or child progress operation is already been displayed
    private final Deque<ProgressOperation> unassignedProgressOperations = new ArrayDeque<ProgressOperation>();

    // Track the parent-children relation between progress operation to avoid displaying a parent when children are been displayed
    private final Map<OperationIdentifier, Set<OperationIdentifier>> parentIdToChildrenIds = new HashMap<OperationIdentifier, Set<OperationIdentifier>>();

    public WorkInProgressRenderer(OutputEventListener listener, BuildProgressArea progressArea, DefaultWorkInProgressFormatter labelFormatter) {
        this.listener = listener;
        this.progressArea = progressArea;
        this.labelFormatter = labelFormatter;
        this.unusedProgressLabels = new ArrayDeque<StyledLabel>(progressArea.getBuildProgressLabels());
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            progressArea.setVisible(true);
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            ProgressOperation op = operations.start(startEvent.getShortDescription(), startEvent.getStatus(), startEvent.getCategory(), startEvent.getOperationId(), startEvent.getParentId());
            attach(op);
        } else if (event instanceof ProgressCompleteEvent) {
            ProgressCompleteEvent completeEvent = (ProgressCompleteEvent) event;
            detach(operations.complete(completeEvent.getOperationId()));
        } else if (event instanceof ProgressEvent) {
            ProgressEvent progressEvent = (ProgressEvent) event;
            operations.progress(progressEvent.getStatus(), progressEvent.getOperationId());
        } else if (event instanceof EndOutputEvent) {
            progressArea.setVisible(false);
        }

        listener.onOutput(event);
    }

    @Override
    public void onOutput(Iterable<OutputEvent> events) {
        super.onOutput(events);
        renderNow();
    }

    private void attach(ProgressOperation operation) {
        // Skip attach if a children is already present
        if (isChildAssociationAlreadyExists(operation.getOperationId())) {
            return;
        }

        AssociationLabel association = null;

        // Reuse parent label if possible
        if (operation.getParent() != null) {
            addDirectChildOperationId(operation.getParent().getOperationId(), operation.getOperationId());
            association = operationIdToAssignedLabels.remove(operation.getParent().getOperationId());
            if (association != null) {
                unusedProgressLabels.push(association.label);
                association = null;
            }
        }

        // No parent? Try to use a new label
        if (association == null && !unusedProgressLabels.isEmpty()) {
            association = new AssociationLabel(operation, unusedProgressLabels.pop());
        }

        if (association == null) {
            unassignedProgressOperations.addLast(operation);
        } else {
            operationIdToAssignedLabels.put(operation.getOperationId(), association);
        }
    }

    private void detach(ProgressOperation operation) {
        if (operation.getParent() != null) {
            removeDirectChildOperationId(operation.getParent().getOperationId(), operation.getOperationId());
        }

        AssociationLabel association = operationIdToAssignedLabels.remove(operation.getOperationId());
        if (association != null) {
            unusedProgressLabels.push(association.label);
            if (operation.getParent() != null) {
                attach(operation.getParent());
            } else if (!unassignedProgressOperations.isEmpty()){
                attach(unassignedProgressOperations.pop());
            }
        } else {
            unassignedProgressOperations.remove(operation);
        }
    }

    private void addDirectChildOperationId(OperationIdentifier parentId, OperationIdentifier childId) {
        Set<OperationIdentifier> children = parentIdToChildrenIds.get(parentId);
        if (children == null) {
            children = new HashSet<OperationIdentifier>();
            parentIdToChildrenIds.put(parentId, children);
        }
        children.add(childId);
    }

    private void removeDirectChildOperationId(OperationIdentifier parentId, OperationIdentifier childId) {
        Set<OperationIdentifier> children = parentIdToChildrenIds.get(parentId);
        if (children == null) {
            throw new IllegalStateException("");
        }
        children.remove(childId);
        if (children.isEmpty()) {
            parentIdToChildrenIds.remove(parentId);
        }
    }

    private boolean isChildAssociationAlreadyExists(OperationIdentifier parentId) {
        Set<OperationIdentifier> children = parentIdToChildrenIds.get(parentId);
        if (children != null && !children.isEmpty()) {
            return true;
        }
        return false;
    }

    private void renderNow() {
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
