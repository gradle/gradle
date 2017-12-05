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

package org.gradle.caching.internal.tasks.origin;

import org.gradle.internal.id.UniqueId;

public class TaskOutputOriginMetadata {

    private final UniqueId buildInvocationId;

    private final long originalExecutionTime;

    public TaskOutputOriginMetadata(UniqueId buildInvocationId, long originalExecutionTime) {
        this.buildInvocationId = buildInvocationId;
        this.originalExecutionTime = originalExecutionTime;
    }

    public UniqueId getBuildInvocationId() {
        return buildInvocationId;
    }

    public long getOriginalExecutionTime() {
        return originalExecutionTime;
    }
}
