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

package org.gradle.internal.progress;

import javax.annotation.Nullable;

/**
 * Meta-data about a build operation.
 */
public final class BuildOperationDescriptor {
    private final Object id;
    private final Object parentId;
    private final String displayName;
    private final String name;
    private final String progressDisplayName;
    private final Object details;
    private final BuildOperationCategory operationType;

    private BuildOperationDescriptor(Object id, Object parentId, String name, String displayName, String progressDisplayName, Object details, BuildOperationCategory operationType) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.displayName = displayName;
        this.progressDisplayName = progressDisplayName;
        this.details = details;
        this.operationType = operationType;
    }

    public Object getId() {
        return id;
    }

    /**
     * Returns a short name for the operation. This is a short human consumable description of the operation that makes sense in the context of the parent operation.
     * See TAPI {@code OperationDescriptor}.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the display name for the operation. This should be a standalone human consumable description of the
     * operation, and should describe the operation whether currently running or not, eg "Run test A" rather than
     * "Running test A".
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the display name to use for progress logging for the operation. Should be short and describe the operation
     * as it is running, eg "Running test A" rather than "Run test A".
     *
     * <p>When null, no progress logging is generated for the operation. Defaults to null.
     */
    @Nullable
    public String getProgressDisplayName() {
        return progressDisplayName;
    }

    /**
     * Arbitrary metadata for the operation.
     */
    @Nullable
    public Object getDetails() {
        return details;
    }

    /**
     * The parent for the operation, if any. When null, the operation of the current thread is used.
     */
    @Nullable
    public Object getParentId() {
        return parentId;
    }

    public BuildOperationCategory getOperationType() {
        return operationType;
    }

    public static Builder displayName(String displayName) {
        return new Builder(displayName);
    }

    public static final class Builder {
        private final String displayName;
        private String name;
        private String progressDisplayName;
        private Object details;
        private BuildOperationState parent;
        private BuildOperationCategory operationType = BuildOperationCategory.UNCATEGORIZED;

        private Builder(String displayName) {
            this.displayName = displayName;
            this.name = displayName;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder progressDisplayName(String progressDisplayName) {
            this.progressDisplayName = progressDisplayName;
            return this;
        }

        public Builder details(Object details) {
            this.details = details;
            return this;
        }

        public Builder operationType(BuildOperationCategory operationType) {
            this.operationType = operationType;
            return this;
        }

        /**
         * Define the parent of the operation. Needs to be the state of an operations that is running at the same time
         * the described operation will run (see: {@link org.gradle.internal.operations.BuildOperationExecutor#getCurrentOperation()}).
         * If parent ID is not set, The last started operation of the executing thread will be used as parent.
         */
        public Builder parent(BuildOperationState parent) {
            this.parent = parent;
            return this;
        }

        public BuildOperationDescriptor build() {
            return build(null, null);
        }

        BuildOperationState getParentState() {
            return parent;
        }

        public BuildOperationDescriptor build(@Nullable Object id, @Nullable Object defaultParentId) {
            return new BuildOperationDescriptor(id, parent == null ? defaultParentId : parent.getId(), name, displayName, progressDisplayName, details, operationType);
        }
    }
}
