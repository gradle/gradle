/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;

@UsedByScanPlugin
public class TestStartEvent {
    private final long startTime;
    private final Object parentId;

    @UsedByScanPlugin("test-distribution")
    public TestStartEvent(long startTime) {
        this(startTime, null);
    }

    @UsedByScanPlugin("test-distribution")
    public TestStartEvent(long startTime, Object parentId) {
        this.startTime = startTime;
        this.parentId = parentId;
    }

    public long getStartTime() {
        return startTime;
    }

    @Nullable
    public Object getParentId() {
        return parentId;
    }

    /**
     * Creates a copy of this event with a new parent id.
     */
    public TestStartEvent withParentId(Object parentId) {
        return new TestStartEvent(startTime, parentId);
    }
}
