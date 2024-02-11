/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.properties;

/**
 * Describes the behavior of an input property.
 */
public enum InputBehavior {
    /**
     * Non-incremental inputs.
     *
     * <ul>
     *     <li>Any change to the property value always triggers a full rebuild of the work</li>
     *     <li>Changes for the property cannot be queried via {@link org.gradle.work.InputChanges}</li>
     * </ul>
     */
    NON_INCREMENTAL(false, false),

    /**
     * Incremental inputs.
     *
     * <ul>
     *     <li>Changes to the property value can cause an incremental execution of the work</li>
     *     <li>Changes for the property can be queried via {@link org.gradle.work.InputChanges}</li>
     * </ul>
     */
    INCREMENTAL(true, false),

    /**
     * Primary (incremental) inputs.
     *
     * <ul>
     *     <li>Changes to the property value can cause an incremental execution</li>
     *     <li>Changes for the property can be queried via {@link org.gradle.work.InputChanges}</li>
     *     <li>When the property is empty, the work is skipped with any previous outputs removed</li>
     * </ul>
     */
    PRIMARY(true, true);

    private final boolean trackChanges;
    private final boolean skipWhenEmpty;

    InputBehavior(boolean trackChanges, boolean skipWhenEmpty) {
        this.trackChanges = trackChanges;
        this.skipWhenEmpty = skipWhenEmpty;
    }

    /**
     * Whether incremental changes should be tracked via {@link org.gradle.work.InputChanges}.
     */
    public boolean shouldTrackChanges() {
        return trackChanges;
    }

    /**
     * Whether the work should be skipped and outputs be removed if the property is empty.
     */
    public boolean shouldSkipWhenEmpty() {
        return skipWhenEmpty;
    }
}
