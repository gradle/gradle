/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.id.UniqueId;

/**
 * Old location of {@link OriginMetadata}.
 *
 * This type is used for Kotlin DSL caching.
 * Let's keep it for now until the usage in the Kotlin DSL has been removed.
 */
@Deprecated
public class OriginTaskExecutionMetadata extends OriginMetadata {
    public OriginTaskExecutionMetadata(UniqueId buildInvocationId, long executionTime) {
        super(buildInvocationId, executionTime);
    }
}
