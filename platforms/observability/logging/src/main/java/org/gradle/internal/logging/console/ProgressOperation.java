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

package org.gradle.internal.logging.console;

import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProgressOperation {

    private String status;
    private final String category;
    private final OperationIdentifier operationId;
    private final ProgressOperation parent;
    private final long startTimestamp;
    private final boolean workAction;
    private final @Nullable String owner;
    private int startedWorkActions;
    private int completedWorkActions;
    private Set<ProgressOperation> children;

    public ProgressOperation(String status, String category, OperationIdentifier operationId, ProgressOperation parent, long startTimestamp) {
        this(status, category, operationId, parent, startTimestamp, false);
    }

    ProgressOperation(String status, String category, OperationIdentifier operationId, ProgressOperation parent, long startTimestamp, boolean workAction) {
        this(status, category, operationId, parent, startTimestamp, workAction, null);
    }

    ProgressOperation(String status, String category, OperationIdentifier operationId, ProgressOperation parent, long startTimestamp, boolean workAction, @Nullable String owner) {
        this.status = status;
        this.category = category;
        this.operationId = operationId;
        this.parent = parent;
        this.startTimestamp = startTimestamp;
        this.workAction = workAction;
        this.owner = owner;
    }

    @Override
    public String toString() {
        return String.format("id=%s, category=%s, status=%s", operationId, category, status);
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        if (GUtil.isTrue(status)) {
            return status;
        }
        return null;
    }

    public String getCategory() {
        return category;
    }

    public OperationIdentifier getOperationId() {
        return operationId;
    }

    /**
     * The owner to show with the message, such as ":lib". Null when no owner is shown.
     */
    @Nullable
    public String getOwner() {
        return owner;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public ProgressOperation getParent() {
        return parent;
    }

    public boolean addChild(ProgressOperation operation) {
        if (children == null) {
            // In start order, so lines are given to work actions in that order
            children = new LinkedHashSet<ProgressOperation>();
        }
        if (operation.isWorkAction()) {
            workActionStarted();
        }
        return children.add(operation);
    }

    public boolean removeChild(ProgressOperation operation) {
        if (children == null) {
            throw new IllegalStateException(String.format("Cannot remove child operation [%s] from operation with no children [%s]", operation, this));
        }
        return children.remove(operation);
    }

    boolean isWorkAction() {
        return workAction;
    }

    void workActionStarted() {
        startedWorkActions++;
    }

    void workActionCompleted() {
        completedWorkActions++;
    }

    int getCompletedWorkActions() {
        return completedWorkActions;
    }

    /**
     * Whether the work actions of this operation are shown, which is from its second work action on.
     * A line for a single work action is of little use next to the line of this operation.
     */
    boolean showsWorkActions() {
        return startedWorkActions > 1;
    }

    /**
     * Whether this is the single work action of its parent.
     * Such a work action is hidden: its line and its message are not shown.
     */
    boolean isHiddenWorkAction() {
        return workAction && parent != null && !parent.showsWorkActions();
    }

    /**
     * The operation whose count of completed work actions is shown on the line of this operation, if any.
     * This is this operation, or else its hidden work action, at any depth, as a hidden work action has no line.
     */
    @Nullable
    ProgressOperation getWorkActionCountOwner() {
        ProgressOperation owner = this;
        while (!owner.showsWorkActions()) {
            List<ProgressOperation> workActions = owner.getWorkActions();
            if (workActions.isEmpty()) {
                return null;
            }
            owner = workActions.get(0);
        }
        return owner;
    }

    /**
     * The running work actions of this operation, in the order they started.
     */
    List<ProgressOperation> getWorkActions() {
        List<ProgressOperation> workActions = new ArrayList<ProgressOperation>();
        if (children != null) {
            for (ProgressOperation child : children) {
                if (child.isWorkAction()) {
                    workActions.add(child);
                }
            }
        }
        return workActions;
    }

    /**
     * Whether this operation has children other than work actions.
     * The children of a hidden work action are counted as children of this operation, at any depth.
     */
    boolean hasChildrenOtherThanWorkActions() {
        if (children != null) {
            for (ProgressOperation child : children) {
                if (!child.isWorkAction() || (child.isHiddenWorkAction() && child.hasChildrenOtherThanWorkActions())) {
                    return true;
                }
            }
        }
        return false;
    }
}
