/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.internal.service.ServiceLookupException;

@NonNullApi
public interface NodeExecutionContext {
    /**
     * Locates the given execution service.
     */
    <T> T getService(Class<T> type) throws ServiceLookupException;

    /**
     * Whether this context is coming from an execution graph.
     * <p>
     * For ad-hoc execution of nodes we use the global context and not the context that would be created as
     * part of executing the execution graph. This is happening when a node value is requested
     * without scheduling and executing the node first.
     */
    default boolean isPartOfExecutionGraph() {
        return true;
    }
}
