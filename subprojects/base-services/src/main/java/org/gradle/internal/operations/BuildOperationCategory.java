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
package org.gradle.internal.operations;

/**
 * Classifies a build operation such that executors and event listeners can
 * react differently depending on this type.
 *
 * @since 4.0
 */
public enum BuildOperationCategory implements BuildOperationMetadata {
    /**
     * Configure the root build. May also include nested {@link #CONFIGURE_BUILD} and {@link #RUN_WORK} operations.
     */
    CONFIGURE_ROOT_BUILD(false, false, false),

    /**
     * Configure a nested build or a buildSrc build.
     */
    CONFIGURE_BUILD(false, false, false),

    /**
     * Configure a single project in any build.
     */
    CONFIGURE_PROJECT(true, false, false),

    /**
     * Execute all work in the root build. Might include work from nested builds.
     */
    RUN_WORK_ROOT_BUILD(false, false, false),

    /**
     * Execute all work in a nested build or a buildSrc build. Includes {@link #TASK} and Includes {@link #TRANSFORM} operations.
     */
    RUN_WORK(false, false, false),

    /**
     * Execute an individual task.
     */
    TASK(true, true, true),

    /**
     * Execute an individual transform.
     */
    TRANSFORM(true, true, false),

    /**
     * Operation doesn't belong to any category.
     */
    UNCATEGORIZED(false, false, false);

    private final boolean grouped;
    private final boolean topLevelWorkItem;
    private final boolean showHeader;

    BuildOperationCategory(boolean grouped, boolean topLevelWorkItem, boolean showHeader) {
        this.grouped = grouped;
        this.topLevelWorkItem = topLevelWorkItem;
        this.showHeader = showHeader;
    }

    public boolean isGrouped() {
        return grouped;
    }

    public boolean isTopLevelWorkItem() {
        return topLevelWorkItem;
    }

    public boolean isShowHeader() {
        return showHeader;
    }
}
